---
name: universal-printer-search
description: Use when working on the Universal Printer Search Android SDK — discovering receipt/label printers via Epson ENPC/UDP network search, Sunmi cloud mDNS (NsdManager) search, generic TCP-9100 IP ping/subnet scan, or USB (UsbManager) enumeration. Covers the wire protocols (ENPC packet layout, ESC/POS GS I brand/serial bytes, Sunmi DNS-SD service type + name filter, USB printer-class detection), the ported constants, the known caveats (Android 11+ MAC hiding, single-socket Epson, TCP-not-ICMP ping, resolveService serialization), and the SDK-free scope boundary. Triggers on Epson discovery, ENPC, Sunmi cloud printer, mDNS/NSD/NsdManager, printer IP scan, TCP 9100, USB printer search, UsbManager, GS I, printer serial number, or any change to the discovery flows in this repo.
---

# Universal Printer Search — discovery reference

SDK-free printer discovery for Android. Three independent flows, no proprietary vendor jars.
Ported from an existing RN printer package's native code; **preserve the verified constants**
below unless you have new evidence (see `.memory/verified-facts.json`).

## ALWAYS do first
1. Read `.memory/verified-facts.json`, `.memory/decisions.json`, `.memory/progress.json`.
2. Obey `CLAUDE.md` strict rules: cite `path:line`, label VERIFIED vs ASSUMED, no green claims
   without running `./gradlew`, and remember **a build pass ≠ verified on hardware**.

## Public API
`UniversalPrinterSearch` (`universal-printer-search/src/main/java/com/universalprintersearch/UniversalPrinterSearch.kt`):
- `suspend discoverEpsonPrinters(): List<DiscoveredPrinter>`
- `suspend discoverSunmiPrinters(): List<DiscoveredPrinter>`
- `suspend discoverNetworkPrinters(): List<DiscoveredPrinter>`
- `discoverUsbPrinters(): List<DiscoveredPrinter>` (not suspend)
- `suspend discoverAll()` — runs them concurrently, de-duped by IP (branded records win)
- `suspend ping(ip)` / `suspend probeEpson(ip): EpsonTcpProbe.EpsonInfo`

## Flow 1 — Epson network (`network/epson/`)
`EpsonDiscovery` orchestrates, bounded to a 10 s window:
- **Phase 1 (primary): ENPC over UDP 3289** (`EpsonUdpDiscovery`). Only Epson devices answer, so a
  responder IS an Epson. Query = ASCII `EPSONQ` + fn byte + fixed tail.
  - `fn=0x00` reply → **MAC at byte offset 54** (6 bytes). Verified on TM-m30III; other models
    guarded (invalid MAC → empty, IP still returned).
  - `fn=0x02` reply → IEEE-1284 ID string → model from the `MDL:` token.
  - Replies start with ASCII `EPSON`. Requires a `WifiManager.MulticastLock` + the
    `CHANGE_WIFI_MULTICAST_STATE` permission.
- **Phase 2 (enrichment): `GS I` over TCP-9100** (`EpsonTcpProbe`), best-effort per UDP hit:
  - `GS I 66` = bytes `1D 49 42` → printable reply contains `EPSON` ⇒ brand match.
  - `GS I 68` = bytes `1D 49 44` → reply `_<serial>\0` → strip non-printable + leading `_`.
- **Phase 3 (fallback, UDP silent): TCP subnet sweep** + `GS I` probe on each reachable host.

**Identity rule:** the ENPC **UDP MAC is the PRIMARY** id (stable, survives Android 11+ MAC
hiding); the **GS I serial is the BACKUP** (Epson accepts ~one concurrent 9100 socket, so the
serial probe often can't connect during a search).

## Flow 2 — Sunmi cloud (`network/sunmi/SunmiDiscovery.kt`)
SDK-free via the platform **`NsdManager`** (DNS-SD / mDNS) — verified by decompiling
`external-printerlibrary2:1.0.13` (`LanHelper`):
- Discover service type **`_afpovertcp._tcp.`** with `discoverServices(type, PROTOCOL_DNS_SD, listener)`.
- **`resolveService`** each hit → host + port. Keep only resolved service names starting with
  **`CloudPrint_`** (this is how Sunmi cloud printers are distinguished from other
  `_afpovertcp._tcp` responders like Macs).
- Printer = { name = serviceName, ip = resolved host, port = resolved port (else 9100), brand = SUNMI }.
- **Serialize resolves** (one awaited `resolveService` at a time) — concurrent resolves fail with
  `FAILURE_ALREADY_ACTIVE` on API < 31.
- Caveats: `resolveService` is deprecated in API 34 (`registerServiceInfoCallback` is the 34+
  follow-up); MAC/serial are not exposed via this mDNS path. The UDP-17899 multicast some Sunmi
  docs mention is NOT used by SDK 1.0.13 (confirmed absent in the jar).

## Flow 3 — generic IP ping + scan (`network/NetworkScanner.kt`)
- **"Ping" is a TCP connect to port 9100, NOT ICMP** (Android forbids raw ICMP without root). A
  successful connect within the timeout = reachable; retried up to `maxRetries`.
- `scanSubnet()` sweeps `x.x.x.2..255` of the device /24, throttled to 10 connects per 100 ms.
- Subnet comes from `NetworkUtils.localIpv4()` — `NetworkInterface` enumeration, so it works on
  **WiFi AND Ethernet** (improvement over the source's WiFi-only `WifiManager.connectionInfo`).
- MAC is only filled on Android ≤ 9 (`ip neigh` ARP); on 11+ rely on the Epson serial/MAC path.

## Flow 4 — USB (`usb/`)
- `UsbPrinterScanner.listConnectedPrinters()` enumerates `UsbManager.deviceList`; a device is a
  printer if any interface reports `USB_CLASS_PRINTER` (0x07). Reports VID/PID/deviceName.
- `UsbPermissionHelper.ensurePermission(device)` — suspend; **RECEIVER_NOT_EXPORTED** on API 33+,
  **FLAG_IMMUTABLE** on API 31+ (else FLAG_ONE_SHOT). Enumeration does NOT prompt; call this
  before opening a connection to print.

## Scope boundary (do not cross)
Star / Seiko-Sii / Zebra discovery each need a proprietary SDK (`stario10`, `SiiAndroidSDK.jar`,
`ZSDK_ANDROID_API.jar`) that can't be redistributed. Keep them OUT of the core library; expose an
adapter interface + optional dependency if a consumer needs them. **Sunmi is IN scope** — it
discovers via the platform `NsdManager`, so no jar is bundled (see Flow 2).

## Build / verify
```bash
./gradlew :universal-printer-search:assembleDebug   # library AAR
./gradlew :example:assembleDebug                    # demo APK
./gradlew :example:installDebug                     # ON-DEVICE — the only runtime verification
```
Report build output verbatim. Runtime discovery against a real printer is the only thing that
turns the discovery logic from ASSUMED to VERIFIED.
