import Foundation
import Flutter

// Wire <-> Swift translation, mirror of Android's Bridge.kt. Parses the `document` map into a
// ReceiptDocument (same "type" switch) and builds the PrintResult dicts.
enum Bridge {

    static func buildDocument(_ map: [String: Any]) -> ReceiptDocument {
        let paper = PaperWidth.from(map["paper"] as? String)
        let cut = CutType.from(map["cut"] as? String)
        var elements: [ReceiptElement] = []

        for item in (map["elements"] as? [Any]) ?? [] {
            guard let e = item as? [String: Any] else { continue }
            switch (e["type"] as? String) ?? "" {
            case "text":
                elements.append(.text(str(e["text"]), align: TextAlign.from(e["align"] as? String),
                                      bold: bool(e["bold"]), underline: bool(e["underline"]),
                                      invert: bool(e["invert"]), size: TextSize.from(e["size"] as? String)))
            case "columns":
                let cells = ((e["cells"] as? [Any]) ?? []).compactMap { c -> ReceiptColumn? in
                    guard let cm = c as? [String: Any] else { return nil }
                    return ReceiptColumn(text: str(cm["text"]), weight: intOr(cm["weight"], 1),
                                         align: TextAlign.from(cm["align"] as? String))
                }
                elements.append(.columns(cells))
            case "row":
                elements.append(.columns([
                    ReceiptColumn(text: str(e["left"]), weight: intOr(e["leftWeight"], 1), align: .left),
                    ReceiptColumn(text: str(e["right"]), weight: intOr(e["rightWeight"], 1), align: .right),
                ]))
            case "imageUrl":
                elements.append(.imageUrl(str(e["url"]), align: TextAlign.from(e["align"] as? String),
                                          invert: bool(e["invert"]), dither: bool(e["dither"])))
            case "image":
                if let d = bytes(e["bytes"]) {
                    elements.append(.image(d, align: TextAlign.from(e["align"] as? String),
                                           invert: bool(e["invert"]), dither: bool(e["dither"])))
                }
            case "barcode":
                elements.append(.barcode(str(e["data"]), symbology: (e["symbology"] as? String) ?? "CODE128",
                                         heightDots: intOr(e["heightDots"], 100),
                                         align: TextAlign.from(e["align"] as? String)))
            case "qr":
                elements.append(.qr(str(e["data"]), sizeDots: intOr(e["sizeDots"], 200),
                                    errorLevel: (e["errorLevel"] as? String) ?? "M",
                                    align: TextAlign.from(e["align"] as? String)))
            case "feed":
                elements.append(.feed(intOr(e["lines"], 1)))
            case "divider":
                elements.append(.divider)
            case "raw":
                if let d = bytes(e["bytes"]) { elements.append(.raw(d)) }
            default:
                break
            }
        }
        return ReceiptDocument(paper: paper, cut: cut, elements: elements)
    }

    static func successResult(_ warnings: [String] = []) -> [String: Any] {
        ["status": "success", "warnings": warnings,
         "warningMessages": warnings.map { PrinterMessages.warningMessage($0) }]
    }
    /// [reason] is a PrintErrorReason wire value; [details] is the technical string (logged, not shown).
    static func errorResult(_ reason: String, _ details: String?) -> [String: Any] {
        ["status": "error", "reason": reason,
         "userMessage": PrinterMessages.userMessage(reason), // safe to show the operator
         "details": details ?? "",
         "message": details ?? ""]                            // back-compat (== details)
    }

    // MARK: value helpers (NSNull-safe)

    private static func str(_ v: Any?) -> String { (v as? String) ?? "" }
    private static func bool(_ v: Any?) -> Bool {
        if let b = v as? Bool { return b }
        if let n = v as? NSNumber { return n.boolValue }
        return false
    }
    private static func intOr(_ v: Any?, _ d: Int) -> Int {
        if let i = v as? Int { return i }
        if let n = v as? NSNumber { return n.intValue }
        return d
    }
    private static func bytes(_ v: Any?) -> Data? {
        if let t = v as? FlutterStandardTypedData { return t.data }
        if let d = v as? Data { return d }
        return nil
    }
}
