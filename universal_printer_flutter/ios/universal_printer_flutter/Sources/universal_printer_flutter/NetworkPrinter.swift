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
}
