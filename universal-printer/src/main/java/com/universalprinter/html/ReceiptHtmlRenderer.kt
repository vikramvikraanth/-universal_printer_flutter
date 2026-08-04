package com.universalprinter.html

import android.graphics.Bitmap
import com.universalprinter.model.Align
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement
import com.universalprinter.model.TextSize

/**
 * Renders a device-agnostic [PrintDocument] to an HTML string — used both as the app-facing preview
 * ("template view") and as the source for the IMAGE print path (HTML → bitmap). Pure: image/QR/barcode
 * bytes are produced by INJECTED encoders (like [com.universalprinter.escpos.EscPosRenderer]'s
 * `imageToHex`), so the HTML assembly itself has no Android dependency and is unit-testable.
 *
 * @param imageEncoder maps a [Bitmap] to an `<img src>` value (e.g. a `data:image/png;base64,…` URI).
 * @param codeEncoder maps a [PrintElement.Barcode]/[PrintElement.QrCode] to an `<img src>` value.
 */
internal object ReceiptHtmlRenderer {

    fun render(
        document: PrintDocument,
        imageEncoder: (Bitmap) -> String,
        codeEncoder: (PrintElement) -> String,
    ): String {
        val body = StringBuilder()
        for (element in document.elements) {
            when (element) {
                is PrintElement.Text -> body.append(text(element))
                is PrintElement.Columns -> body.append(columns(element))
                is PrintElement.Image ->
                    body.append(box(element.align, "<img src=\"${imageEncoder(element.bitmap)}\"${if (element.invert) " style=\"filter:invert(1)\"" else ""}>"))
                is PrintElement.Barcode -> body.append(box(element.align, "<img src=\"${codeEncoder(element)}\">"))
                is PrintElement.QrCode -> body.append(box(element.align, "<img src=\"${codeEncoder(element)}\">"))
                is PrintElement.Feed -> body.append("<div style=\"height:${element.lines}em\"></div>")
                PrintElement.Divider -> body.append("<div class=\"divider\"></div>")
                is PrintElement.Raw -> {} // raw bytes have no HTML representation
                is PrintElement.ImageUrl -> {} // resolved to Image before rendering; skip if unresolved
            }
        }
        return document(document.paper.widthPx, body.toString())
    }

    private fun document(widthPx: Int, body: String): String =
        // viewport width = paper dots → the WebView lays out at exactly widthPx CSS px regardless of
        // device density, so the rasterized bitmap is paper-width-correct. body is 100% of that.
        "<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=$widthPx\"><style>" +
            "*{margin:0;padding:0;box-sizing:border-box}" +
            "body{width:100%;font-family:monospace;font-size:24px;line-height:1.25;color:#000;background:#fff}" +
            ".line{white-space:pre-wrap;overflow-wrap:break-word}" +
            ".row{display:flex;width:100%}" +
            ".cell{white-space:pre-wrap;overflow-wrap:break-word;padding-right:6px}" +
            ".cell:last-child{padding-right:0}" +
            ".divider{border-top:1px dashed #000;margin:4px 0}" +
            ".inv{background:#000;color:#fff}" +
            "img{max-width:100%}" +
            "</style></head><body>$body</body></html>"

    private fun text(t: PrintElement.Text): String {
        var inner = escape(t.text)
        val sizeStyle = sizeStyle(t.size)
        if (sizeStyle.isNotEmpty()) inner = "<span style=\"$sizeStyle\">$inner</span>"
        if (t.bold) inner = "<b>$inner</b>"
        if (t.underline) inner = "<u>$inner</u>"
        if (t.invert) inner = "<span class=\"inv\">$inner</span>"
        return "<div class=\"line\" style=\"text-align:${css(t.align)}\">$inner</div>"
    }

    private fun columns(c: PrintElement.Columns): String {
        val cells = c.cells.joinToString("") { cell ->
            "<div class=\"cell\" style=\"flex:${cell.weight};text-align:${css(cell.align)}\">${escape(cell.text)}</div>"
        }
        return "<div class=\"row\">$cells</div>"
    }

    private fun box(align: Align, inner: String): String = "<div style=\"text-align:${css(align)}\">$inner</div>"

    private fun css(a: Align): String = when (a) { Align.LEFT -> "left"; Align.CENTER -> "center"; Align.RIGHT -> "right" }

    // Double-width/height via a contained transform (headers are typically standalone lines).
    private fun sizeStyle(size: TextSize): String = when (size) {
        TextSize.NORMAL -> ""
        TextSize.WIDE -> "display:inline-block;transform:scaleX(2);transform-origin:left center"
        TextSize.TALL -> "display:inline-block;transform:scaleY(2);transform-origin:left top"
        TextSize.LARGE -> "display:inline-block;transform:scale(2);transform-origin:left top"
    }

    private fun escape(s: String): String = buildString(s.length) {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            '\n' -> append("<br>")
            else -> append(ch)
        }
    }
}
