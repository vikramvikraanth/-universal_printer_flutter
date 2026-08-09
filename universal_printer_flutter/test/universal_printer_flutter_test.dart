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
    test('parses a built-in printer with brand, type, and paper widths', () {
      final p = DiscoveredPrinter.fromMap({
        'name': 'Sunmi V2',
        'connectionType': 'BUILT_IN',
        'brand': 'SUNMI',
        'model': 'V2',
        'isBuiltIn': true,
        'supportedPaperWidthsMm': [58],
      });
      expect(p.connectionType, PrinterConnectionType.builtIn);
      expect(p.isBuiltIn, isTrue);
      expect(p.brand, PrinterBrand.sunmi);
      expect(p.supportedPaperWidthsMm, [58]);
    });

    test('generic printer reports both TEXT and IMAGE support', () {
      final p = DiscoveredPrinter.fromMap({
        'name': 'Network Printer',
        'connectionType': 'NETWORK',
        'brand': 'GENERIC',
        'supportedPrintTypes': ['TEXT', 'IMAGE'],
        'supportsImage': true,
      });
      expect(p.supportedPrintTypes, [PrintType.text, PrintType.image]);
      expect(p.supportsImage, isTrue);
    });

    test('impact printer reports TEXT only (fallback from isImpact when field absent)', () {
      final p = DiscoveredPrinter.fromMap({
        'name': 'TM-U220',
        'connectionType': 'NETWORK',
        'model': 'TM-U220',
        'isImpact': true,
      });
      expect(p.supportedPrintTypes, [PrintType.text]);
      expect(p.supportsImage, isFalse);
    });

    test('parses the iMin brand and 80mm paper', () {
      final p = DiscoveredPrinter.fromMap({
        'name': 'iMin Swift',
        'connectionType': 'BUILT_IN',
        'brand': 'IMIN',
        'supportedPaperWidthsMm': [80],
      });
      expect(p.brand, PrinterBrand.imin);
      expect(p.isBuiltIn, isTrue);
      expect(p.supportedPaperWidthsMm, [80]);
    });
  });

  group('PrintResult.fromMap', () {
    test('success carries warnings', () {
      final r = PrintResult.fromMap({'status': 'success', 'warnings': ['PAPER_NEAR_END']});
      expect(r.isSuccess, isTrue);
      expect(r.warnings, [PrinterWarning.paperNearEnd]);
    });

    test('error carries reason + back-compat message (from details)', () {
      final r = PrintResult.fromMap({'status': 'error', 'reason': 'NOT_CONNECTED', 'message': 'refused'});
      expect(r.isSuccess, isFalse);
      expect(r.reason, PrintErrorReason.notConnected);
      expect(r.message, 'refused'); // message aliases details
    });

    test('actionable error exposes friendly userMessage + technical details', () {
      final r = PrintResult.fromMap({
        'status': 'error',
        'reason': 'PAPER_OUT',
        'userMessage': 'The printer is out of paper. Load paper and try again.',
        'details': 'out of paper',
      });
      expect(r.userMessage, contains('out of paper'));
      expect(r.displayMessage, r.userMessage);
      expect(r.details, 'out of paper');
    });

    test('displayMessage falls back to generic when no userMessage', () {
      final r = PrintResult.fromMap({'status': 'error', 'reason': 'IO', 'details': 'Broken pipe'});
      expect(r.userMessage, isNull);
      expect(r.displayMessage, contains('Printing failed'));
      expect(r.details, 'Broken pipe'); // technical detail still available to the app
    });

    test('success carries friendly warning messages', () {
      final r = PrintResult.fromMap({
        'status': 'success',
        'warnings': ['PAPER_NEAR_END'],
        'warningMessages': ['Paper is running low — please replace the roll soon.'],
      });
      expect(r.warningMessages.single, contains('running low'));
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
