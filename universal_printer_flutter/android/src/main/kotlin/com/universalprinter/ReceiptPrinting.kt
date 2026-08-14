package com.universalprinter

import android.content.Context
import com.universalprinter.html.HtmlReceiptRasterizer
import com.universalprinter.html.ReceiptHtmlRenderer
import com.universalprinter.html.ReceiptImages
import com.universalprinter.image.ImageCache
import com.universalprinter.image.ImageResolver
import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintResult
import com.universalprinter.model.PrintType
import com.universalprinter.model.withPaper

/**
 * Renders [document] to a self-contained HTML string (embedded logo images + real, scannable
 * barcodes/QR as data-URIs). Use it for the app's receipt preview/template view, or as the source
 * of the [PrintType.IMAGE] print path. Pure rendering — does not print.
 */
suspend fun renderReceiptHtml(context: Context, document: PrintDocument): String {
    val resolved = ImageResolver.resolve(document) { ImageCache.load(context, it) }
    val images = ReceiptImages(resolved.paper)
    return ReceiptHtmlRenderer.render(resolved, images::imageEncoder, images::codeEncoder)
}

/** Warm the offline image cache for [document]'s URL images while online, so a later print works offline. */
suspend fun preloadReceiptImages(context: Context, document: PrintDocument) =
    ImageCache.preload(context, ImageResolver.urls(document))

/**
 * Common print entry. [PrintType.TEXT] prints via the native ESC/POS / vendor path (fast);
 * [PrintType.IMAGE] renders the receipt HTML to a bitmap (offscreen WebView) and prints that single
 * image (max fidelity — same output as the preview). [context] is required (WebView + code generation).
 */
suspend fun Printer.printReceipt(
    context: Context,
    document: PrintDocument,
    type: PrintType = PrintType.TEXT,
): PrintResult {
    // If the printer knows its physical paper width (from discovery), re-paginate to it so the HTML/
    // bitmap/text render at the printer's real print width — you can't print wider than the paper.
    val sized = impactAwarePaper(isImpact, paperWidthMm)?.let { document.withPaper(it) } ?: document
    // Resolve URL images to cached bitmaps once, up front — both print types then work offline.
    val resolved = ImageResolver.resolve(sized) { ImageCache.load(context, it) }
    return routePrint(type, isImpact, resolved, ::print) { doc ->
        val html = renderReceiptHtml(context, doc) // doc already resolved → the inner resolve is a no-op fast path
        val bitmap = HtmlReceiptRasterizer(context).toBitmap(html, doc.paper.widthPx)
        PrintDocument.image(bitmap, doc.paper, doc.cut)
    }
}

/**
 * The paper to re-paginate to before rendering, or null to leave the document's own paper.
 * An **impact** printer is always the 9-pin ~76mm class ([PaperWidth.IMPACT_76], 33-char Font A) — its
 * width can't be inferred from a mm value (discovery may report 76/80), so `isImpact` is the authority.
 * Otherwise use the discovered [paperWidthMm]. Pure — unit-testable.
 */
internal fun impactAwarePaper(isImpact: Boolean, paperWidthMm: Int?): PaperWidth? = when {
    isImpact -> PaperWidth.IMPACT_76
    paperWidthMm != null -> PaperWidth.ofMillimeters(paperWidthMm)
    else -> null
}

/** Pure routing between text and image paths — testable without a WebView. */
internal suspend fun routePrint(
    type: PrintType,
    isImpact: Boolean,
    document: PrintDocument,
    print: suspend (PrintDocument) -> PrintResult,
    toImageDoc: suspend (PrintDocument) -> PrintDocument,
): PrintResult = when {
    // 9-pin impact printers can't raster, so the IMAGE path is meaningless — and it would produce a
    // single-image doc that the backend's impact text-only pass then strips to nothing. Force TEXT.
    isImpact || type == PrintType.TEXT -> print(document)
    else -> print(toImageDoc(document))
}
