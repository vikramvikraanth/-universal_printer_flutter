import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:universal_printer_flutter/universal_printer_flutter.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('DiscoveredPrinter.fromMap', () {
    test('parses enums and computed fields, defaults emulation to ESC/POS', () {
      final p = DiscoveredPrinter.fromMap({
        'name': 'TM-U220II',
        'connectionType': 'NETWORK',
        'ipAddress': '192.168.0.50',
        'port': 9100,
        'brand': 'EPSON',
        'model': 'TM-U220II',
        'emulation': null,
        'isImpact': true,
        'effectiveEmulation': 'ESC/POS',
      });
      expect(p.connectionType, PrinterConnectionType.network);
      expect(p.brand, PrinterBrand.epson);
      expect(p.isImpact, isTrue);
      expect(p.effectiveEmulation, 'ESC/POS');
    });

    test('unknown enum strings fall back safely', () {
      final p = DiscoveredPrinter.fromMap({'name': 'x', 'connectionType': 'WAT', 'brand': 'NOPE'});
      expect(p.connectionType, PrinterConnectionType.network);
      expect(p.brand, PrinterBrand.unknown);
    });
  });

  group('PrintResult.fromMap', () {
    test('success carries warnings', () {
      final r = PrintResult.fromMap({'status': 'success', 'warnings': ['PAPER_NEAR_END']});
      expect(r.isSuccess, isTrue);
      expect(r.warnings, [PrinterWarning.paperNearEnd]);
    });

    test('error carries reason + message', () {
      final r = PrintResult.fromMap({'status': 'error', 'reason': 'NOT_CONNECTED', 'message': 'refused'});
      expect(r.isSuccess, isFalse);
      expect(r.reason, PrintErrorReason.notConnected);
      expect(r.message, 'refused');
    });
  });

  group('PrintDocument.toJson', () {
    test('serializes elements with Kotlin-name wire enums', () {
      final json = (PrintDocument(paper: PaperWidth.impact76, cut: CutType.full)
            ..text('HI', align: PrintAlign.center, invert: true, size: TextSize.large)
            ..columns([const PrintColumn('Qty'), const PrintColumn('Amt', weight: 2, align: PrintAlign.right)])
            ..row('Coffee', '3.50')
            ..imageUrl('https://x/y.png')
            ..image(Uint8List.fromList([1, 2, 3]))
            ..barcode('123', symbology: BarcodeSymbology.ean13)
            ..qr('u', errorLevel: QrErrorLevel.h)
            ..feed(2)
            ..divider())
          .toJson();

      expect(json['paper'], 'IMPACT_76');
      expect(json['cut'], 'FULL');
      final elements = json['elements'] as List;
      expect(elements.first, containsPair('type', 'text'));
      expect(elements.first, containsPair('align', 'CENTER'));
      expect(elements.first, containsPair('size', 'LARGE'));
      expect(
        elements.map((e) => (e as Map)['type']),
        containsAll(<String>['text', 'columns', 'row', 'imageUrl', 'image', 'barcode', 'qr', 'feed', 'divider']),
      );
      // Image bytes survive as a typed list for the standard codec.
      final img = elements.firstWhere((e) => (e as Map)['type'] == 'image') as Map;
      expect(img['bytes'], isA<Uint8List>());
    });
  });

  test('PaperWidth display fields mirror the Kotlin profiles', () {
    expect(PaperWidth.mm80.charsPerLine, 48);
    expect(PaperWidth.impact76.wire, 'IMPACT_76');
    expect(PaperWidth.impact76.charsPerLine, 33);
  });
}
