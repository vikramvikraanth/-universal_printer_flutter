# Claude Context — Universal Printer Search

A standalone, **SDK-free** Android (Kotlin) library that discovers receipt/label printers on the
local network and over USB. No proprietary vendor SDKs — only raw sockets + platform APIs.
**Source of truth for project state lives in `.memory/*.json` — read it before doing anything**
(a SessionStart hook prints it for you).

---

## STRICT RULES — anti-hallucination (NON-NEGOTIABLE)

These override default behavior. A fabricated claim here means a broken native build or a printer
that silently never gets found on hardware. The bar is **evidence before assertion, always.**

1. **Never claim a class, function, constant, or protocol byte exists without reading it.** Cite
   `path:line`. If you haven't opened it this session, open it before referencing it.
2. **Never invent protocol/wire behavior.** ENPC packet layout, ESC/POS `GS I` command bytes, the
   ENPC MAC offset, `USB_CLASS_PRINTER`, Android permission-flag requirements — confirm each by
   (a) reading the code/comment in this repo, or (b) a web search with the URL cited. No
   memory-based assertions about byte offsets or vendor wire formats.
3. **Never say "done", "works", "builds", or "passing" without running the command and pasting its
   output.** `./gradlew ...` output, not inspection. On-device discovery is NEVER "verified" from a
   build alone — it is ASSUMED until run against real hardware.
4. **Separate VERIFIED from ASSUMED in every report.** Use those words. A build-green claim covers
   compilation only, not runtime discovery.
5. **This library is a clean-room extraction, not a rewrite.** The three flows were ported from an
   existing RN printer package's native code. When changing discovery logic, preserve the verified
   constants (see `.memory/verified-facts.json`) unless you have new evidence.
6. **Star / Seiko-Sii / Zebra are OUT of scope.** They require proprietary jars/aars that cannot be
   redistributed. Do not add them to the core library; if a consumer needs them, they go behind an
   adapter interface + optional dependency. (Sunmi IS in scope — it discovers via the platform
   `NsdManager`/mDNS, no jar; see decision D5.)
7. **Record every non-obvious decision** in `.memory/decisions.json` (append, with rationale +
   evidence). Update `.memory/progress.json` as work lands. Add newly-verified facts to
   `.memory/verified-facts.json` — but only after verifying them.

---

## Memory protocol (every session)
- **On start:** read `.memory/verified-facts.json` (anchors), `.memory/decisions.json` (what's
  locked), `.memory/progress.json` (where we are). Do not re-derive these.
- **During work:** append decisions; update progress; add verified facts. Treat `.memory/` as the
  durable brain — chat context is ephemeral.

## What this repo is
| Module | Namespace | Purpose |
|--------|-----------|---------|
| `:universal-printer-search` | `com.universalprintersearch` | The library (AAR). |
| `:example` | `com.universalprintersearch.example` | Jetpack Compose demo app. |

Public API = the `UniversalPrinterSearch` facade
(`universal-printer-search/src/main/java/com/universalprintersearch/UniversalPrinterSearch.kt`):
`discoverEpsonPrinters()`, `discoverNetworkPrinters()`, `discoverUsbPrinters()`, `discoverAll()`,
`ping(ip)`, `probeEpson(ip)`.

### The discovery flows (see the `universal-printer-search` skill for detail)
- **Epson network** — `network/epson/*`: ENPC UDP/3289 broadcast (primary) + `GS I` TCP-9100 probe.
- **Sunmi cloud** — `network/sunmi/SunmiDiscovery.kt`: `NsdManager` mDNS (`_afpovertcp._tcp.`, name `CloudPrint_`).
- **Generic IP** — `network/NetworkScanner.kt`: TCP-9100 reachability + /24 subnet sweep.
- **USB** — `usb/*`: `UsbManager` enumeration filtered by `USB_CLASS_PRINTER` + permission helper.

## Commands (verify before trusting)
- Build lib: `./gradlew :universal-printer-search:assembleDebug`
- Build example: `./gradlew :example:assembleDebug`
- Install demo: `./gradlew :example:installDebug` (connected device — the only way to verify runtime)
- Requires JDK 17 + Android SDK (compileSdk 34). Toolchain: AGP 8.5.2, Kotlin 2.0.20, Gradle 8.13.

## Final deliverable
A publishable, buildable AAR + a runnable example app, with the discovery logic faithful to the
verified constants in `.memory/verified-facts.json`.
