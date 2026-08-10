import 'package:flutter/services.dart';

import 'models.dart';

/// A handle to a native [Printer] created via `UniversalPrinterFlutter`. The stateful printer
/// (connection + FIFO print queue) lives on the Android side; this Dart object references it by
/// [handle]. Always [close] it when done to release the native connection.
class Printer {
  Printer(this._channel, this.handle);

  final MethodChannel _channel;
  final String handle;

  /// Enqueue and print [document]. Suspends until the job finishes; same-printer jobs run FIFO.
  Future<PrintResult> printDocument(PrintDocument document, {PrintType type = PrintType.text}) async {
    final res = await _channel.invokeMapMethod<dynamic, dynamic>('printDocument', {
      'handle': handle,
      'document': document.toJson(),
      'type': type.wire,
    });
    return PrintResult.fromMap(
      res ?? const {'status': 'error', 'reason': 'UNKNOWN', 'message': 'null result from channel'},
    );
  }

  /// Read the printer's live hardware status (paper / cover / cutter / online). Works for network
  /// ESC/POS (and Sunmi/Star); returns [PrinterStatus.unsupported] for backends that can't report it
  /// (USB, iMin). Requires an idle printer — ESC/POS status uses a short-lived connection.
  Future<PrinterStatus> status() async {
    final m = await _channel.invokeMapMethod<dynamic, dynamic>('getStatus', {'handle': handle});
    return PrinterStatus.fromMap(m ?? const {'supported': false});
  }

  /// Close the native connection and drop the handle.
  Future<void> close() => _channel.invokeMethod<void>('closePrinter', {'handle': handle});
}

/// Roll-paper state from a live status query.
enum PaperState { ok, nearEnd, notPresent, unknown }

/// A live snapshot of a printer's hardware state (from an ESC/POS `DLE EOT` query or vendor SDK).
class PrinterStatus {
  const PrinterStatus({
    required this.supported,
    required this.answered,
    this.online = false,
    this.coverOpen = false,
    this.error = false,
    this.autoCutterError = false,
    this.paper = PaperState.unknown,
    this.ready = false,
  });

  /// This backend can report status at all (false for USB / iMin).
  final bool supported;

  /// The printer actually answered the query (false = unreachable / didn't respond).
  final bool answered;

  final bool online;
  final bool coverOpen;
  final bool error;
  final bool autoCutterError;
  final PaperState paper;

  /// Ready to print right now (online, no fault, paper present).
  final bool ready;

  factory PrinterStatus.unsupported() => const PrinterStatus(supported: false, answered: false);

  factory PrinterStatus.fromMap(Map<dynamic, dynamic> m) {
    final supported = m['supported'] as bool? ?? false;
    final answered = m['answered'] as bool? ?? false;
    if (!supported || !answered) {
      return PrinterStatus(supported: supported, answered: answered);
    }
    PaperState paper;
    switch ((m['paper'] as String?)?.toUpperCase()) {
      case 'OK':
        paper = PaperState.ok;
      case 'NEAR_END':
        paper = PaperState.nearEnd;
      case 'NOT_PRESENT':
        paper = PaperState.notPresent;
      default:
        paper = PaperState.unknown;
    }
    return PrinterStatus(
      supported: true,
      answered: true,
      online: m['online'] as bool? ?? false,
      coverOpen: m['coverOpen'] as bool? ?? false,
      error: m['error'] as bool? ?? false,
      autoCutterError: m['autoCutterError'] as bool? ?? false,
      paper: paper,
      ready: m['ready'] as bool? ?? false,
    );
  }

  @override
  String toString() => !supported
      ? 'PrinterStatus(unsupported)'
      : !answered
          ? 'PrinterStatus(no response)'
          : 'PrinterStatus(ready=$ready, online=$online, cover=$coverOpen, cutter=$autoCutterError, paper=$paper)';
}
