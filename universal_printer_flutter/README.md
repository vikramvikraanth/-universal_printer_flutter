# universal_printer_flutter

Flutter plugin for the **Universal Printer** SDK — discover receipt/label printers on the local
network and over USB, and print ESC/POS receipts. Wraps the verified native Kotlin SDK over a
`MethodChannel`.

- **Discovery:** Epson · Sunmi · Zebra · SNMP (Bixolon/Citizen/Brother/Seiko) · generic network (TCP-9100) · USB
- **Printing:** ESC/POS network & USB · Star (StarXpand) · Sunmi/iMin built-in
- **Two print modes:** `PrintType.text` (native ESC/POS, fast) and `PrintType.image` (HTML → bitmap, max fidelity)

| Platform | Support |
|---|---|
| **Android** | ✅ full (minSdk **26**) |
| **iOS** | ◐ network discovery + network/Star ESC/POS `text` printing; USB & Sunmi/iMin are Android-only |

---

## 1. Integrate into a fresh project

### 1a. Add the dependency

Not on pub.dev (it references vendor SDKs). Use a **git dependency** in your app's `pubspec.yaml`:

```yaml
dependencies:
  universal_printer_flutter:
    git:
      url: https://github.com/vikramvikraanth/-universal_printer_flutter.git
      path: universal_printer_flutter   # the package lives in this subfolder
      ref: v0.0.1                        # pin to a tag
```

```bash
flutter pub get
```

### 1b. Android setup (required)

The vendor deps (DantSu ESC/POS, iMin) come from **JitPack**. Add it to `android/build.gradle.kts`
(or `build.gradle`) — without it the Android build can't resolve them:

```kotlin
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }   // ← add this
    }
}
```

Set the minimum SDK in `android/app/build.gradle.kts`:

```kotlin
defaultConfig {
    minSdk = 26   // StarXpand SDK floor
}
```

> Network, Wi-Fi, USB-host and Sunmi/iMin `<queries>` permissions are declared **inside the plugin**
> and merge into your app automatically — you don't add them.

### 1c. iOS setup

Nothing extra for network printing (uses system frameworks). Star support and the `image`/preview
path are still in progress on iOS — see the support table above. Deployment target: iOS 13+.

---

## 2. Quick start

```dart
import 'package:universal_printer_flutter/universal_printer_flutter.dart';

// 1) find printers on the LAN
final printers = await UniversalPrinterFlutter.discoverNetwork();

// 2) build a receipt
final doc = PrintDocument(paper: PaperWidth.mm80)
  ..text('HELLO', align: PrintAlign.center, bold: true, size: TextSize.large)
  ..divider()
  ..row('Coffee', '3.50')
  ..feed(2);

// 3) print to the first printer found
final printer = await UniversalPrinterFlutter.networkPrinter(printers.first.ipAddress!);
final result  = await printer.printDocument(doc);          // PrintType.text by default
await printer.close();

print(result.isSuccess ? 'printed ✓' : 'failed: ${result.reason} — ${result.message}');
```

---

## 3. Discovery

Every method returns `Future<List<DiscoveredPrinter>>` (except `ping`):

```dart
await UniversalPrinterFlutter.discoverNetwork(); // TCP-9100 subnet sweep
await UniversalPrinterFlutter.discoverEpson();    // Epson (ENPC) [Android]
await UniversalPrinterFlutter.discoverSunmi();    // Sunmi (mDNS / Bonjour)
await UniversalPrinterFlutter.discoverStar();     // Star [Android]
await UniversalPrinterFlutter.discoverSnmp();     // Bixolon/Citizen/Brother/Seiko [Android]
await UniversalPrinterFlutter.discoverZebra();    // Zebra [Android]
await UniversalPrinterFlutter.discoverUsb();      // USB [Android]
await UniversalPrinterFlutter.discoverBuiltIn();  // host device's own Sunmi/iMin printer [Android]
await UniversalPrinterFlutter.discoverAll();      // everything (incl. built-in), de-duped
await UniversalPrinterFlutter.ping('192.168.0.50'); // Future<bool> — TCP-9100 reachable?
```

Each `DiscoveredPrinter` carries:

| Field | Notes |
|---|---|
| `name`, `brand`, `model` | identity |
| `connectionType` | `network` / `usb` / `builtIn` |
| `ipAddress`, `port` | network printers |
| `macAddress`, `serialNumber` | when the transport provides them |
| `vendorId`, `productId`, `usbDeviceName` | USB printers |
| `isBuiltIn` | `true` for the host device's own Sunmi/iMin printer |
| `supportedPaperWidthsMm` | e.g. `[58]` / `[80]` — queried live for built-in printers, else empty |
| `isImpact` | `true` for 9-pin dot-matrix (text-only — no image/QR) |
| `effectiveEmulation` | command language, defaults to `"ESC/POS"` when unknown |

Filter by type, e.g. `printers.where((p) => p.connectionType == PrinterConnectionType.builtIn)`.

---

## 4. Create a printer

Factories return a `Printer` handle (the native connection lives on the platform side):

```dart
await UniversalPrinterFlutter.networkPrinter('192.168.0.50', port: 9100);
await UniversalPrinterFlutter.sunmiCloudPrinter('192.168.0.51');   // Sunmi Cloud over LAN
await UniversalPrinterFlutter.starPrinter('00:11:62:...');          // Star, id from discovery [Android]
await UniversalPrinterFlutter.sunmiPrinter();                       // Sunmi built-in [Android]
await UniversalPrinterFlutter.iminPrinter();                        // iMin built-in  [Android]
await UniversalPrinterFlutter.usbPrinter(vendorId: 1208, productId: 3600); // [Android]

// or straight from a discovery result (network/USB):
final printer = await UniversalPrinterFlutter.printerFor(printers.first);
```

