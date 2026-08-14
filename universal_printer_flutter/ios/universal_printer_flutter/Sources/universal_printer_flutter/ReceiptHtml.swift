import Foundation
import CoreGraphics
import ImageIO

// Swift port of Android's ReceiptHtmlRenderer: renders a ReceiptDocument to a self-contained HTML string
// (embedded logo images + real, scannable barcodes/QR as base64 data-URIs) for the app's receipt preview.
// Markup + CSS are kept byte-for-byte in step with the Kotlin renderer so both platforms preview identically.
enum ReceiptHtml {

    static func render(_ doc: ReceiptDocument) -> String {
        var body = ""
        for el in doc.elements {
            switch el {
            case let .text(s, align, bold, underline, invert, size):
                body += text(s, align, bold, underline, invert, size)
            case let .columns(cells):
                body += columns(cells)
            case let .image(bytes, align, invert, _):
                if let uri = CodeImages.decode(bytes).flatMap(pngDataURI) {
                    body += box(align, "<img src=\"\(uri)\"\(invert ? " style=\"filter:invert(1)\"" : "")>")
                }
            case let .imageUrl(url, align, invert, _):
                if let uri = CodeImages.download(url).flatMap(pngDataURI) {
                    body += box(align, "<img src=\"\(uri)\"\(invert ? " style=\"filter:invert(1)\"" : "")>")
                }
            case let .barcode(data, sym, h, align):
                if let uri = CodeImages.barcode(data, symbology: sym, heightDots: h).flatMap(pngDataURI) {
                    body += box(align, "<img src=\"\(uri)\">")
                }
            case let .qr(data, sizeDots, level, align):
                if let uri = CodeImages.qr(data, sizeDots: sizeDots, errorLevel: level).flatMap(pngDataURI) {
                    body += box(align, "<img src=\"\(uri)\">")
                }
            case let .feed(n):
                body += "<div style=\"height:\(n)em\"></div>"
            case .divider:
                body += "<div class=\"divider\"></div>"
            case .raw:
                break // raw bytes have no HTML representation
            }
        }
        return document(doc.paper.widthPx, body)
    }

    // viewport width = paper dots → the WebView lays out at exactly widthPx CSS px; body is 100% of it.
    private static func document(_ widthPx: Int, _ body: String) -> String {
        "<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=\(widthPx)\"><style>"
            + "*{margin:0;padding:0;box-sizing:border-box}"
            + "body{width:100%;font-family:monospace;font-size:24px;line-height:1.25;color:#000;background:#fff}"
            + ".line{white-space:pre-wrap;overflow-wrap:break-word}"
            + ".row{display:flex;width:100%}"
            + ".cell{white-space:pre-wrap;overflow-wrap:break-word;padding-right:6px}"
            + ".cell:last-child{padding-right:0}"
            + ".divider{border-top:1px dashed #000;margin:4px 0}"
            + ".inv{background:#000;color:#fff}"
            + "img{max-width:100%}"
            + "</style></head><body>\(body)</body></html>"
    }

    private static func text(_ s: String, _ align: TextAlign, _ bold: Bool, _ underline: Bool, _ invert: Bool, _ size: TextSize) -> String {
        var inner = escape(s)
        let style = sizeStyle(size)
        if !style.isEmpty { inner = "<span style=\"\(style)\">\(inner)</span>" }
        if bold { inner = "<b>\(inner)</b>" }
        if underline { inner = "<u>\(inner)</u>" }
        if invert { inner = "<span class=\"inv\">\(inner)</span>" }
        return "<div class=\"line\" style=\"text-align:\(css(align))\">\(inner)</div>"
    }

    private static func columns(_ cells: [ReceiptColumn]) -> String {
        let inner = cells.map {
            "<div class=\"cell\" style=\"flex:\($0.weight);text-align:\(css($0.align))\">\(escape($0.text))</div>"
        }.joined()
        return "<div class=\"row\">\(inner)</div>"
    }

    private static func box(_ align: TextAlign, _ inner: String) -> String {
        "<div style=\"text-align:\(css(align))\">\(inner)</div>"
    }

    private static func css(_ a: TextAlign) -> String {
        switch a { case .left: return "left"; case .center: return "center"; case .right: return "right" }
    }

    // Double-width/height via a contained transform (headers are typically standalone lines).
    private static func sizeStyle(_ size: TextSize) -> String {
        switch size {
        case .normal: return ""
        case .wide: return "display:inline-block;transform:scaleX(2);transform-origin:left center"
        case .tall: return "display:inline-block;transform:scaleY(2);transform-origin:left top"
        case .large: return "display:inline-block;transform:scale(2);transform-origin:left top"
        }
    }

    private static func escape(_ s: String) -> String {
        var out = ""
        out.reserveCapacity(s.count)
        for ch in s {
            switch ch {
            case "&": out += "&amp;"
            case "<": out += "&lt;"
            case ">": out += "&gt;"
            case "\"": out += "&quot;"
            case "'": out += "&#39;"
            case "\n": out += "<br>"
            default: out.append(ch)
            }
        }
        return out
    }

    // CGImage → `data:image/png;base64,…` URI (mirrors Android's data-URI image encoder).
    private static func pngDataURI(_ image: CGImage) -> String? {
        let data = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(data, "public.png" as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(dest, image, nil)
        guard CGImageDestinationFinalize(dest) else { return nil }
        return "data:image/png;base64,\((data as Data).base64EncodedString())"
    }
}
