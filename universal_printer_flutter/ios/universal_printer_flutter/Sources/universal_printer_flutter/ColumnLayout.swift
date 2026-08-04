import Foundation

// Port of the Android `ColumnLayout` (com.universalprinter.util) — weighted, word-wrapped columns to
// fixed-pitch monospace lines. Kept byte-for-byte equivalent so the same row lays out identically.
enum ColumnLayout {

    static func format(_ cells: [ReceiptColumn], _ paper: PaperWidth) -> [String] {
        if cells.isEmpty { return [] }
        let clamped = Array(cells.prefix(paper.maxColumns))
        let widths = columnWidths(clamped, total: paper.charsPerLine)
        let wrapped = clamped.enumerated().map { wrap($0.element.text, width: widths[$0.offset]) }
        let rows = wrapped.map { $0.count }.max() ?? 0
        return (0..<rows).map { r in
            var line = ""
            for i in clamped.indices {
                let cell = r < wrapped[i].count ? wrapped[i][r] : ""
                line += pad(cell, width: widths[i], align: clamped[i].align)
            }
            return line
        }
    }

    static func columnWidths(_ cells: [ReceiptColumn], total: Int) -> [Int] {
        let weights = cells.map { max(1, $0.weight) }
        let sum = weights.reduce(0, +)
        var widths = weights.map { Int(Int64(total) * Int64($0) / Int64(sum)) }
        var used = widths.reduce(0, +)
        var i = 0
        while used < total && !widths.isEmpty {
            widths[i % widths.count] += 1; used += 1; i += 1
        }
        return widths
    }

    static func wrap(_ text: String, width: Int) -> [String] {
        if width <= 0 { return [""] }
        var out: [String] = []
        for rawLine in text.components(separatedBy: "\n") {
            let words = rawLine.split(whereSeparator: { $0 == " " || $0 == "\t" }).map(String.init)
            if words.isEmpty { out.append(""); continue }
            var cur = ""
            for word in words {
                var w = word
                while w.count > width {
                    if !cur.isEmpty { out.append(cur); cur = "" }
                    out.append(String(w.prefix(width)))
                    w = String(w.dropFirst(width))
                }
                if w.isEmpty { continue }
                if cur.isEmpty {
                    cur = w
                } else if cur.count + 1 + w.count <= width {
                    cur += " " + w
                } else {
                    out.append(cur); cur = w
                }
            }
            if !cur.isEmpty { out.append(cur) }
        }
        return out.isEmpty ? [""] : out
    }

    private static func pad(_ s: String, width: Int, align: TextAlign) -> String {
        let t = s.count > width ? String(s.prefix(width)) : s
        let total = width - t.count
        if total <= 0 { return t }
        switch align {
        case .left: return t + String(repeating: " ", count: total)
        case .right: return String(repeating: " ", count: total) + t
        case .center:
            let left = total / 2
            return String(repeating: " ", count: left) + t + String(repeating: " ", count: total - left)
        }
    }
}
