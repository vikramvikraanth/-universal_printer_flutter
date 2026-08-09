import Foundation

// Swift mirror of the wire contract. Enums decode from their Kotlin `.name` strings so the exact
// same MethodChannel payloads parse on both platforms.

enum PaperWidth: String {
    case mm58 = "MM_58", mm72 = "MM_72", mm80 = "MM_80", impact76 = "IMPACT_76"

    var widthPx: Int { switch self { case .mm58: return 384; case .mm72: return 512; case .mm80: return 576; case .impact76: return 200 } }
    var charsPerLine: Int { switch self { case .mm58: return 32; case .mm72: return 42; case .mm80: return 48; case .impact76: return 33 } }
    var maxColumns: Int { switch self { case .mm58: return 3; case .mm72: return 4; case .mm80: return 5; case .impact76: return 2 } }

    static func from(_ s: String?) -> PaperWidth { PaperWidth(rawValue: (s ?? "").uppercased()) ?? .mm80 }
    static func ofMillimeters(_ mm: Int) -> PaperWidth {
        switch mm { case 58: return .mm58; case 72: return .mm72; case 80: return .mm80; default: return .mm80 }
    }
}

enum TextAlign: String {
    case left = "LEFT", center = "CENTER", right = "RIGHT"
    static func from(_ s: String?) -> TextAlign { TextAlign(rawValue: (s ?? "").uppercased()) ?? .left }
}

enum TextSize: String {
    case normal = "NORMAL", wide = "WIDE", tall = "TALL", large = "LARGE"
    static func from(_ s: String?) -> TextSize { TextSize(rawValue: (s ?? "").uppercased()) ?? .normal }
}

enum CutType: String {
    case none = "NONE", partial = "PARTIAL", full = "FULL"
    static func from(_ s: String?) -> CutType { CutType(rawValue: (s ?? "").uppercased()) ?? .partial }
}

struct ReceiptColumn {
    let text: String
    let weight: Int
    let align: TextAlign
}

enum ReceiptElement {
    case text(String, align: TextAlign, bold: Bool, underline: Bool, invert: Bool, size: TextSize)
    case columns([ReceiptColumn])
    case imageUrl(String, align: TextAlign, invert: Bool, dither: Bool)
    case image(Data, align: TextAlign, invert: Bool, dither: Bool)
    case barcode(String, symbology: String, heightDots: Int, align: TextAlign)
    case qr(String, sizeDots: Int, errorLevel: String, align: TextAlign)
    case feed(Int)
    case divider
    case raw(Data)
}

struct ReceiptDocument {
    let paper: PaperWidth
    let cut: CutType
    let elements: [ReceiptElement]
}

// Discovery result — serialized to the same dict keys Android emits.
struct DiscoveredPrinter {
    var name: String
    var connectionType: String       // "NETWORK" | "USB"
    var ipAddress: String?
    var port: Int = 9100
    var macAddress: String?
    var serialNumber: String?
    var brand: String = "UNKNOWN"
    var model: String?
    var emulation: String?
    var vendorId: Int?
    var productId: Int?
    var usbDeviceName: String?
    var supportedPaperWidthsMm: [Int] = []

    static let impactTokens = ["TM-U", "SP700", "SP742", "SRP-270", "SRP-275"]

    var isImpact: Bool {
        let id = (model ?? name).uppercased()
        return DiscoveredPrinter.impactTokens.contains { id.contains($0) }
    }
    var isBuiltIn: Bool { connectionType == "BUILT_IN" }
    // Text is universal; image works on everything except 9-pin impact printers.
    var supportedPrintTypes: [String] { isImpact ? ["TEXT"] : ["TEXT", "IMAGE"] }
    var supportsImage: Bool { !isImpact }
    var effectiveEmulation: String { emulation ?? "ESC/POS" }

    func toMap() -> [String: Any] {
        // The FlutterStandardMessageCodec can't encode Swift `nil`; use NSNull (decodes to null in Dart).
        func v(_ x: Any?) -> Any { x ?? NSNull() }
        return [
            "name": name, "connectionType": connectionType, "ipAddress": v(ipAddress), "port": port,
            "macAddress": v(macAddress), "serialNumber": v(serialNumber), "brand": brand, "model": v(model),
            "emulation": v(emulation), "vendorId": v(vendorId), "productId": v(productId),
            "usbDeviceName": v(usbDeviceName), "isImpact": isImpact, "isBuiltIn": isBuiltIn,
            "supportedPaperWidthsMm": supportedPaperWidthsMm,
            "supportedPrintTypes": supportedPrintTypes, "supportsImage": supportsImage,
            "effectiveEmulation": effectiveEmulation,
        ]
    }
}
