package com.universalprinter.model

/**
 * How textual elements ([PrintElement.Text]/[PrintElement.Columns]) are put on paper.
 *
 * - [AUTO] (default): Latin/Western text prints via the fast native font path; any run containing
 *   non-Latin script (CJK, Arabic, Hebrew, Thai, Cyrillic, Greek, …) is rendered to a bitmap on the
 *   device and printed as an image, so it works on any raster-capable printer regardless of the
 *   printer's font ROM / codepages.
 * - [TEXT]: force the native font path (fastest; Western-only — non-Latin may print as garbage).
 * - [IMAGE]: render all text to bitmaps (guaranteed glyphs / WYSIWYG; slower, more ink).
 *
 * Barcodes and QR codes are always printed natively (rasterizing them hurts scannability).
 */
enum class RenderMode { AUTO, TEXT, IMAGE }
