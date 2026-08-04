// Basic widget smoke test — the discovery screen renders without touching the platform.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:universal_printer_flutter_example/main.dart';

void main() {
  testWidgets('DiscoveryScreen renders its title and discover buttons', (WidgetTester tester) async {
    await tester.pumpWidget(const MaterialApp(home: DiscoveryScreen()));

    expect(find.text('Universal Printer Search'), findsOneWidget);
    expect(find.text('Discover All'), findsOneWidget);
    expect(find.text('Epson'), findsOneWidget);
  });
}
