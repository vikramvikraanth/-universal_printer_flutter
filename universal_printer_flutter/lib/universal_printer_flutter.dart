import 'dart:io';

import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:flutter/services.dart';

import 'src/channel.dart';
import 'src/models.dart';
import 'src/printer.dart';

export 'src/models.dart';
export 'src/printer.dart' show Printer, PrinterStatus, PaperState;

/// Flutter API for the Universal Printer SDK — printer **discovery** (Epson/Sunmi/Zebra/Star/SNMP/
/// generic-network/USB) and **printing** (ESC/POS network/USB, Star, Sunmi/iMin built-in), backed by
/// the verified Kotlin SDK over a MethodChannel.
///
/// **Android + iOS.** On iOS, network + Star printing and network discovery are supported; USB and
/// Sunmi/iMin built-in are Android-only and surface as a catchable error / empty list. Any other
/// platform throws [UnsupportedError].
class UniversalPrinterFlutter {
  UniversalPrinterFlutter._();

  static const MethodChannel _channel = MethodChannel(PrinterChannel.name);

  static void _ensureSupported() {
    if (!Platform.isAndroid && !Platform.isIOS) {
      throw UnsupportedError('universal_printer_flutter supports Android and iOS only');
    }
  }

  // ---- Discovery ----

  static Future<List<DiscoveredPrinter>> discoverEpson() => _discover('discoverEpson');
  static Future<List<DiscoveredPrinter>> discoverSunmi() => _discover('discoverSunmi');
  static Future<List<DiscoveredPrinter>> discoverSnmp() => _discover('discoverSnmp');
  static Future<List<DiscoveredPrinter>> discoverZebra() => _discover('discoverZebra');
  static Future<List<DiscoveredPrinter>> discoverStar() => _discover('discoverStar');
  static Future<List<DiscoveredPrinter>> discoverSeiko() => _discover('discoverSeiko');
  static Future<List<DiscoveredPrinter>> discoverNetwork() => _discover('discoverNetwork');
  static Future<List<DiscoveredPrinter>> discoverUsb() => _discover('discoverUsb');

  /// The host device's own **built-in** printer (Sunmi/iMin POS hardware), detected by the vendor
  /// print-service package; paper size is queried live. Empty on non-vendor devices and on iOS.
  static Future<List<DiscoveredPrinter>> discoverBuiltIn() => _discover('discoverBuiltIn');

  /// Every source (network/USB/Star/… + built-in), de-duped.
  static Future<List<DiscoveredPrinter>> discoverAll() => _discover('discoverAll');

  static Future<List<DiscoveredPrinter>> _discover(String method) async {
    _ensureSupported();
    final list = await _channel.invokeListMethod<dynamic>(method) ?? const [];
    return list.map((e) => DiscoveredPrinter.fromMap(e as Map)).toList();
  }

  /// TCP-9100 reachability check (not ICMP). True if the host accepts a raw-print connection.
  static Future<bool> ping(String ip) async {
    _ensureSupported();
    return await _channel.invokeMethod<bool>('ping', {'ip': ip}) ?? false;
  }

  // ---- Printer factories (return a native-backed [Printer] handle) ----

  /// Network/TCP ESC/POS printer (raw-print port, default 9100). Optionally carry the discovered
  /// [brand] and [paperWidthMm] (mm) so the printer object knows what it's driving. Set [isImpact] for
  /// a 9-pin dot-matrix model (Epson TM-U*) — every job is then forced text-only (no image/QR/barcode).
  static Future<Printer> networkPrinter(String host,
          {int port = 9100, PrinterBrand? brand, int? paperWidthMm, bool isImpact = false}) =>
      _create(_networkArgs(host, port: port, brand: brand, paperWidthMm: paperWidthMm, isImpact: isImpact));

  /// Sunmi Cloud Printer over LAN (alias for [networkPrinter]).
  static Future<Printer> sunmiCloudPrinter(String host, {int port = 9100}) => _create({
        PrinterChannel.argKind: PrinterChannel.kindSunmiCloud,
        PrinterChannel.argHost: host,
        PrinterChannel.argPort: port,
      });

  /// Star printer via the StarXpand SDK. [identifier] is the MAC/IP from Star discovery. Set [isImpact]
  /// for a 9-pin impact Star (SP700/SP742) — every job is then forced text-only. [paperWidthMm] (mm)
  /// re-paginates the render to the printer's real width.
  static Future<Printer> starPrinter(String identifier, {bool isImpact = false, int? paperWidthMm}) =>
      _create(_starArgs(identifier, isImpact: isImpact, paperWidthMm: paperWidthMm));

