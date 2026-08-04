# iOS port — research & proposed plan (2026-07-25)

Status: **RESEARCH + PROPOSED PLAN. Decision NOT made yet** (KMP vs native Swift is pending user choice).
Stored for later. Web-verified facts are marked; architecture is a recommendation.

## Goal
Build the same `:universal-printer` SDK for iOS. "Parity" = the receipt model, rendering, and
reliability layer — NOT identical transports (iOS constraints change which backends can exist).

## iOS platform reality — VERIFIED via web search (sources at bottom)
| Backend | Android | iOS | Notes |
|---|---|---|---|
| Network ESC/POS (Epson/Seiko/Rongta/Sunmi Cloud) | yes | yes | raw TCP-9100 via Network.framework (NWConnection) |
| Star | yes (StarXpand) | yes | **StarXpand iOS/Swift SDK EXISTS** (StarIO10, SPM) — same command model |
| Epson | via generic net | yes optional | **Epson ePOS iOS SDK** (CocoaPods `epsonPrintSDK`) |
| Bluetooth / MFi | NOT built (neither platform) | yes — PRIMARY iOS transport | most iOS receipt printers are Apple-MFi certified; BLE via CoreBluetooth |
| USB ESC/POS | yes | **NO** | iOS has no general USB-host printing (MFi External Accessory only) — Android-only |
| Sunmi built-in / iMin built-in | yes (AIDL) | **NO** | Sunmi/iMin POS are **Android-only devices**; Sunmi offers only a *Cloud* iOS SDK for the standalone network printer, not a built-in device SDK |

=> **iOS backend set = Network ESC/POS + Star (StarXpand) + Bluetooth/MFi (+ optional Epson ePOS).**
Built-in (Sunmi/iMin) and USB are Android-only BY PLATFORM — document as limitations, not TODOs.

## Architecture decision (PENDING — the gate)
- **RECOMMENDED: Kotlin Multiplatform (KMP).** Move the tested pure logic to `commonMain` (compiles to a
  Kotlin/Native iOS framework), add `iosMain` actuals + a thin Swift async/await veneer. One source of
  truth, reuses the 92 unit tests on JVM + iOS, no logic drift. kotlinx.coroutines runs on Kotlin/Native
  (queue/retry survive). Cost: upfront refactor of the Android module to abstract `android.graphics.Bitmap`.
- **Alternative: native Swift rewrite.** Faster to start, but duplicates all tested logic (column layout,
  ESC/POS, preflight, i18n) => 2x maintenance + drift risk.

## KMP split
- **commonMain (already ~pure, directly shareable):** model enums + Column/PaperWidth/RenderMode/PrintType,
  ColumnLayout, Scripts, RenderPlan policy, EscPosStatus (DLE-EOT parser), Preflight mappers,
  RetryPolicy/retrying, PrintQueue/QueuedPrinter, PrintResult/errors/warnings, ReceiptHtmlRenderer
  (string assembly w/ injected encoders), Dithering.floydSteinberg (pure IntArray), + a NEW pure ESC/POS byte encoder.
- **expect/actual (platform):** image handle (Bitmap <-> UIImage/CGImage), transport (TCP + BT),
  HTML->bitmap rasterizer, offline image cache, barcode/QR generation, vendor SDK adapters.

## Android -> iOS component mapping (the actuals)
| Concern | Android today | iOS |
|---|---|---|
| Image type | android.graphics.Bitmap | UIImage / CGImage |
| ESC/POS bytes | **DantSu (Android-only)** | our NEW pure commonMain ESC/POS encoder |
| Barcode/QR | ZXing (JVM) | **CoreImage** CIQRCodeGenerator / CICode128BarcodeGenerator |
| HTML->bitmap | Android WebView + software layer | WKWebView + takeSnapshot / drawViewHierarchyInRect |
| Offline URL cache | Glide | Kingfisher or SDWebImage |
| TCP transport | DantSu TcpConnection | Network.framework NWConnection |
| Star | StarXpand Android | StarXpand iOS (same builder API) |
| Built-in (Sunmi/iMin), USB | AIDL / UsbManager | N/A on iOS |

## BIGGEST new work item
Our Android ESC/POS path **depends on DantSu (Android-only)**. iOS has no DantSu, so we must write our own
**ESC/POS byte encoder** (align/bold/size/invert, image raster GS v 0, ESC t codepage, barcode/QR, cut,
DLE EOT). Put it in **commonMain** so BOTH platforms share it (and Android can retire DantSu later).
We already reverse-engineered these commands this session (GS v 0, ESC t table from Rongta, DLE EOT masks) — spec is known.

## Phasing
1. Confirm KMP vs Swift.
2. Extract pure core -> commonMain (abstract Bitmap; keep Android tests green).
3. Pure ESC/POS byte encoder in commonMain (+ tests); migrate Android off DantSu incrementally.
4. iosMain actuals: image, NWConnection TCP, CoreImage codes, WKWebView rasterizer, Kingfisher cache.
5. iOS backends: Network ESC/POS, Star (StarXpand iOS), Bluetooth/MFi (External Accessory).
6. Swift veneer + iOS example (paper-size picker, WKWebView preview, TEXT/IMAGE) + on-device verify (Star + LAN Epson + MFi BT).

## Verification target
- The 92 pure unit tests move to commonMain, run on JVM + Kotlin/Native.
- New: ESC/POS byte-encoder tests, CoreImage code-gen tests, Swift smoke tests.
- On device: iOS example prints TEXT + IMAGE to Star + LAN Epson + an MFi BT printer; HTML preview renders.

## Open questions for later
- KMP vs native Swift (the gate).
- Include Bluetooth/MFi from the start? (main mobile transport on iOS; neither platform has BT yet).
- StarXpand iOS + Epson ePOS iOS licensing/packaging (SPM/CocoaPods).
- Effort: substantial (weeks). Biggest = KMP refactor + pure ESC/POS encoder.

## Sources (web-verified 2026-07-25)
- StarXpand iOS SDK: https://github.com/star-micronics/StarXpand-SDK-iOS (SPM; iOS/Android/Windows cross-platform)
- Epson ePOS iOS SDK: https://cocoapods.org/pods/epsonPrintSDK
- Star MFi Bluetooth (iOS): https://starmicronics.com/bluetooth-receipt-printers-pos-thermal-impact-portable/
- Sunmi Cloud Printer SDK for iOS (built-ins Android-only): https://developer.sunmi.com/docs/en-US/cdixeghjk491/xfmceghjk502
- Bixolon iOS UPOS SDK (iOS 15+), POS-X iOS SDK — other vendor iOS SDKs exist.
