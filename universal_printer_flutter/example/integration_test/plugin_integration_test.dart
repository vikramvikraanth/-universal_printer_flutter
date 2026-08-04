// Basic Flutter integration test — runs against the real host plugin.
//
// https://flutter.dev/to/integration-testing

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:universal_printer_flutter/universal_printer_flutter.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('platformVersion returns a non-empty string on Android', (WidgetTester tester) async {
    final String? version = await UniversalPrinterFlutter.platformVersion();
    expect(version?.isNotEmpty, true);
  });
}