  /// Sunmi built-in printer (Sunmi hardware only).
  static Future<Printer> sunmiPrinter() => _create({PrinterChannel.argKind: PrinterChannel.kindSunmi});

  /// iMin built-in printer, v1 + v2 (iMin hardware only).
  static Future<Printer> iminPrinter() => _create({PrinterChannel.argKind: PrinterChannel.kindImin});

  /// USB ESC/POS printer, matched by [vendorId]/[productId] from [discoverUsb]. Runtime USB
  /// permission is requested natively on first print. Set [isImpact] for a 9-pin dot-matrix model
  /// (Epson TM-U* over USB) — every job is then forced text-only.
  static Future<Printer> usbPrinter({required int vendorId, required int productId, bool isImpact = false}) =>
      _create(_usbArgs(vendorId: vendorId, productId: productId, isImpact: isImpact));

  // ---- Wire arg builders (pure — shared by the factories and [createArgs]) ----

  static Map<String, Object?> _networkArgs(String host,
          {int port = 9100, PrinterBrand? brand, int? paperWidthMm, bool isImpact = false}) =>
      {
        PrinterChannel.argKind: PrinterChannel.kindNetwork,
        PrinterChannel.argHost: host,
        PrinterChannel.argPort: port,
        PrinterChannel.argBrand: brand?.wire,
        PrinterChannel.argPaperWidthMm: paperWidthMm,
        PrinterChannel.argIsImpact: isImpact,
      };

  static Map<String, Object?> _usbArgs({required int vendorId, required int productId, bool isImpact = false}) => {
        PrinterChannel.argKind: PrinterChannel.kindUsb,
        PrinterChannel.argVendorId: vendorId,
        PrinterChannel.argProductId: productId,
        PrinterChannel.argIsImpact: isImpact,
      };

  static Map<String, Object?> _starArgs(String identifier, {bool isImpact = false, int? paperWidthMm}) => {
        PrinterChannel.argKind: PrinterChannel.kindStar,
        PrinterChannel.argIdentifier: identifier,
        PrinterChannel.argIsImpact: isImpact,
        PrinterChannel.argPaperWidthMm: paperWidthMm,
      };

  /// The `createPrinter` wire args for a discovered printer. Routing: **Star brand → StarXpand
  /// backend** (must precede the others — a discovered Star reports `connectionType = network`);
  /// USB → USB backend; everything else → network ESC/POS. Pure (no channel), so it's unit-testable.
  @visibleForTesting
  static Map<String, Object?> createArgs(DiscoveredPrinter p) {
    if (p.brand == PrinterBrand.star) {
      // Star's LAN connect string is surfaced as ipAddress (fall back to the MAC/uniqueId).
      return _starArgs(
        p.ipAddress ?? p.macAddress ?? '',
        isImpact: p.isImpact,
        paperWidthMm: p.supportedPaperWidthsMm.isNotEmpty ? p.supportedPaperWidthsMm.first : null,
      );
    }
    if (p.connectionType == PrinterConnectionType.usb) {
      return _usbArgs(vendorId: p.vendorId ?? 0, productId: p.productId ?? 0, isImpact: p.isImpact);
    }
    return _networkArgs(
      p.ipAddress ?? '',
      port: p.port,
      brand: p.brand,
      paperWidthMm: p.supportedPaperWidthsMm.isNotEmpty ? p.supportedPaperWidthsMm.first : null,
      isImpact: p.isImpact,
    );
  }

  /// Build the right printer for a [DiscoveredPrinter] (Star / USB / network). For built-in Sunmi/iMin
  /// use the dedicated factories.
  static Future<Printer> printerFor(DiscoveredPrinter p) => _create(createArgs(p));

  static Future<Printer> _create(Map<String, Object?> args) async {
    _ensureSupported();
    final handle = await _channel.invokeMethod<String>(PrinterChannel.methodCreatePrinter, args);
    if (handle == null) {
      throw StateError('createPrinter returned no handle for $args');
    }
    return Printer(_channel, handle);
  }

  // ---- Preview ----

  /// Render [document] to a self-contained HTML string (same output as [PrintType.image]) for a
  /// `WebView` preview / template view.
  static Future<String> receiptHtml(PrintDocument document) async {
    _ensureSupported();
    return await _channel.invokeMethod<String>('receiptHtml', {'document': document.toJson()}) ?? '';
  }

  /// Native platform version string (diagnostic).
  static Future<String?> platformVersion() async {
    _ensureSupported();
    return _channel.invokeMethod<String>('getPlatformVersion');
  }
}
