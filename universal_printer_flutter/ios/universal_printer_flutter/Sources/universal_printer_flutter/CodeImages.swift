import Foundation
import CoreImage
import CoreGraphics
import ImageIO

// Barcode/QR via CoreImage (replaces ZXing) + CGImage → 1-bit `GS v 0` raster (port of EscPosRaster.bands).
enum CodeImages {

    private static let ciContext = CIContext(options: nil)

    static func qr(_ data: String, sizeDots: Int, errorLevel: String) -> CGImage? {
        guard let f = CIFilter(name: "CIQRCodeGenerator") else { return nil }
        f.setValue(data.data(using: .utf8), forKey: "inputMessage")
        let lvl = ["L", "M", "Q", "H"].contains(errorLevel.uppercased()) ? errorLevel.uppercased() : "M"
        f.setValue(lvl, forKey: "inputCorrectionLevel")
        guard let out = f.outputImage, out.extent.width > 0 else { return nil }
        let scale = max(1, CGFloat(sizeDots) / out.extent.width)
        let scaled = out.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        return ciContext.createCGImage(scaled, from: scaled.extent)
    }

    static func barcode(_ data: String, symbology: String, heightDots: Int) -> CGImage? {
        // CoreImage ships a Code128 generator; other symbologies fall back to it for v1.
        guard let f = CIFilter(name: "CICode128BarcodeGenerator") else { return nil }
        f.setValue(data.data(using: .ascii), forKey: "inputMessage")
        guard let out = f.outputImage, out.extent.height > 0 else { return nil }
        let sy = max(1, CGFloat(heightDots) / out.extent.height)
        let scaled = out.transformed(by: CGAffineTransform(scaleX: 2, y: sy))
        return ciContext.createCGImage(scaled, from: scaled.extent)
    }

    static func decode(_ bytes: Data) -> CGImage? {
        guard let src = CGImageSourceCreateWithData(bytes as CFData, nil) else { return nil }
        return CGImageSourceCreateImageAtIndex(src, 0, nil)
    }

    /// Synchronous fetch (runs on the print background queue). file:// reads locally; http(s) downloads.
    static func download(_ urlString: String) -> CGImage? {
        guard let url = URL(string: urlString) else { return nil }
        if url.isFileURL { return (try? Data(contentsOf: url)).flatMap(decode) }
        var result: Data?
        let sem = DispatchSemaphore(value: 0)
        URLSession.shared.dataTask(with: url) { d, _, _ in result = d; sem.signal() }.resume()
        _ = sem.wait(timeout: .now() + 8)
        return result.flatMap(decode)
    }

    /// GS v 0 raster bands, scaled to at most [maxWidth] dots. 1-bit MSB-first, dark = luminance < 384.
    static func raster(_ image: CGImage, maxWidth: Int, bandHeight: Int = 128) -> [Data] {
        let w = min(image.width, max(1, maxWidth))
        let h = max(1, Int((Double(image.height) * Double(w) / Double(max(1, image.width))).rounded()))
        let widthBytes = (w + 7) / 8
        var pixels = [UInt8](repeating: 0xFF, count: w * h * 4)
        let cs = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(data: &pixels, width: w, height: h, bitsPerComponent: 8,
                                  bytesPerRow: w * 4, space: cs,
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return [] }
        ctx.setFillColor(red: 1, green: 1, blue: 1, alpha: 1)
        ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
        // Flip so buffer row 0 == top of the image.
        ctx.translateBy(x: 0, y: CGFloat(h))
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))

        var bands: [Data] = []
        var y = 0
        while y < h {
            let bh = min(bandHeight, h - y)
            var data = Data([0x1D, 0x76, 0x30, 0x00,
                             UInt8(widthBytes & 0xFF), UInt8((widthBytes >> 8) & 0xFF),
                             UInt8(bh & 0xFF), UInt8((bh >> 8) & 0xFF)])
            for row in 0..<bh {
                for bx in 0..<widthBytes {
                    var b: Int = 0
                    for bit in 0..<8 {
                        let x = bx * 8 + bit
                        if x < w {
                            let idx = ((y + row) * w + x) * 4
                            let lum = Int(pixels[idx]) + Int(pixels[idx + 1]) + Int(pixels[idx + 2])
                            if lum < 384 { b |= (0x80 >> bit) }
                        }
                    }
                    data.append(UInt8(b))
                }
            }
            bands.append(data)
            y += bh
        }
        return bands
    }
}