Always `await printer.close();` when done to release the connection.

---

## 5. Build a receipt

`PrintDocument` is a fluent builder. Every method appends one line and returns the document, so use
cascade (`..`). Order is preserved.

```dart
final doc = PrintDocument(
  paper: PaperWidth.mm80,        // 58 / 72 / 80 mm, or impact76
  cut: CutType.partial,          // none / partial / full
  openDrawer: false,             // kick the cash drawer after printing
)
  // logo (downloaded + printed as a raster)
  ..imageUrl('https://example.com/logo.png', align: PrintAlign.center)

  // styled text
  ..text('CAFE MOCHA', align: PrintAlign.center, bold: true, invert: true, size: TextSize.large)
  ..text('123 Main St', align: PrintAlign.center)
  ..divider()

  // a header row of weighted, aligned columns
  ..columns([
    const PrintColumn('Qty'),
    const PrintColumn('Item', weight: 3),
    const PrintColumn('Amount', weight: 2, align: PrintAlign.right),
  ])
  ..divider()

  // simple two-column rows (left / right)
  ..row('1  Latte', '3.50')
  ..row('2  Croissant (long names wrap automatically)', '9.00')
  ..divider()
  ..row('TOTAL', '12.50')

  // codes
  ..feed(1)
  ..barcode('123456789012', symbology: BarcodeSymbology.ean13, heightDots: 80)
  ..qr('https://example.com/receipt/42', align: PrintAlign.center)

  ..feed(3);
```

### Line types

| Method | Produces |
|---|---|
| `text(s, {align, bold, underline, invert, size})` | a styled text line |
| `columns([PrintColumn(...)])` | weighted, word-wrapped columns |
| `row(left, right, {leftWeight, rightWeight})` | shorthand for a 2-column row |
| `imageUrl(url, {align, invert, dither})` | image fetched by URL, printed as raster |
| `image(Uint8List, {align, invert, dither})` | raw image bytes (prefer `imageUrl` — lighter over the channel) |
| `barcode(data, {symbology, heightDots, align})` | 1-D barcode |
| `qr(data, {sizeDots, errorLevel, align})` | QR code |
| `feed([lines])` | blank line feed |
| `divider()` | a full-width `----` rule |
| `raw(Uint8List)` | raw ESC/POS bytes (escape hatch) |

---

## 6. Print

```dart
// native ESC/POS (fast, default)
final r1 = await printer.printDocument(doc);
// or: full-fidelity image (renders HTML → bitmap, then prints one raster) — Android
final r2 = await printer.printDocument(doc, type: PrintType.image);

switch (r1.isSuccess) {
  case true:  print('ok ${r1.warnings}');                 // e.g. [PrinterWarning.paperNearEnd]
  case false: print('${r1.reason}: ${r1.message}');       // e.g. notConnected / paperOut / coverOpen
}
```

`PrintResult` → `isSuccess`, `warnings` (`List<PrinterWarning>`), and on failure `reason`
(`PrintErrorReason`) + `message`.

### HTML preview

Get the same layout the `image` path prints, as an HTML string for a `WebView` preview:

```dart
final html = await UniversalPrinterFlutter.receiptHtml(doc);   // Android
```

---

## 7. Config reference (what crosses the channel)

When you call `printDocument`, this is the config serialized to native alongside the line list:

| Config | Type | Values |
|---|---|---|
| `paper` | `PaperWidth` | `mm58` (32 chars) · `mm72` (42) · `mm80` (48) · `impact76` (33, dot-matrix) |
| `cut` | `CutType` | `none` · `partial` · `full` |
| `openDrawer` | `bool` | kick cash drawer after print |
| `renderMode` | `RenderMode` | `auto` · `text` · `image` |
| `type` (per print call) | `PrintType` | `text` (native) · `image` (HTML→bitmap) |

Per-element enums: `PrintAlign` (`left/center/right`), `TextSize` (`normal/wide/tall/large`),
`BarcodeSymbology` (`code128/code39/ean13/upca`), `QrErrorLevel` (`l/m/q/h`).

> Enums cross the wire as their native `.name` string (e.g. `PaperWidth.mm80 → "MM_80"`). For the
> full byte-level payload shape, see `docs/wire_spec.html`.

### Impact / dot-matrix printers

9-pin impact printers can't raster. Detect and route them to text:

```dart
if (discovered.isImpact) {
  // print with PrintType.text and skip image/QR elements
  await printer.printDocument(textOnlyDoc, type: PrintType.text);
}
```

---

## 8. Troubleshooting

- **`Could not find com.github.DantSu…` / `IminPrinterLibrary`** — add the JitPack repo (step 1b).
- **`minSdkVersion` conflict** — set `minSdk = 26` (step 1b).
- **`UnsupportedError: supports Android and iOS only`** — the plugin was called on an unsupported platform.
- **USB / Sunmi / iMin on iOS** — Android-only; these return an error/empty list on iOS by design.

## Architecture

The Dart API talks to the native SDK over a single `MethodChannel`. See `docs/` for the diagrams
(channel architecture + the search/receipt wire spec).

## ⚠️ Licensing

This package **references** proprietary vendor SDKs (StarXpand, Sunmi, iMin) via Maven coordinates; it
does not bundle their binaries. Review each vendor's license before redistributing.

## Status

Android builds green and the SDK's 128 unit tests pass; the Dart↔native channel round-trip has not
yet been device-smoke-tested — treat runtime discovery/printing as **assumed** until verified on your
hardware.
