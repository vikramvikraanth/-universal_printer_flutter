import Flutter
import UIKit

/// iOS implementation of the `universal_printer_flutter` MethodChannel. Mirrors the Android
/// `UniversalPrinterFlutterPlugin`: routes every method, does blocking work off the main thread, and
/// delivers channel replies on the main thread. v1 supports **network** discovery + printing; USB and
/// Sunmi/iMin built-in are Android-only and return a catchable error / empty list. (Star lands next.)
public class UniversalPrinterFlutterPlugin: NSObject, FlutterPlugin {

    private var printers: [String: NetworkPrinter] = [:]
    private var seq: Int = 0
    private let lock = NSLock()

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "universal_printer_flutter", binaryMessenger: registrar.messenger())
        registrar.addMethodCallDelegate(UniversalPrinterFlutterPlugin(), channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        let args = call.arguments as? [String: Any] ?? [:]
        switch call.method {
        case "getPlatformVersion":
            reply(result, "iOS " + UIDevice.current.systemVersion)

        // ---- Discovery ----
        case "discoverNetwork", "discoverEpson":
            Discovery.scanSubnet { found in self.reply(result, found.map { $0.toMap() }) }
        case "discoverSunmi":
            Discovery.bonjour { found in self.reply(result, found.map { $0.toMap() }) }
        case "discoverAll":
            discoverAll { found in self.reply(result, found.map { $0.toMap() }) }
        case "discoverZebra", "discoverSnmp", "discoverSeiko", "discoverStar", "discoverUsb", "discoverBuiltIn":
            reply(result, [Any]())   // not supported / deferred on iOS (built-in = Android-only hardware)
        case "ping":
            Discovery.ping((args["ip"] as? String) ?? "") { ok in self.reply(result, ok) }

        // ---- Printing ----
        case "createPrinter":
            createPrinter(args, result)
        case "printDocument":
            printDocument(args, result)
        case "getStatus":
            guard let handle = args["handle"] as? String else {
                reply(result, ["supported": false]); return
            }
            lock.lock(); let printer = printers[handle]; lock.unlock()
            guard let printer = printer else {
                reply(result, FlutterError(code: "NO_PRINTER", message: "no printer for handle \(handle)", details: nil)); return
            }
            printer.queryStatus { map in self.reply(result, map) }
        case "closePrinter":
            if let h = args["handle"] as? String { lock.lock(); printers[h] = nil; lock.unlock() }
            reply(result, nil)

        default:
            result(FlutterMethodNotImplemented)
        }
    }

    // MARK: printing

    private func createPrinter(_ args: [String: Any], _ result: @escaping FlutterResult) {
        let kind = (args["kind"] as? String) ?? ""
        switch kind {
        case "network", "sunmiCloud":
            let host = (args["host"] as? String) ?? ""
            let port = intArg(args["port"], 9100)
            let brand = args["brand"] as? String
            let paperWidthMm = args["paperWidthMm"] as? Int
            lock.lock()
            seq += 1
            let handle = "p\(seq)"
            printers[handle] = NetworkPrinter(host: host, port: port, brand: brand, paperWidthMm: paperWidthMm)
            lock.unlock()
            reply(result, handle)
        default:
            reply(result, FlutterError(code: "UNSUPPORTED_PLATFORM",
                                       message: "'\(kind)' printers are not supported on iOS yet", details: nil))
        }
    }

    private func printDocument(_ args: [String: Any], _ result: @escaping FlutterResult) {
        guard let handle = args["handle"] as? String else {
            reply(result, Bridge.errorResult("UNKNOWN", "missing handle")); return
        }
        lock.lock(); let printer = printers[handle]; lock.unlock()
        guard let printer = printer else {
            reply(result, Bridge.errorResult("NOT_CONNECTED", "no printer for handle \(handle)")); return
        }
        guard let docMap = args["document"] as? [String: Any] else {
            reply(result, Bridge.errorResult("CONTENT_INVALID", "missing document")); return
        }
        // Encode off the main thread (buildDocument may synchronously fetch URL images).
        DispatchQueue.global().async {
            var doc = Bridge.buildDocument(docMap)
            // If the printer knows its paper width, re-paginate to it (you can't print wider than the paper).
            if let mm = printer.paperWidthMm {
                doc = ReceiptDocument(paper: PaperWidth.ofMillimeters(mm), cut: doc.cut, elements: doc.elements)
            }
            let data = EscPos.encode(doc)
            printer.send(data) { reason in
                if let reason = reason {
                    self.reply(result, Bridge.errorResult(reason, "print failed"))
                } else {
                    self.reply(result, Bridge.successResult())
                }
            }
        }
    }

    // MARK: discovery aggregation

    private func discoverAll(_ completion: @escaping ([DiscoveredPrinter]) -> Void) {
        let group = DispatchGroup()
        let lock2 = NSLock()
        var all: [DiscoveredPrinter] = []
        group.enter(); Discovery.scanSubnet { r in lock2.lock(); all += r; lock2.unlock(); group.leave() }
        group.enter(); Discovery.bonjour { r in lock2.lock(); all += r; lock2.unlock(); group.leave() }
        group.notify(queue: .global()) {
            var seen = Set<String>()
            completion(all.filter { seen.insert($0.ipAddress ?? $0.name).inserted })
        }
    }

    // MARK: helpers

    private func intArg(_ v: Any?, _ d: Int) -> Int {
        if let i = v as? Int { return i }
        if let n = v as? NSNumber { return n.intValue }
        return d
    }

    /// MethodChannel replies must be delivered on the main thread.
    private func reply(_ result: @escaping FlutterResult, _ value: Any?) {
        if Thread.isMainThread { result(value) } else { DispatchQueue.main.async { result(value) } }
    }
}
