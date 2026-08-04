package com.universalprinter.html

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.universalprinter.model.BarcodeSymbology
import com.universalprinter.model.PrintElement
import com.universalprinter.model.QrErrorLevel

/** Pure ZXing generation of a barcode/QR [BitMatrix] (no Android). Rendered to a bitmap by the caller. */
internal object ReceiptCodes {

    fun format(s: BarcodeSymbology): BarcodeFormat = when (s) {
        BarcodeSymbology.CODE128 -> BarcodeFormat.CODE_128
        BarcodeSymbology.CODE39 -> BarcodeFormat.CODE_39
        BarcodeSymbology.EAN13 -> BarcodeFormat.EAN_13
        BarcodeSymbology.UPCA -> BarcodeFormat.UPC_A
    }

    fun level(l: QrErrorLevel): ErrorCorrectionLevel = when (l) {
        QrErrorLevel.L -> ErrorCorrectionLevel.L
        QrErrorLevel.M -> ErrorCorrectionLevel.M
        QrErrorLevel.Q -> ErrorCorrectionLevel.Q
        QrErrorLevel.H -> ErrorCorrectionLevel.H
    }

    /** Encodes a [PrintElement.Barcode] or [PrintElement.QrCode] to a [BitMatrix]. [maxWidthPx] bounds 1D width. */
    fun matrix(element: PrintElement, maxWidthPx: Int): BitMatrix = when (element) {
        is PrintElement.QrCode -> {
            val size = element.sizeDots.coerceIn(1, maxWidthPx)
            MultiFormatWriter().encode(
                element.data, BarcodeFormat.QR_CODE, size, size,
                mapOf(EncodeHintType.ERROR_CORRECTION to level(element.errorLevel), EncodeHintType.MARGIN to 1),
            )
        }
        is PrintElement.Barcode -> MultiFormatWriter().encode(
            element.data, format(element.symbology), maxWidthPx, element.heightDots.coerceAtLeast(1),
            mapOf(EncodeHintType.MARGIN to 2),
        )
        else -> throw IllegalArgumentException("not a code element: $element")
    }
}
