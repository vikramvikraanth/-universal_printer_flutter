# universal_printer_flutter

Flutter plugin for the **Universal Printer** SDK: discover receipt/label printers on the local network
and over USB, and print ESC/POS receipts. Wraps the verified Kotlin SDK over a `MethodChannel`.

- **Discovery:** Epson · Sunmi · Zebra · SNMP (Bixolon/Citizen/Brother/Seiko) · generic network (TCP-9100) · USB
- **Printing:** ESC/POS network & USB · Star (StarXpand) · Sunmi/iMin built-in
- **Two print modes:** `PrintType.text` (native ESC/POS, fast) and `PrintType.image` (HTML → WebView → bitmap, max fidelity)

## Platform support

| Platform | Status |
|---|---|
| Android | ✅ (minSdk **26**) |
| iOS | ⛔ no-op stub — every call throws a catchable `UnsupportedError` (no crash) |

## Install

Not published to pub.dev (it references vendor SDKs — see below). Use a **git dependency**:

```yaml
dependencies:
  universal_printer_flutter:
    git:
      url: https://github.com/<you>/<repo>.git
      path: universal_printer_flutter   # this package lives in a subfolder
      ref: main
```

Then `flutter pub get`.

### Android setup (required)

Vendor deps (DantSu ESC/POS, iMin) resolve from **JitPack**, so add it to your app's
`android/build.gradle(.kts)` — otherwise the Android build fails to resolve them:

```kotlin
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Set `minSdk` to at least **26** (StarXpand floor) in `android/app/build.gradle.kts`.

## Usage

### Discover printers

```dart
import 'package:universal_printer_flutter/universal_printer_flutter.dart';

final printers = await UniversalPrinterFlutter.discoverNetwork();
for (final p in printers) {
  print('${p.name} ${p.ipAddress} · ${p.effectiveEmulation} · impact=${p.isImpact}');
}
```

Other methods: `discoverEpson`, `discoverSunmi`, `discoverZebra`, `discoverStar`, `discoverSnmp`,
`discoverSeiko`, `discoverUsb`, `discoverAll`, and `ping(ip)`.

### Build and print a receipt

```dart
final doc = PrintDocument(paper: PaperWidth.mm80, cut: CutType.partial)
  ..text('CAFE MOCHA', align: PrintAlign.center, bold: true, size: TextSize.large)
  ..divider()
  ..row('1  Latte', '3.50')
  ..row('TOTAL',    '3.50')
  ..qr('https://pay/abc', align: PrintAlign.center)
  ..feed(2);

final printer = await UniversalPrinterFlutter.networkPrinter('192.168.0.50');
final result  = await printer.printDocument(doc, type: PrintType.text);
await printer.close();

if (result.isSuccess) {
  print('printed ✓ ${result.warnings}');
} else {
  print('failed: ${result.reason} — ${result.message}');
}
```

Printer factories: `networkPrinter`, `usbPrinter`, `starPrinter`, `sunmiPrinter`, `iminPrinter`,
`sunmiCloudPrinter`, and `printerFor(discoveredPrinter)`. Preview HTML with
`UniversalPrinterFlutter.receiptHtml(doc)`.

Impact (dot-matrix) printers are text-only: check `discoveredPrinter.isImpact` and print with
`PrintType.text`.

## Architecture

The Dart API talks to the native SDK over one `MethodChannel`. See `docs/` for the wire diagrams
(channel architecture + the search/receipt payload spec).

## ⚠️ Licensing note

This package **references** proprietary vendor SDKs (StarXpand, Sunmi, iMin) via Maven coordinates; it
does not bundle their binaries. Review each vendor's license before redistributing publicly.

## Status

Android builds green and the SDK's 128 unit tests pass, but the Dart↔native channel round-trip has not
yet been validated on a device — treat runtime discovery/printing as **assumed** until smoke-tested.
