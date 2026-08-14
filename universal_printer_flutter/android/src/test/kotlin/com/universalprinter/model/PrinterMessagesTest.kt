package com.universalprinter.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterMessagesTest {

    private val actionable = setOf(
        PrintErrorReason.PAPER_OUT,
        PrintErrorReason.COVER_OPEN,
        PrintErrorReason.CUTTER_ERROR,
        PrintErrorReason.PAPER_JAM,
        PrintErrorReason.OVERHEATED,
        PrintErrorReason.HOLDING_PAPER,
        PrintErrorReason.NOT_CONNECTED,
        PrintErrorReason.TIMEOUT,
        PrintErrorReason.PERMISSION_DENIED,
    )
    private val technical = setOf(
        PrintErrorReason.CONTENT_INVALID,
        PrintErrorReason.UNSUPPORTED,
        PrintErrorReason.IO,
        PrintErrorReason.UNKNOWN,
    )

    @Test
    fun everyReasonHasANonBlankUserMessage() {
        for (r in PrintErrorReason.values()) {
            assertTrue("$r must have a message", PrinterMessages.userMessage(r).isNotBlank())
        }
    }

    @Test
    fun technicalReasonsReturnExactlyTheGenericMessage() {
        for (r in technical) {
            assertEquals("$r should be generic", PrinterMessages.GENERIC, PrinterMessages.userMessage(r))
        }
    }

    @Test
    fun actionableReasonsHaveSpecificMessagesNotTheGeneric() {
        for (r in actionable) {
            assertNotEquals("$r should be specific", PrinterMessages.GENERIC, PrinterMessages.userMessage(r))
        }
        assertTrue(PrinterMessages.userMessage(PrintErrorReason.PAPER_OUT).contains("paper", ignoreCase = true))
        assertTrue(PrinterMessages.userMessage(PrintErrorReason.COVER_OPEN).contains("cover", ignoreCase = true))
        assertTrue(PrinterMessages.userMessage(PrintErrorReason.CUTTER_ERROR).contains("cutter", ignoreCase = true))
    }

    @Test
    fun warningHasAFriendlyMessage() {
        assertTrue(PrinterMessages.warningMessage(PrinterWarning.PAPER_NEAR_END).contains("low", ignoreCase = true))
    }
}
