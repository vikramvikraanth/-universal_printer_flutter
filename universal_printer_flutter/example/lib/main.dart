import 'package:flutter/material.dart';
import 'package:universal_printer_flutter/universal_printer_flutter.dart';
import 'package:webview_flutter/webview_flutter.dart';

void main() => runApp(const MaterialApp(home: DiscoveryScreen()));

/// One centralized receipt (mirrors the Compose example's sampleReceipt).
PrintDocument sampleReceipt(PaperWidth paper) => PrintDocument(paper: paper)
  ..imageUrl('https://placehold.co/384x120.png', align: PrintAlign.center)
  ..text('UNIVERSAL PRINTER', align: PrintAlign.center, bold: true, invert: true, size: TextSize.large)
  ..text('Sample Receipt', align: PrintAlign.center)
  ..divider()
  ..columns([
    const PrintColumn('Qty'),
    const PrintColumn('Item', weight: 3),
    const PrintColumn('Amount', weight: 2, align: PrintAlign.right),
  ])
  ..divider()
  ..row('1  Coffee', '3.50')
  ..row('2  Sandwich (extra long name auto-wraps)', '11.00')
  ..divider()
  ..row('TOTAL', '14.50')
  ..feed(1)
  ..qr('https://example.com/r/123', align: PrintAlign.center)
  ..feed(2);

class DiscoveryScreen extends StatefulWidget {
  const DiscoveryScreen({super.key});
  @override
  State<DiscoveryScreen> createState() => _DiscoveryScreenState();
}

class _DiscoveryScreenState extends State<DiscoveryScreen> {
  bool _loading = false;
  String _status = 'Tap a button to search for printers.';
  List<DiscoveredPrinter> _printers = [];
  PaperWidth _paper = PaperWidth.mm80;
  String? _previewHtml;

