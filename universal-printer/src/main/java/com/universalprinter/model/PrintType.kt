package com.universalprinter.model

/**
 * How a receipt is put on paper by the common print entry (`Printer.printReceipt`).
 *
 * - [TEXT]: render to native ESC/POS / vendor commands (fast; RenderMode still applies within it).
 * - [IMAGE]: render the receipt to HTML, rasterize it to one bitmap (via WebView), and print that —
 *   maximum layout fidelity and the same output the HTML preview shows, at the cost of speed/ink.
 */
enum class PrintType { TEXT, IMAGE }
