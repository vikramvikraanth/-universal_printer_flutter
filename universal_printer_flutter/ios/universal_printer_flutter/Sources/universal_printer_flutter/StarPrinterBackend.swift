import Foundation
import UIKit
import StarIO10

// Star printer backend via the StarXpand SDK (StarIO10) — the iOS counterpart to Android's
// StarPrinterBackend. Renders the device-agnostic ReceiptDocument into StarXpand commands and prints
// over a per-job connection (open → print → close), mirroring Android's queue-serialized behaviour.
//
// NOTE: this file imports StarIO10, which is only present after `pod install` (the SDK is a hard
// CocoaPods dependency, matching Android). It therefore compiles in a real pod build, not in a bare
// swiftc type-check. The StarXpand API used here is taken verbatim from the official iOS example
// (star-micronics/StarXpand-SDK-iOS: PrintingView / StatusView / DiscoveryView).
final class StarPrinterBackend: PrinterBackend {

    private let identifier: String
    private let interfaceType: InterfaceType
    let paperWidthMm: Int?
    let isImpact: Bool

    /// [interface] is a wire string ("LAN" | "BLUETOOTH" | "BLUETOOTHLE" | "USB") so the plugin stays
    /// StarIO10-free; it's mapped to `InterfaceType` here.
    init(identifier: String, interface: String = "LAN", isImpact: Bool = false, paperWidthMm: Int? = nil) {
        self.identifier = identifier
        self.interfaceType = StarPrinterBackend.interfaceType(interface)
        self.isImpact = isImpact
        self.paperWidthMm = paperWidthMm
    }

    // MARK: PrinterBackend

    func printDocument(_ doc: ReceiptDocument, completion: @escaping (_ reason: String?) -> Void) {
        let commands = buildCommands(doc)
        Task {
            let printer = StarPrinter(StarConnectionSettings(interfaceType: interfaceType, identifier: identifier))
            do {
                try await printer.open()
                defer { Task { await printer.close() } }
                // Preflight on the same connection (mirrors Android Preflight.star): block on actionable
                // faults with a specific reason before spending a print. Detail fault flags (Bool?) are
                // from StarPrinterStatusDetail (verified against the StarXpand API reference).
                let s = try await printer.getStatus()
                let d = s.detail
                if s.coverOpen { completion("COVER_OPEN"); return }
                if s.paperEmpty { completion("PAPER_OUT"); return }
                if d.paperPresent == true { completion("HOLDING_PAPER"); return }
                if d.cutterError == true { completion("CUTTER_ERROR"); return }
                if d.paperJamError == true { completion("PAPER_JAM"); return }
                if d.printHeadOverTemperature == true || d.printHeadThermistorError == true { completion("OVERHEATED"); return }
                // Any remaining fault (printUnitOpen/voltage/roll/separator/unrecoverable/…) → generic.
                if s.hasError { completion("UNKNOWN"); return }
                try await printer.print(command: commands)
                completion(nil)
            } catch {
                completion(StarPrinterBackend.reason(for: error))
            }
        }
    }

    func queryStatus(completion: @escaping (_ status: [String: Any]) -> Void) {
        Task {
            let printer = StarPrinter(StarConnectionSettings(interfaceType: interfaceType, identifier: identifier))
            do {
                try await printer.open()
                defer { Task { await printer.close() } }
                let s = try await printer.getStatus()
                let d = s.detail
                let paper = s.paperEmpty ? "NOT_PRESENT" : (s.paperNearEmpty ? "NEAR_END" : "OK")
                let cutter = d.cutterError == true
                // Fold the technical detail faults into `error` (mirrors Android queryStatus).
                let error = s.hasError || d.printUnitOpen == true || d.voltageError == true
                    || d.printHeadOverTemperature == true || d.printHeadThermistorError == true
                    || d.rollPositionError == true || d.paperSeparatorError == true || d.unrecoverableError == true
                let online = !(d.unrecoverableError == true)
                let ready = online && !s.coverOpen && !s.paperEmpty && !error && !cutter
                completion([
                    "supported": true, "answered": true, "online": online,
                    "coverOpen": s.coverOpen, "error": error, "autoCutterError": cutter,
                    "paper": paper, "ready": ready,
                ])
            } catch {
                completion(["supported": true, "answered": false])
            }
        }
    }

    func close() {} // per-job open/close — nothing persistent to release.

    // MARK: command building (mirror of Android StarPrinterBackend.buildCommands)

