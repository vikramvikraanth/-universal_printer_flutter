package com.universalprinter.escpos

import com.dantsu.escposprinter.exceptions.EscPosBarcodeException
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import com.dantsu.escposprinter.exceptions.EscPosEncodingException
import com.dantsu.escposprinter.exceptions.EscPosParserException
import com.universalprinter.model.PrintErrorReason
import java.io.IOException

/**
 * Maps a DantSu/socket failure to a typed [PrintErrorReason]. Connectivity errors
 * ([EscPosConnectionException]/[IOException]) → NOT_CONNECTED; content errors
 * (parser/encoding/barcode) → CONTENT_INVALID; anything else → UNKNOWN. All four exception types
 * were verified present in the resolved DantSu AAR.
 */
internal fun escPosReason(t: Throwable): PrintErrorReason = when (t) {
    is EscPosConnectionException, is IOException -> PrintErrorReason.NOT_CONNECTED
    is EscPosParserException, is EscPosEncodingException, is EscPosBarcodeException -> PrintErrorReason.CONTENT_INVALID
    else -> PrintErrorReason.UNKNOWN
}
