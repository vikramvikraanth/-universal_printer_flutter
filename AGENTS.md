# Agent rules — Universal Printer Search

This file is the cross-tool entry point for AI coding agents. **The authoritative rules live in
[`CLAUDE.md`](./CLAUDE.md)** — read it first, then this summary.

## Non-negotiables (see CLAUDE.md for the full text)
1. **Evidence before assertion.** Cite `path:line`. Never claim a symbol, protocol byte, or API
   exists without reading it. No memory-based claims about ENPC/ESC-POS/USB wire formats.
2. **No green claims without running the command.** Paste `./gradlew` output. A build pass proves
   compilation only — **on-device discovery is ASSUMED until run on real hardware.**
3. **Label VERIFIED vs ASSUMED** in every report.
4. **Read `.memory/*.json` before acting; update it after.** `verified-facts.json` = anchors,
   `decisions.json` = append-only rationale log, `progress.json` = current state.
5. **Scope discipline.** SDK-free flows only (Epson network, Sunmi mDNS, generic IP, USB).
   Star/Seiko/Zebra need proprietary SDKs and stay out of the core library.

## Where things are
- Library: `universal-printer-search/src/main/java/com/universalprintersearch/`
- Facade / public API: `UniversalPrinterSearch.kt`
- Discovery internals: `network/epson/`, `network/NetworkScanner.kt`, `usb/`
- Example app: `example/src/main/java/com/universalprintersearch/example/MainActivity.kt`
- Domain reference: the `universal-printer-search` skill (`.claude/skills/universal-printer-search/`)

## Build / verify
```bash
./gradlew :universal-printer-search:assembleDebug :example:assembleDebug
```
