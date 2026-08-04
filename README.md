# Universal Printer Search

A standalone, **SDK-free** Android library for discovering receipt/label printers on the
local network and over USB. Extracted and cleaned from an existing React Native printer
package's native search code. It has **no proprietary vendor dependencies** — only raw sockets
and platform APIs.

## What it covers

| Path | How | Class |
|------|-----|-------|
| **Epson network** | ENPC broadcast over UDP 3289 (primary, returns MAC + model) → GS I 66/68 over TCP-9100 for serial; TCP subnet fallback if UDP is silent | `EpsonDiscovery` |
| **Sunmi cloud** | Android `NsdManager` mDNS/DNS-SD: service `_afpovertcp._tcp.`, name filter `CloudPrint_` (verified from `external-printerlibrary2:1.0.13`) | `SunmiDiscovery` |
| **Generic network** | TCP-9100 reachability sweep of the /24 subnet | `NetworkScanner` |
| **USB** | `UsbManager` enumeration, filtered by `USB_CLASS_PRINTER`; runtime permission helper | `UsbPrinterScanner`, `UsbPermissionHelper` |

> **Out of scope by design:** Star, Seiko/Sii, and Zebra discovery each require a licensed
> proprietary SDK (jar/aar) that cannot be redistributed in an open library. Wire them behind
> your own adapter + optional dependency if you need them. (Sunmi is IN — it discovers via the
> platform `NsdManager`, so no jar is bundled.)

## Modules
- `:universal-printer-search` — the library (`namespace com.universalprintersearch`).
- `:example` — a Jetpack Compose demo app with Epson / Sunmi / Network / USB / All buttons.

## Usage

```kotlin
val discovery = UniversalPrinterSearch(context)

// Individual searches (suspend, main-safe)
val epson   = discovery.discoverEpsonPrinters()    // List<DiscoveredPrinter>
val sunmi   = discovery.discoverSunmiPrinters()
val network = discovery.discoverNetworkPrinters()
val usb     = discovery.discoverUsbPrinters()      // not suspend

// Everything at once (concurrent, de-duped by IP)
val all = discovery.discoverAll()

// Single-IP helpers (manual "add printer")
val reachable = discovery.ping("192.168.1.50")
val info      = discovery.probeEpson("192.168.1.50")  // EpsonInfo(isEpson, serial)
```

`DiscoveredPrinter` carries `name`, `connectionType`, `ipAddress`, `port`, `macAddress`,
`serialNumber`, `brand`, `model`, and USB `vendorId`/`productId`.

## Permissions

The library manifest contributes `INTERNET`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`,
and `CHANGE_WIFI_MULTICAST_STATE` (the last is required to receive ENPC UDP replies).

## Build

```bash
./gradlew :universal-printer-search:assembleDebug   # build the library
./gradlew :example:assembleDebug             # build the demo app
./gradlew :example:installDebug              # install on a connected device
```

Requires JDK 17 and the Android SDK (compileSdk 34). Toolchain: AGP 8.5.2, Kotlin 2.0.20,
Gradle 8.13.

## Notes / caveats

- **"Ping" is TCP-9100, not ICMP.** Android forbids raw ICMP without root; a TCP connect to
  the raw-print port is the reachability signal.
- **MAC on Android 11+.** The OS hides ARP/MAC, so `NetworkScanner` only fills `macAddress` on
  Android ≤ 9 (`ip neigh`). Epson devices still expose a stable MAC via ENPC, and a serial via
  GS I 68 — use `serialNumber` as the unique id on modern Android.
- **Subnet detection is transport-agnostic** (`NetworkInterface`, so it works on Ethernet POS
  terminals too) — unlike the WiFi-only `WifiManager.connectionInfo` in the original code.
- The ENPC MAC byte-offset (54) is verified for the Epson TM-m30III; other TM models may differ
  and are guarded (invalid MAC → empty, discovery still returns the IP).
