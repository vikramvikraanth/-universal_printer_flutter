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

  /// Close the native connection and drop the handle.
  Future<void> close() => _channel.invokeMethod<void>('closePrinter', {'handle': handle});
}
