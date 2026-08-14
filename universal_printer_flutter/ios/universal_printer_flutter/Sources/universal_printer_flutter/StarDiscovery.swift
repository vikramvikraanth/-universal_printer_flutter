import Foundation
import StarIO10

// Star LAN/BT discovery via StarDeviceDiscoveryManager (the iOS counterpart to Android's Star discovery).
// Imports StarIO10 → compiles in a real pod build, not a bare type-check. API taken verbatim from the
// official example (star-micronics/StarXpand-SDK-iOS: DiscoveryView.swift).
enum StarDiscovery {

    // In-flight sessions are retained here because the manager holds its delegate weakly; released on finish.
    private static var active: [StarDiscoverySession] = []
    private static let lock = NSLock()

    static func discover(timeoutMs: Int = 5000, completion: @escaping ([DiscoveredPrinter]) -> Void) {
        let session = StarDiscoverySession { printers, finished in
            lock.lock(); active.removeAll { $0 === finished }; lock.unlock()
            completion(printers)
        }
        lock.lock(); active.append(session); lock.unlock()
        session.start(timeoutMs: timeoutMs)
    }
}

private final class StarDiscoverySession: NSObject, StarDeviceDiscoveryManagerDelegate {
    private let onFinish: (_ printers: [DiscoveredPrinter], _ session: StarDiscoverySession) -> Void
    private var manager: (any StarDeviceDiscoveryManager)?
    private var found: [DiscoveredPrinter] = []
    private var finished = false

    init(onFinish: @escaping (_ printers: [DiscoveredPrinter], _ session: StarDiscoverySession) -> Void) {
        self.onFinish = onFinish
    }

    func start(timeoutMs: Int) {
        do {
            let m = try StarDeviceDiscoveryManagerFactory.create(interfaceTypes: [.lan, .bluetooth, .bluetoothLE, .usb])
            m.discoveryTime = timeoutMs
            m.delegate = self
            try m.startDiscovery()
            manager = m
        } catch {
            finish()
        }
    }

    func manager(_ manager: any StarDeviceDiscoveryManager, didFind printer: StarPrinter) {
        let identifier = printer.connectionSettings.identifier
        let iface = printer.connectionSettings.interfaceType
        let model = printer.information?.model.map { String(describing: $0) }
        var dp = DiscoveredPrinter(name: model ?? "Star Printer",
                                   connectionType: iface == .lan ? "NETWORK" : "USB")
        // For LAN the identifier is the connect string the app passes back to `starPrinter(identifier)`.
        if iface == .lan {
            dp.ipAddress = identifier
            dp.macAddress = printer.information?.detail.lan.uniqueId
        }
        dp.brand = "STAR"
        dp.model = model
        dp.emulation = "STAR"
        found.append(dp)
    }

    func managerDidFinishDiscovery(_ manager: any StarDeviceDiscoveryManager) { finish() }

    private func finish() {
        if finished { return }
        finished = true
        manager?.stopDiscovery()
        onFinish(found, self)
    }
}
