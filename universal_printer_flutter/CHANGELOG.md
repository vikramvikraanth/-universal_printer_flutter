# Changelog

## 0.0.11

- **iOS Star printing + `receiptHtml`.** New Star backend (StarXpand / StarIO10), receipt-HTML
  rendering, and a shared `PrinterBackend` protocol on iOS. Star is a hard CocoaPods dependency
  (`StarIO10`), mirroring Android.
- **Impact (9-pin) printers print text-only by default** across Android/iOS/Dart — images are dropped
  and barcodes/QR render as their data string; impact re-paginates to the 33-char `IMPACT_76` paper.
- **Smoother network printing.** The pre-print `DLE EOT` status check is now adaptive: printers that
  don't support real-time status are detected (after 2 consecutive silent probes) and skipped on later
  jobs, with periodic re-probe to recover; status read timeout cut to 500ms. Capable printers keep
  fault-blocking.
- **Better error messages.** Overheating on Sunmi/iMin and unreachable Star now map to actionable
  reasons (`OVERHEATED` / `NOT_CONNECTED`) instead of the generic message.
- **Fixes:** discovered Star printers now route to the Star backend (were printing as raw ESC/POS);
  `PrintType.IMAGE` on an impact printer no longer prints blank; iOS EAN-13 barcode symbology;
  Epson ENPC Phase-2 model fetch hardened; `paperWidthMm` carried into Star; Star status detail read
  compile-checked; USB status no longer probes a non-printer interface.

## 0.0.10

- **iMin built-in printer now checks status before printing.** Previously it printed "blind"
  (iMin reports success even when out of paper / cover open). Added a preflight status read + the
  standard fault blocking (out-of-paper / cover-open / overheat / not-connected), plus on-demand
  `status()` support. Codes per the official iMin SDK docs; read reflectively for SDK-version safety.


## 0.0.9

- **Fix Sunmi status gaps** (verified against the reference RN package): states 5 (overheating) and
  9 (no black-mark paper) previously fell through and **printed despite the fault** — now blocked.
  State 3 (hardware abnormal) re-labelled from NOT_CONNECTED to a hardware error; 505 stays NOT_CONNECTED.


## 0.0.8

- **Read printer status on demand.** New `Printer.status()` returns a `PrinterStatus`
  (online / coverOpen / error / autoCutterError / paper / ready) for network ESC/POS printers;
  unsupported backends (USB, iMin) report `supported: false`. Sample app gains a **Check status** button.


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
