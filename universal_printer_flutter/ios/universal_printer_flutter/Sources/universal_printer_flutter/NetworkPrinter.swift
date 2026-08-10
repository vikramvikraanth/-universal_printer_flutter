import Foundation
import Network

// TCP-9100 ESC/POS transport via Network.framework. Connect → write → drain (proportional wait so the
// buffer flushes before close, mirroring Android D32) → close. Returns a PrintErrorReason string or nil.
final class NetworkPrinter {
    let host: String
    let port: Int
    let brand: String?         // discovered brand, e.g. "EPSON"
    let paperWidthMm: Int?     // physical paper width (mm)

    init(host: String, port: Int, brand: String? = nil, paperWidthMm: Int? = nil) {
        self.host = host
        self.port = port
        self.brand = brand
        self.paperWidthMm = paperWidthMm
    }

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
    func queryStatus(timeout: TimeInterval = 2.0, completion: @escaping ([String: Any]) -> Void) {
        guard let nwPort = NWEndpoint.Port(rawValue: UInt16(port)) else {
            completion(["supported": true, "answered": false]); return
        }
        let conn = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: .tcp)
        let queue = DispatchQueue(label: "upf.status.\(host)")
        var done = false
        func finish(_ map: [String: Any]) { if done { return }; done = true; conn.cancel(); completion(map) }
        // DLE EOT n: 1=printer, 2=offline cause, 4=paper. (n=3 cutter omitted; not all printers answer.)
        let query = Data([0x10, 0x04, 1, 0x10, 0x04, 2, 0x10, 0x04, 4])
        queue.asyncAfter(deadline: .now() + timeout) { finish(["supported": true, "answered": false]) }
        conn.stateUpdateHandler = { st in
            if case .ready = st {
                conn.send(content: query, completion: .contentProcessed { _ in
                    conn.receive(minimumIncompleteLength: 1, maximumLength: 8) { data, _, _, _ in
                        guard let d = data, d.count >= 1 else {
                            finish(["supported": true, "answered": false]); return
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
                finish(["supported": true, "answered": false])
            }
        }
        conn.start(queue: queue)
    }
}