    private func buildCommands(_ doc: ReceiptDocument) -> String {
        let pb = StarXpandCommand.PrinterBuilder()
        for el in doc.elements {
            switch el {
            case let .text(s, align, bold, underline, invert, size):
                // Scope styles to this line via a child builder so they don't leak to later elements.
                let child = StarXpandCommand.PrinterBuilder()
                    .styleAlignment(alignment(align))
                    .styleBold(bold)
                    .styleUnderLine(underline)
                    .styleInvert(invert)
                    .styleMagnification(magnification(size))
                    .actionPrintText(s + "\n")
                _ = pb.add(child)
            case let .columns(cells):
                _ = pb.styleAlignment(.left)
                for line in ColumnLayout.format(cells, doc.paper) { _ = pb.actionPrintText(line + "\n") }
            case .divider:
                _ = pb.styleAlignment(.left)
                    .actionPrintText(String(repeating: "-", count: doc.paper.charsPerLine) + "\n")
            case let .feed(n):
                _ = pb.actionFeedLine(n)
            case let .image(bytes, align, _, _):
                if let img = UIImage(data: bytes) {
                    _ = pb.styleAlignment(alignment(align))
                        .actionPrintImage(StarXpandCommand.Printer.ImageParameter(image: img, width: doc.paper.widthPx))
                }
            case let .imageUrl(url, align, _, _):
                if let cg = CodeImages.download(url) {
                    _ = pb.styleAlignment(alignment(align))
                        .actionPrintImage(StarXpandCommand.Printer.ImageParameter(image: UIImage(cgImage: cg), width: doc.paper.widthPx))
                }
            case let .barcode(data, sym, h, align):
                _ = pb.styleAlignment(alignment(align)).actionPrintBarcode(
                    StarXpandCommand.Printer.BarcodeParameter(content: data, symbology: barcodeSymbology(sym))
                        .setHeight(Double(max(1, h)) / 8.0) // dots → mm (≈8 dots/mm)
                        .setPrintHRI(true))
            case let .qr(data, sizeDots, level, align):
                _ = pb.styleAlignment(alignment(align)).actionPrintQRCode(
                    StarXpandCommand.Printer.QRCodeParameter(content: data)
                        .setLevel(qrLevel(level))
                        .setCellSize(max(1, min(8, sizeDots / 24))))
            case .raw:
                break // StarXpand's builder has no generic raw passthrough
            }
        }
        if doc.cut != .none { _ = pb.actionCut(cutType(doc.cut)) }
        let builder = StarXpandCommand.StarXpandCommandBuilder()
        _ = builder.addDocument(StarXpandCommand.DocumentBuilder().addPrinter(pb))
        return builder.getCommands()
    }

    // MARK: mappers

    private func alignment(_ a: TextAlign) -> StarXpandCommand.Printer.Alignment {
        switch a { case .left: return .left; case .center: return .center; case .right: return .right }
    }

    private func magnification(_ s: TextSize) -> StarXpandCommand.MagnificationParameter {
        switch s {
        case .normal: return StarXpandCommand.MagnificationParameter(width: 1, height: 1)
        case .wide: return StarXpandCommand.MagnificationParameter(width: 2, height: 1)
        case .tall: return StarXpandCommand.MagnificationParameter(width: 1, height: 2)
        case .large: return StarXpandCommand.MagnificationParameter(width: 2, height: 2)
        }
    }

    // Our BarcodeSymbology wire strings → Star's enum. The enum exposes both .ean13 and .jan13 as
    // distinct cases (verified against the StarXpand iOS API reference), so EAN-13 maps to .ean13 —
    // matching Android's Ean13 mapping.
    private func barcodeSymbology(_ s: String) -> StarXpandCommand.Printer.BarcodeSymbology {
        switch s.uppercased() {
        case "CODE39": return .code39
        case "EAN13": return .ean13
        case "UPCA": return .upcA
        default: return .code128
        }
    }

    private func qrLevel(_ s: String) -> StarXpandCommand.Printer.QRCodeLevel {
        switch s.uppercased() { case "L": return .l; case "Q": return .q; case "H": return .h; default: return .m }
    }

    private func cutType(_ c: CutType) -> StarXpandCommand.Printer.CutType {
        switch c { case .full: return .full; default: return .partial }
    }

    private static func interfaceType(_ s: String) -> InterfaceType {
        switch s.uppercased() {
        case "BLUETOOTH": return .bluetooth
        case "BLUETOOTHLE": return .bluetoothLE
        case "USB": return .usb
        default: return .lan
        }
    }

    // Map proven connectivity failures to the actionable NOT_CONNECTED ("can't reach the printer");
    // every other StarIO10Error is technical → generic IO (real cause logged via `details`). Actionable
    // hardware faults (cover/paper/etc.) are already caught by the preflight above. Cases verified against
    // the StarIO10Error enum in the StarXpand iOS manual. (serverCommunication is omitted — not verified
    // as an iOS enum case; Android maps its equivalent exception, iOS keeps it generic rather than guess.)
    private static func reason(for error: Error) -> String {
        guard let e = error as? StarIO10Error else { return "IO" }
        switch e {
        case .notFound, .communication, .illegalDeviceState: return "NOT_CONNECTED"
        default: return "IO"
        }
    }
}
