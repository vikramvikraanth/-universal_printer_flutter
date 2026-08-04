package com.universalprinter.escpos

import com.dantsu.escposprinter.EscPosCharsetEncoding
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.DeviceConnection
import com.universalprinter.model.PrintDocument

/**
 * Builds a DantSu [EscPosPrinter] for [document]. When the document sets a [PrintDocument.charset],
 * the printer is created with an [EscPosCharsetEncoding] so text is emitted in that native code page
 * (`ESC t n`) instead of the default — shared by the network and USB backends (DRY).
 */
internal fun buildEscPosPrinter(connection: DeviceConnection, document: PrintDocument, dpi: Int): EscPosPrinter {
    val paper = document.paper
    val charset = document.charset
    return if (charset != null) {
        EscPosPrinter(connection, dpi, paper.printableWidthMM, paper.charsPerLine, EscPosCharsetEncoding(charset.javaCharsetName, charset.codePageId))
    } else {
        EscPosPrinter(connection, dpi, paper.printableWidthMM, paper.charsPerLine)
    }
}
