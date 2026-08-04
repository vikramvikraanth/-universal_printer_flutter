import 'dart:io';

import 'package:flutter/services.dart';

import 'src/models.dart';
import 'src/printer.dart';

export 'src/models.dart';
export 'src/printer.dart' show Printer;

/// Flutter API for the Universal Printer SDK — printer **discovery** (Epson/Sunmi/Zebra/Star/SNMP/
/// generic-network/USB) and **printing** (ESC/POS network/USB, Star, Sunmi/iMin built-in), backed by
/// the verified Kotlin SDK over a MethodChannel.
///
/// **Android + iOS.** On iOS, network + Star printing and network discovery are supported; USB and
/// Sunmi/iMin built-in are Android-only and surface as a catchable error / empty list. Any other
/// platform throws [UnsupportedError].
class UniversalPrinterFlutter {
  UniversalPrinterFlutter._();

  static const MethodChannel _channel = MethodChannel('universal_printer_flutter');

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

  /// Network/TCP ESC/POS printer (raw-print port, default 9100).
  static Future<Printer> networkPrinter(String host, {int port = 9100}) =>
      _create({'kind': 'network', 'host': host, 'port': port});

  /// Sunmi Cloud Printer over LAN (alias for [networkPrinter]).
  static Future<Printer> sunmiCloudPrinter(String host, {int port = 9100}) =>
      _create({'kind': 'sunmiCloud', 'host': host, 'port': port});

  /// Star printer via the StarXpand SDK. [identifier] is the MAC/IP from Star discovery.
  static Future<Printer> starPrinter(String identifier) =>
      _create({'kind': 'star', 'identifier': identifier});

  /// Sunmi built-in printer (Sunmi hardware only).
  static Future<Printer> sunmiPrinter() => _create({'kind': 'sunmi'});

  /// iMin built-in printer, v1 + v2 (iMin hardware only).
  static Future<Printer> iminPrinter() => _create({'kind': 'imin'});

  /// USB ESC/POS printer, matched by [vendorId]/[productId] from [discoverUsb]. Runtime USB
  /// permission is requested natively on first print.
  static Future<Printer> usbPrinter({required int vendorId, required int productId}) =>
      _create({'kind': 'usb', 'vendorId': vendorId, 'productId': productId});

  /// Build the right printer for a [DiscoveredPrinter] (network/USB). For built-in Sunmi/iMin/Star
  /// use the dedicated factories.
  static Future<Printer> printerFor(DiscoveredPrinter p) {
    if (p.connectionType == PrinterConnectionType.usb) {
      return usbPrinter(vendorId: p.vendorId ?? 0, productId: p.productId ?? 0);
    }
    return networkPrinter(p.ipAddress ?? '', port: p.port);
  }

  static Future<Printer> _create(Map<String, Object?> args) async {
    _ensureSupported();
    final handle = await _channel.invokeMethod<String>('createPrinter', args);
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
