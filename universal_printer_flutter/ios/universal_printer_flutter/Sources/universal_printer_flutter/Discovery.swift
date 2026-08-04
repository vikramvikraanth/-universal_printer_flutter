import Foundation
import Network
import Darwin

// Network discovery — TCP-9100 reachability + subnet sweep, and Bonjour/mDNS for Sunmi Cloud.
// (Epson ENPC UDP arrives with the Star increment.)
enum Discovery {

    // MARK: reachability

    static func ping(_ ip: String, port: Int = 9100, timeout: TimeInterval = 1.0,
                     completion: @escaping (Bool) -> Void) {
        guard let nwPort = NWEndpoint.Port(rawValue: UInt16(port)) else { completion(false); return }
        let conn = NWConnection(host: NWEndpoint.Host(ip), port: nwPort, using: .tcp)
        let q = DispatchQueue(label: "upf.ping")
        var done = false
        func finish(_ ok: Bool) { if done { return }; done = true; conn.cancel(); completion(ok) }
        q.asyncAfter(deadline: .now() + timeout) { finish(false) }
        conn.stateUpdateHandler = { st in
            switch st {
            case .ready: finish(true)
            case .failed, .cancelled: finish(false)
            default: break
            }
        }
        conn.start(queue: q)
    }

    // MARK: subnet sweep

    static func scanSubnet(port: Int = 9100, completion: @escaping ([DiscoveredPrinter]) -> Void) {
        guard let prefix = subnetPrefix() else { completion([]); return }
        let group = DispatchGroup()
        let lock = NSLock()
        var found: [DiscoveredPrinter] = []
        let sema = DispatchSemaphore(value: 24) // cap concurrent connects
        let q = DispatchQueue(label: "upf.scan", attributes: .concurrent)
        for h in 2...254 {
            group.enter()
            q.async {
                sema.wait()
                ping("\(prefix)\(h)", port: port, timeout: 0.6) { ok in
                    if ok {
                        lock.lock()
                        found.append(DiscoveredPrinter(name: "Network Printer", connectionType: "NETWORK",
                                                       ipAddress: "\(prefix)\(h)", port: port,
                                                       brand: "GENERIC", emulation: "ESC/POS"))
                        lock.unlock()
                    }
                    sema.signal(); group.leave()
                }
            }
        }
        group.notify(queue: q) { completion(found) }
    }

    // MARK: Bonjour (Sunmi Cloud)

    static func bonjour(timeout: TimeInterval = 4.0, completion: @escaping ([DiscoveredPrinter]) -> Void) {
        let browser = NWBrowser(for: .bonjour(type: "_afpovertcp._tcp.", domain: nil), using: NWParameters())
        let lock = NSLock()
        var found: [DiscoveredPrinter] = []
        browser.browseResultsChangedHandler = { results, _ in
            for r in results {
                if case let .service(name, _, _, _) = r.endpoint, name.hasPrefix("CloudPrint_") {
                    lock.lock()
                    found.append(DiscoveredPrinter(name: name, connectionType: "NETWORK", brand: "SUNMI"))
                    lock.unlock()
                }
            }
        }
        browser.start(queue: .global())
        DispatchQueue.global().asyncAfter(deadline: .now() + timeout) {
            browser.cancel()
            lock.lock()
            var seen = Set<String>()
            let unique = found.filter { seen.insert($0.name).inserted }
            lock.unlock()
            completion(unique)
        }
    }

    // MARK: local address helpers

    static func subnetPrefix() -> String? {
        guard let ip = localIPv4() else { return nil }
        var parts = ip.split(separator: ".")
        guard parts.count == 4 else { return nil }
        parts.removeLast()
        return parts.joined(separator: ".") + "."
    }

    static func localIPv4() -> String? {
        var address: String?
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return nil }
        var ptr: UnsafeMutablePointer<ifaddrs>? = first
        while let cur = ptr {
            let iface = cur.pointee
            if let sa = iface.ifa_addr, sa.pointee.sa_family == UInt8(AF_INET) {
                let name = String(cString: iface.ifa_name)
                if name == "en0" || name == "en1" {
                    var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                    getnameinfo(sa, socklen_t(sa.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST)
                    address = String(cString: host)
                }
            }
            ptr = iface.ifa_next
        }
        freeifaddrs(ifaddr)
        return address
    }
}
