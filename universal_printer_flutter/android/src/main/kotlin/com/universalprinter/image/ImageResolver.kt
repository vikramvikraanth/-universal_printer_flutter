package com.universalprinter.image

import android.graphics.Bitmap
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement

/**
 * Resolves [PrintElement.ImageUrl] elements to concrete [PrintElement.Image]s (via [loader]) before a
 * document is rendered or printed — so every downstream path (ESC/POS text, HTML, html→bitmap) works on
 * plain bitmaps and prints offline. A URL that fails to load ([loader] returns null) is **dropped** (the
 * rest of the receipt still prints). The [loader] is injected so this policy is unit-testable without Glide.
 */
internal object ImageResolver {

    suspend fun resolve(document: PrintDocument, loader: suspend (String) -> Bitmap?): PrintDocument {
        if (document.elements.none { it is PrintElement.ImageUrl }) return document // fast path
        val resolved = ArrayList<PrintElement>(document.elements.size)
        for (element in document.elements) {
            if (element is PrintElement.ImageUrl) {
                loader(element.url)?.let { bmp ->
                    resolved += PrintElement.Image(bmp, element.align, element.invert, element.dither)
                } // else: skip — print the rest
            } else {
                resolved += element
            }
        }
        return PrintDocument(resolved, document.paper, document.cut, document.openDrawer, document.renderMode, document.charset)
    }

    /** The URLs of all [PrintElement.ImageUrl]s in [document] (for cache pre-warming). */
    fun urls(document: PrintDocument): List<String> =
        document.elements.filterIsInstance<PrintElement.ImageUrl>().map { it.url }
}
