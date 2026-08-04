# Changelog

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
