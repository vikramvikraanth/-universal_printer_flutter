import Foundation
import CoreGraphics

// The Swift ESC/POS byte encoder — the iOS counterpart to Android's DantSu text path + EscPosRaster.
// Control bytes are ported 1:1 from EscPosRaster.kt and standard ESC/POS. Produces one Data buffer for
// a whole ReceiptDocument; the transport writes it (paced, for network).
enum EscPos {

    private static let ESC: UInt8 = 0x1B
    private static let GS: UInt8 = 0x1D
    private static let LF: UInt8 = 0x0A

    static func encode(_ doc: ReceiptDocument) -> Data {
        var out = Data([ESC, 0x40]) // ESC @  — initialize

        for el in doc.elements {
            switch el {
            case let .text(s, align, bold, underline, invert, size):
                out.append(contentsOf: alignCmd(align))
                out.append(contentsOf: [ESC, 0x45, bold ? 1 : 0])       // ESC E — bold
                out.append(contentsOf: [ESC, 0x2D, underline ? 1 : 0])  // ESC - — underline
                out.append(contentsOf: [GS, 0x42, invert ? 1 : 0])      // GS B  — reverse (white on black)
                out.append(contentsOf: [GS, 0x21, sizeByte(size)])      // GS !  — character size
                out.append(encodeText(s))
                out.append(LF)
                out.append(contentsOf: [ESC, 0x45, 0, ESC, 0x2D, 0, GS, 0x42, 0, GS, 0x21, 0]) // reset

            case let .columns(cells):
                out.append(contentsOf: alignCmd(.left))
                for line in ColumnLayout.format(cells, doc.paper) {
                    out.append(encodeText(line)); out.append(LF)
                }

            case .divider:
                out.append(encodeText(String(repeating: "-", count: doc.paper.charsPerLine)))
                out.append(LF)

            case let .feed(n):
                out.append(contentsOf: [ESC, 0x64, UInt8(max(0, min(255, n)))]) // ESC d — feed n lines

            case let .barcode(data, sym, h, align):
                appendRaster(&out, CodeImages.barcode(data, symbology: sym, heightDots: h), align, doc.paper)

            case let .qr(data, size, level, align):
                appendRaster(&out, CodeImages.qr(data, sizeDots: size, errorLevel: level), align, doc.paper)

            case let .image(bytes, align, _, _):
                appendRaster(&out, CodeImages.decode(bytes), align, doc.paper)

            case let .imageUrl(url, align, _, _):
                appendRaster(&out, CodeImages.download(url), align, doc.paper)

            case let .raw(d):
                out.append(d)
            }
        }

        out.append(LF)
        out.append(contentsOf: cutCmd(doc.cut))
        return out
    }

    // ESC a n — 0 left, 1 center, 2 right.
    private static func alignCmd(_ a: TextAlign) -> [UInt8] {
        [ESC, 0x61, a == .center ? 1 : (a == .right ? 2 : 0)]
    }

    // GS V m — 0 full, 1 partial; nothing for NONE.
    private static func cutCmd(_ c: CutType) -> [UInt8] {
        switch c { case .full: return [GS, 0x56, 0]; case .partial: return [GS, 0x56, 1]; case .none: return [] }
    }

    // GS ! n — width high nibble, height low nibble (double = 1).
    private static func sizeByte(_ s: TextSize) -> UInt8 {
        switch s { case .normal: return 0x00; case .wide: return 0x10; case .tall: return 0x01; case .large: return 0x11 }
    }

    private static func appendRaster(_ out: inout Data, _ image: CGImage?, _ align: TextAlign, _ paper: PaperWidth) {
        guard let img = image else { return }
        out.append(contentsOf: alignCmd(align))
        for band in CodeImages.raster(img, maxWidth: paper.widthPx) { out.append(band) }
    }

    // ESC/POS printers speak a byte codepage, not UTF-8. Latin-1 covers Western text; non-Latin is lossy
    // (Android rasterizes it — that path is deferred on iOS).
    private static func encodeText(_ s: String) -> Data {
        if let d = s.data(using: .isoLatin1, allowLossyConversion: true) { return d }
        return s.data(using: .ascii, allowLossyConversion: true) ?? Data()
    }
}
