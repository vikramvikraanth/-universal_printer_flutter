package com.universalprinter.universal_printer_flutter

import android.graphics.BitmapFactory
import com.universalprinter.model.Align
import com.universalprinter.model.BarcodeSymbology
import com.universalprinter.model.Column
import com.universalprinter.model.CutType
import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrinterMessages
import com.universalprinter.model.PrintResult
import com.universalprinter.model.PrintType
import com.universalprinter.model.QrErrorLevel
import com.universalprinter.model.RenderMode
import com.universalprinter.model.TextSize
import com.universalprinter.model.printDocument
import com.universalprintersearch.model.DiscoveredPrinter

/**
 * JSON<->Kotlin translation for the MethodChannel contract. Kept separate from the plugin wiring so
 * the serialization shape is easy to audit against the Dart models. All enum values cross the channel
 * as their Kotlin `.name` string; unknown/absent values fall back to the SDK's own defaults.
 */
internal object Bridge {

    // ---- Discovery output ----

    fun printerToMap(p: DiscoveredPrinter): Map<String, Any?> = mapOf(
        "name" to p.name,
        "connectionType" to p.connectionType.name,
        "ipAddress" to p.ipAddress,
        "port" to p.port,
        "macAddress" to p.macAddress,
        "serialNumber" to p.serialNumber,
        "brand" to p.brand.name,
        "model" to p.model,
        "emulation" to p.emulation,
        "vendorId" to p.vendorId,
        "productId" to p.productId,
        "usbDeviceName" to p.usbDeviceName,
        "isImpact" to p.isImpact,
        "isBuiltIn" to p.isBuiltIn,
        "supportedPaperWidthsMm" to p.supportedPaperWidthsMm,
        "effectiveEmulation" to p.effectiveEmulation,
    )

    fun printersToList(list: List<DiscoveredPrinter>): List<Map<String, Any?>> = list.map(::printerToMap)

    // ---- Print result output ----

    fun resultToMap(r: PrintResult): Map<String, Any?> = when (r) {
        is PrintResult.Success -> mapOf(
            "status" to "success",
            "warnings" to r.warnings.map { it.name },
            "warningMessages" to r.warnings.map { PrinterMessages.warningMessage(it) },
        )
        is PrintResult.Error -> mapOf(
            "status" to "error",
            "reason" to r.reason.name,
            "userMessage" to PrinterMessages.userMessage(r.reason), // always safe to show the operator
            "details" to r.message,                                 // technical detail — for logging/support
            "message" to r.message,                                 // back-compat (== details)
        )
    }

    // ---- PrintDocument input (Dart JSON -> Kotlin) ----

    fun buildDocument(map: Map<*, *>): PrintDocument {
        val paper = enumOrDefault(map["paper"] as? String, PaperWidth.MM_80)
        return printDocument(paper) {
            (map["cut"] as? String)?.let { cut = enumOrDefault(it, CutType.PARTIAL) }
            (map["openDrawer"] as? Boolean)?.let { openDrawer = it }
            (map["renderMode"] as? String)?.let { renderMode = enumOrDefault(it, RenderMode.AUTO) }

            val elements = map["elements"] as? List<*> ?: emptyList<Any?>()
            for (raw in elements) {
                val e = raw as? Map<*, *> ?: continue
                when (e["type"] as? String) {
                    "text" -> text(
                        text = e["text"] as? String ?: "",
                        align = align(e["align"]),
                        bold = bool(e["bold"]),
                        underline = bool(e["underline"]),
                        invert = bool(e["invert"]),
                        size = enumOrDefault(e["size"] as? String, TextSize.NORMAL),
                    )
                    "columns" -> {
                        val cells = (e["cells"] as? List<*>).orEmpty().mapNotNull { c ->
                            (c as? Map<*, *>)?.let {
                                Column(it["text"] as? String ?: "", intOr(it["weight"], 1), align(it["align"]))
                            }
                        }
                        columns(*cells.toTypedArray())
                    }
                    "row" -> row(
                        left = e["left"] as? String ?: "",
                        right = e["right"] as? String ?: "",
                        leftWeight = intOr(e["leftWeight"], 1),
                        rightWeight = intOr(e["rightWeight"], 1),
                    )
                    "imageUrl" -> imageUrl(
                        url = e["url"] as? String ?: "",
                        align = align(e["align"]),
                        invert = bool(e["invert"]),
                        dither = bool(e["dither"]),
                    )
                    "image" -> {
                        val bytes = e["bytes"] as? ByteArray
                        val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                        if (bmp != null) image(bmp, align(e["align"]), bool(e["invert"]), bool(e["dither"]))
                    }
                    "barcode" -> barcode(
                        data = e["data"] as? String ?: "",
                        symbology = enumOrDefault(e["symbology"] as? String, BarcodeSymbology.CODE128),
                        heightDots = intOr(e["heightDots"], 100),
                        align = align(e["align"]),
                    )
                    "qr" -> qr(
                        data = e["data"] as? String ?: "",
                        sizeDots = intOr(e["sizeDots"], 200),
                        errorLevel = enumOrDefault(e["errorLevel"] as? String, QrErrorLevel.M),
                        align = align(e["align"]),
                    )
                    "feed" -> feed(intOr(e["lines"], 1))
                    "divider" -> divider()
                    "raw" -> (e["bytes"] as? ByteArray)?.let { raw(it) }
                }
            }
        }
    }

    // ---- helpers ----

    private fun align(v: Any?): Align = enumOrDefault(v as? String, Align.LEFT)
    private fun bool(v: Any?): Boolean = v as? Boolean ?: false
    private fun intOr(v: Any?, default: Int): Int = when (v) {
        is Int -> v
        is Long -> v.toInt()
        is Number -> v.toInt()
        else -> default
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it.uppercase()) }.getOrNull() } ?: default
}