  Future<void> _run(String label, Future<List<DiscoveredPrinter>> Function() block) async {
    if (_loading) return;
    setState(() {
      _loading = true;
      _status = 'Searching ($label)…';
      _printers = [];
    });
    try {
      final result = await block();
      setState(() {
        _printers = result;
        _status = '$label: ${result.length} printer(s) found';
      });
    } catch (e) {
      setState(() => _status = '$label failed: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  Future<void> _printTo(String label, PrintType type, Future<Printer> Function() factory) async {
    if (_loading) return;
    setState(() {
      _loading = true;
      _status = 'Printing ($label)…';
    });
    Printer? printer;
    try {
      printer = await factory();
      final result = await printer.printDocument(sampleReceipt(_paper), type: type);
      setState(() => _status = result.isSuccess
          ? '$label: printed ✓${result.warnings.isNotEmpty ? ' [${result.warnings.join(', ')}]' : ''}'
          : '$label: ${result.reason?.name} — ${result.message}');
    } catch (e) {
      setState(() => _status = '$label error: $e');
    } finally {
      await printer?.close();
      setState(() => _loading = false);
    }
  }

  Future<void> _preview() async {
    if (_loading) return;
    setState(() => _loading = true);
    try {
      final html = await UniversalPrinterFlutter.receiptHtml(sampleReceipt(_paper));
      setState(() => _previewHtml = html);
    } catch (e) {
      setState(() => _status = 'Preview error: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Universal Printer Search')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Epson · Sunmi · Zebra · Star · SNMP (Bixolon/Citizen/Brother/Seiko) · Network · USB',
                style: TextStyle(fontSize: 12)),
            const SizedBox(height: 12),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(children: [
                _btn('Epson', () => _run('Epson', UniversalPrinterFlutter.discoverEpson)),
                _btn('Sunmi', () => _run('Sunmi', UniversalPrinterFlutter.discoverSunmi)),
                _btn('Zebra', () => _run('Zebra', UniversalPrinterFlutter.discoverZebra)),
                _btn('Star', () => _run('Star', UniversalPrinterFlutter.discoverStar)),
                _btn('SNMP', () => _run('SNMP', UniversalPrinterFlutter.discoverSnmp)),
                _btn('Network', () => _run('Network', UniversalPrinterFlutter.discoverNetwork)),
                _btn('USB', () => _run('USB', UniversalPrinterFlutter.discoverUsb)),
              ]),
            ),
            const SizedBox(height: 8),
            Row(children: [
              const Text('Paper: '),
              for (final p in [PaperWidth.mm58, PaperWidth.mm72, PaperWidth.mm80])
                Padding(
                  padding: const EdgeInsets.only(right: 6),
                  child: ChoiceChip(
                    label: Text('${p.charsPerLine}c'),
                    selected: _paper == p,
                    onSelected: (_) => setState(() => _paper = p),
                  ),
                ),
              Text('· ${_paper.widthPx} dots', style: const TextStyle(fontSize: 12)),
            ]),
            const SizedBox(height: 8),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(children: [
                _btn('Print→Sunmi', () => _printTo('Sunmi', PrintType.text, UniversalPrinterFlutter.sunmiPrinter)),
                _btn('Net TEXT',
                    () => _printTo('Net TEXT', PrintType.text, () => UniversalPrinterFlutter.networkPrinter('192.168.80.57'))),
                _btn('Net IMAGE',
                    () => _printTo('Net IMAGE', PrintType.image, () => UniversalPrinterFlutter.networkPrinter('192.168.80.57'))),
                _btn('Preview', _preview),
              ]),
            ),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: _loading ? null : () => _run('All', UniversalPrinterFlutter.discoverAll),
              child: const Text('Discover All'),
            ),
            const SizedBox(height: 12),
            Row(children: [
              if (_loading) const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
              const SizedBox(width: 8),
              Expanded(child: Text(_status)),
            ]),
            const SizedBox(height: 8),
            if (_previewHtml != null)
              SizedBox(height: 260, child: _HtmlPreview(html: _previewHtml!)),
            Expanded(
              child: ListView(
                children: _printers.map((p) => _PrinterCard(printer: p, canPrint: !_loading, onTest: _testDiscovered)).toList(),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _testDiscovered(DiscoveredPrinter p, PrintType type) {
    // Impact printers can't raster: force TEXT (native also strips graphics + uses IMPACT_76).
    final effType = p.isImpact ? PrintType.text : type;
    final ip = p.ipAddress;
    if (ip == null) return;
    _printTo('Test $ip', effType, () => UniversalPrinterFlutter.networkPrinter(ip, port: p.port));
  }

  Widget _btn(String label, VoidCallback onTap) => Padding(
        padding: const EdgeInsets.only(right: 8),
        child: ElevatedButton(onPressed: _loading ? null : onTap, child: Text(label)),
      );
}

class _PrinterCard extends StatelessWidget {
  const _PrinterCard({required this.printer, required this.canPrint, required this.onTest});
  final DiscoveredPrinter printer;
  final bool canPrint;
  final void Function(DiscoveredPrinter, PrintType) onTest;

  @override
  Widget build(BuildContext context) {
    final mono = const TextStyle(fontFamily: 'monospace', fontSize: 12);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(printer.name, style: const TextStyle(fontWeight: FontWeight.bold)),
            Text('${printer.connectionType.name} · ${printer.brand.name}', style: const TextStyle(fontSize: 12)),
            if (printer.ipAddress != null) Text('IP: ${printer.ipAddress}:${printer.port}', style: mono),
            if (printer.macAddress != null) Text('MAC: ${printer.macAddress}', style: mono),
            if (printer.serialNumber != null) Text('Serial: ${printer.serialNumber}', style: mono),
            if (printer.model != null) Text('Model: ${printer.model}', style: mono),
            Text('Emulation: ${printer.effectiveEmulation}', style: mono),
            if (printer.isImpact)
              Text('Type: IMPACT (9-pin) · text-only', style: mono.copyWith(color: Colors.deepOrange)),
            if (printer.vendorId != null)
              Text('USB: VID ${printer.vendorId} · PID ${printer.productId}', style: mono),
            if (printer.ipAddress != null)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Row(children: [
                  ElevatedButton(
                    onPressed: canPrint ? () => onTest(printer, PrintType.text) : null,
                    child: const Text('Test TEXT'),
                  ),
                  const SizedBox(width: 8),
                  ElevatedButton(
                    onPressed: canPrint ? () => onTest(printer, PrintType.image) : null,
                    child: const Text('Test IMAGE'),
                  ),
                ]),
              ),
          ],
        ),
      ),
    );
  }
}

/// Renders the receipt HTML string (same output as PrintType.image) in a WebView.
class _HtmlPreview extends StatefulWidget {
  const _HtmlPreview({required this.html});
  final String html;
  @override
  State<_HtmlPreview> createState() => _HtmlPreviewState();
}

class _HtmlPreviewState extends State<_HtmlPreview> {
  late final WebViewController _controller = WebViewController()..loadHtmlString(widget.html);

  @override
  void didUpdateWidget(covariant _HtmlPreview oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.html != widget.html) _controller.loadHtmlString(widget.html);
  }

  @override
  Widget build(BuildContext context) => DecoratedBox(
        decoration: BoxDecoration(border: Border.all(color: Colors.grey.shade300)),
        child: WebViewWidget(controller: _controller),
      );
}
