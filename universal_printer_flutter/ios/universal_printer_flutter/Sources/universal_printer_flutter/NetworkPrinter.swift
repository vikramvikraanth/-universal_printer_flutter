import Foundation
import Network

// TCP-9100 ESC/POS transport via Network.framework. Connect → write → drain (proportional wait so the
// buffer flushes before close, mirroring Android D32) → close. Returns a PrintErrorReason string or nil.
final class NetworkPrinter: PrinterBackend {
    let host: String
    let port: Int
    let brand: String?         // discovered brand, e.g. "EPSON"
    let paperWidthMm: Int?     // physical paper width (mm)
    let isImpact: Bool         // 9-pin dot-matrix (e.g. TM-U*) → text-only, no raster

    // Adaptive preflight state — mirrors Kotlin PreflightGate. A printer reachable-but-silent to DLE EOT
    // doesn't implement real-time status, so we stop probing it (keeps printing smooth). silentThreshold>1
    // so one slow/busy first probe can't permanently downgrade a capable printer; reprobeInterval re-probes
    // periodically to recover. Jobs are serialized per handle, so plain vars need no extra synchronization.
    private var consecutiveSilent = 0
    private var statusUnsupported = false
    private var jobsSinceProbe = 0
    private let silentThreshold = 2
    private let reprobeInterval = 20

    init(host: String, port: Int, brand: String? = nil, paperWidthMm: Int? = nil, isImpact: Bool = false) {
        self.host = host
        self.port = port
        self.brand = brand
        self.paperWidthMm = paperWidthMm
        self.isImpact = isImpact
    }

    // MARK: PrinterBackend

    func printDocument(_ doc: ReceiptDocument, completion: @escaping (_ reason: String?) -> Void) {
        // Known non-status printer → skip the probe and print straight through (the reference package
        // never preflights), but re-probe periodically so a recovered printer regains fault detection.
        if statusUnsupported {
            jobsSinceProbe += 1
            if jobsSinceProbe < reprobeInterval { send(EscPos.encode(doc), completion: completion); return }
            jobsSinceProbe = 0
        }
        // Otherwise preflight via DLE EOT (mirrors Android Preflight.escPos).
        readStatus { status in
            let answered = (status["answered"] as? Bool) == true
            let reachable = (status["reachable"] as? Bool) == true
            if answered {
                self.consecutiveSilent = 0
                self.statusUnsupported = false // it answered → supports status; resume probing every job
                if let reason = NetworkPrinter.preflightReason(status) { completion(reason); return }
            } else if reachable {
                // Reachable but silent → doesn't implement real-time status; give up only after a streak.
                self.consecutiveSilent += 1
                if self.consecutiveSilent >= self.silentThreshold { self.statusUnsupported = true }
            }
            // Unreachable → transient; don't count it against the printer.
            self.send(EscPos.encode(doc), completion: completion)
        }
    }

    /// Maps a `readStatus` result to a blocking PrintErrorReason wire string, or nil to proceed.
    /// Mirrors Kotlin `Preflight.escPos`: unanswered → proceed; then online/cover/cutter/paper.
    static func preflightReason(_ s: [String: Any]) -> String? {
        guard (s["answered"] as? Bool) == true else { return nil } // best-effort: no answer → proceed
        if (s["online"] as? Bool) == false { return "NOT_CONNECTED" }
        if (s["coverOpen"] as? Bool) == true { return "COVER_OPEN" }
        if (s["autoCutterError"] as? Bool) == true { return "CUTTER_ERROR" }
        if (s["paper"] as? String) == "NOT_PRESENT" { return "PAPER_OUT" }
        return nil // includes NEAR_END → print continues
    }

    func queryStatus(completion: @escaping (_ status: [String: Any]) -> Void) {
        readStatus(completion: completion)
    }

    /// Connection-per-job transport — nothing persistent to release.
    func close() {}

