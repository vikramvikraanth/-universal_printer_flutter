# Changelog

## 0.0.7

- Maintenance release.

## 0.0.6

- **Discovery now reports print capability.** Each `DiscoveredPrinter` carries `supportedPrintTypes`
  (`[text, image]` for generic/thermal printers, `[text]` for 9-pin impact) plus a `supportsImage`
  convenience getter — so the search result tells you whether a printer can do image + text or text only.

## 0.0.5

- Maintenance release — no functional changes since 0.0.4 (release automation added in-repo).


## 0.0.4

- **User-friendly error messages.** `PrintResult` now returns `userMessage`/`displayMessage` (safe to
  show the operator) alongside `details` (technical, for logging). Actionable faults (paper/cover/
  cutter/connection/permission) get a specific instruction; internal/technical failures get a generic
  message while `details` still carries the raw cause.
- Success warnings now include friendly `warningMessages` (e.g. "Paper is running low…").
- `message` is retained as a back-compat alias for `details`.

## 0.0.3

- `networkPrinter(host, {brand, paperWidthMm})` — the printer object now carries the discovered
  **brand** and **paper width**, so it knows what it's driving (foundation for brand-aware behaviour).
- `printerFor(discovered)` auto-fills `brand` + the first `supportedPaperWidthsMm` from discovery.
- **Printer paper drives the render width:** when a printer knows its paper width, `printReceipt`
  re-paginates the document to it — HTML, bitmap, and text all render at the printer's real print width.
- **Calibration fix:** `PaperWidth.MM_72` corrected to 64mm printable / 512px (72mm − 8mm margin);
  was mistakenly 576px (same as 80mm). MM_58 = 48mm/384px, MM_80 = 72mm/576px unchanged.

## 0.0.2

- **Built-in printer discovery:** `discoverBuiltIn()` detects the host device's own Sunmi/iMin printer
  (via the vendor print-service package) and is folded into `discoverAll()`.
- `PrinterConnectionType.builtIn` + `DiscoveredPrinter.isBuiltIn`; new `PrinterBrand.imin`.
- **Supported paper size:** `DiscoveredPrinter.supportedPaperWidthsMm` (e.g. `[58]` / `[80]`), queried
  live from the vendor SDK for built-in printers.

## 0.0.1

- Initial Android implementation: a federated Flutter plugin wrapping the Universal Printer Kotlin SDK
  over a `MethodChannel`.
- **Discovery:** Epson (ENPC), Sunmi (mDNS), Zebra, SNMP (Bixolon/Citizen/Brother/Seiko), generic
  network (TCP-9100), USB — plus `discoverAll` and `ping`.
- **Printing:** ESC/POS network & USB, Star (StarXpand), Sunmi/iMin built-in; centralized
  `PrintDocument` builder with `PrintType.text` (native ESC/POS) and `PrintType.image`
  (HTML → WebView → bitmap); `receiptHtml` for previews.
- Impact/dot-matrix detection (`isImpact`) and `effectiveEmulation` default (ESC/POS).
- iOS: no-op stub that returns `UNSUPPORTED_PLATFORM` (no crash); Android-only for now.