    func send(_ data: Data, connectTimeout: TimeInterval = 5.0, completion: @escaping (_ reason: String?) -> Void) {
        guard let nwPort = NWEndpoint.Port(rawValue: UInt16(port)) else { completion("CONTENT_INVALID"); return }
        let conn = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: .tcp)
        let queue = DispatchQueue(label: "upf.print.\(host)")
        var finished = false
        func finish(_ reason: String?) {
            if finished { return }
            finished = true
            conn.cancel()
            completion(reason)
        }
        queue.asyncAfter(deadline: .now() + connectTimeout) {
            if conn.state != .ready { finish("TIMEOUT") }
        }
        conn.stateUpdateHandler = { state in
            switch state {
            case .ready:
                conn.send(content: data, completion: .contentProcessed { error in
                    if error != nil { finish("IO"); return }
                    let drainMs = min(2500, max(60, data.count / 64))
                    queue.asyncAfter(deadline: .now() + .milliseconds(drainMs)) { finish(nil) }
                })
            case .failed:
                finish("NOT_CONNECTED")
            default:
                break
            }
        }
        conn.start(queue: queue)
    }

    /// Live status via ESC/POS `DLE EOT` on a short-lived connection. Returns a status dict, or
    /// `{supported:true, answered:false}` if the printer doesn't respond. Needs an idle printer.
    // 0.5s: the pre-print preflight runs on every job, so keep it short — real-time status replies are
    // immediate; a printer that doesn't answer must not stall printing (matches Android statusReadTimeoutMs).
    private func readStatus(timeout: TimeInterval = 0.5, completion: @escaping ([String: Any]) -> Void) {
        guard let nwPort = NWEndpoint.Port(rawValue: UInt16(port)) else {
            completion(["supported": true, "answered": false]); return
        }
        let conn = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: .tcp)
        let queue = DispatchQueue(label: "upf.status.\(host)")
        var done = false
        // `reachable` = did the socket connect before answering. Lets the caller tell a reachable-but-silent
        // printer (doesn't implement status) from an unreachable one, so only the former is remembered.
        var reachable = false
        func finish(_ map: [String: Any]) { if done { return }; done = true; conn.cancel(); completion(map) }
        // DLE EOT n: 1=printer, 2=offline cause, 4=paper. (n=3 cutter omitted; not all printers answer.)
        let query = Data([0x10, 0x04, 1, 0x10, 0x04, 2, 0x10, 0x04, 4])
        queue.asyncAfter(deadline: .now() + timeout) { finish(["supported": true, "answered": false, "reachable": reachable]) }
        conn.stateUpdateHandler = { st in
            if case .ready = st {
                reachable = true
                conn.send(content: query, completion: .contentProcessed { _ in
                    conn.receive(minimumIncompleteLength: 1, maximumLength: 8) { data, _, _, _ in
                        guard let d = data, d.count >= 1 else {
                            finish(["supported": true, "answered": false, "reachable": true]); return
                        }
                        let b = [UInt8](d)
                        let printer = Int(b[0])
                        let offline = b.count > 1 ? Int(b[1]) : 0
                        let paperB = b.count > 2 ? Int(b[2]) : 0
                        let online = (printer & 0x08) == 0
                        let cover = (offline & 0x04) != 0
                        let err = (offline & 0x40) != 0
                        let paper: String = (paperB & 0x72) == 0x72 ? "NOT_PRESENT"
                            : (paperB & 0x1E) == 0x1E ? "NEAR_END" : "OK"
                        let ready = online && !cover && !err && paper != "NOT_PRESENT"
                        finish([
                            "supported": true, "answered": true, "online": online, "coverOpen": cover,
                            "error": err, "autoCutterError": false, "paper": paper, "ready": ready,
                        ])
                    }
                })
            } else if case .failed = st {
                finish(["supported": true, "answered": false, "reachable": false])
            }
        }
        conn.start(queue: queue)
    }
}
