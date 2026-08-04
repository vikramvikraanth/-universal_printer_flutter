import 'dart:typed_data';

/// Enum values cross the MethodChannel as their Kotlin `.name` string (uppercased). Each Dart enum
/// therefore carries its [wire] value so the contract stays explicit and drift-proof.

enum PrinterConnectionType {
  network('NETWORK'),
  usb('USB');

  const PrinterConnectionType(this.wire);
  final String wire;
  static PrinterConnectionType fromWire(String? w) =>
      values.firstWhere((e) => e.wire == w, orElse: () => PrinterConnectionType.network);
}

enum PrinterBrand {
  epson('EPSON'),
  sunmi('SUNMI'),
  seiko('SEIKO'),
  star('STAR'),
  zebra('ZEBRA'),
  bixolon('BIXOLON'),
  citizen('CITIZEN'),
  brother('BROTHER'),
  generic('GENERIC'),
  unknown('UNKNOWN');

  const PrinterBrand(this.wire);
  final String wire;
  static PrinterBrand fromWire(String? w) =>
      values.firstWhere((e) => e.wire == w, orElse: () => PrinterBrand.unknown);
}

enum PrintAlign {
  left('LEFT'),
  center('CENTER'),
  right('RIGHT');

  const PrintAlign(this.wire);
  final String wire;
}

enum TextSize {
  normal('NORMAL'),
  wide('WIDE'),
  tall('TALL'),
  large('LARGE');

  const TextSize(this.wire);
  final String wire;
}

enum CutType {
  none('NONE'),
  partial('PARTIAL'),
  full('FULL');

  const CutType(this.wire);
  final String wire;
}

enum RenderMode {
  auto('AUTO'),
  text('TEXT'),
  image('IMAGE');

  const RenderMode(this.wire);
  final String wire;
}

enum BarcodeSymbology {
  code128('CODE128'),
  code39('CODE39'),
  ean13('EAN13'),
  upca('UPCA');

  const BarcodeSymbology(this.wire);
  final String wire;
}

enum QrErrorLevel {
  l('L'),
  m('M'),
  q('Q'),
  h('H');

  const QrErrorLevel(this.wire);
  final String wire;
}

/// How the receipt is put on paper by the common print entry (mirrors Kotlin `PrintType`).
enum PrintType {
  /// Native ESC/POS / vendor commands (fast).
  text('TEXT'),

  /// Render to HTML, rasterize to one bitmap, print that (max fidelity, slower).
  image('IMAGE');

  const PrintType(this.wire);
  final String wire;
}

/// Paper width profiles (mirror Kotlin `PaperWidth`; [widthPx]/[charsPerLine] duplicated for UI display).
enum PaperWidth {
  mm58('MM_58', 384, 32),
  mm72('MM_72', 576, 42),
  mm80('MM_80', 576, 48),

  /// 3-inch impact / dot-matrix (Epson TM-U220 class). Text-only.
  impact76('IMPACT_76', 200, 33);

  const PaperWidth(this.wire, this.widthPx, this.charsPerLine);
  final String wire;
  final int widthPx;
  final int charsPerLine;
}

enum PrintErrorReason {
  timeout('TIMEOUT'),
  notConnected('NOT_CONNECTED'),
  paperOut('PAPER_OUT'),
  coverOpen('COVER_OPEN'),
  cutterError('CUTTER_ERROR'),
  permissionDenied('PERMISSION_DENIED'),
  contentInvalid('CONTENT_INVALID'),
  unsupported('UNSUPPORTED'),
  io('IO'),
  unknown('UNKNOWN');

  const PrintErrorReason(this.wire);
  final String wire;
  static PrintErrorReason fromWire(String? w) =>
      values.firstWhere((e) => e.wire == w, orElse: () => PrintErrorReason.unknown);
}

enum PrinterWarning {
  paperNearEnd('PAPER_NEAR_END');

  const PrinterWarning(this.wire);
  final String wire;
  static PrinterWarning fromWire(String? w) =>
      values.firstWhere((e) => e.wire == w, orElse: () => PrinterWarning.paperNearEnd);
}

/// A printer surfaced by discovery. Mirrors Kotlin `DiscoveredPrinter` incl. computed
/// [isImpact] / [effectiveEmulation].
class DiscoveredPrinter {
  const DiscoveredPrinter({
    required this.name,
    required this.connectionType,
    this.ipAddress,
    this.port = 9100,
    this.macAddress,
    this.serialNumber,
    this.brand = PrinterBrand.unknown,
    this.model,
    this.emulation,
    this.vendorId,
    this.productId,
    this.usbDeviceName,
    this.isImpact = false,
    this.effectiveEmulation = 'ESC/POS',
  });

  final String name;
  final PrinterConnectionType connectionType;
  final String? ipAddress;
  final int port;
  final String? macAddress;
  final String? serialNumber;
  final PrinterBrand brand;
  final String? model;
  final String? emulation;
  final int? vendorId;
  final int? productId;
  final String? usbDeviceName;

  /// 9-pin impact / dot-matrix (text-only — no image/QR).
  final bool isImpact;

  /// [emulation] if known, else the ESC/POS default.
  final String effectiveEmulation;

  factory DiscoveredPrinter.fromMap(Map<dynamic, dynamic> m) => DiscoveredPrinter(
        name: m['name'] as String? ?? 'Printer',
        connectionType: PrinterConnectionType.fromWire(m['connectionType'] as String?),
        ipAddress: m['ipAddress'] as String?,
        port: (m['port'] as num?)?.toInt() ?? 9100,
        macAddress: m['macAddress'] as String?,
        serialNumber: m['serialNumber'] as String?,
        brand: PrinterBrand.fromWire(m['brand'] as String?),
        model: m['model'] as String?,
        emulation: m['emulation'] as String?,
        vendorId: (m['vendorId'] as num?)?.toInt(),
        productId: (m['productId'] as num?)?.toInt(),
        usbDeviceName: m['usbDeviceName'] as String?,
        isImpact: m['isImpact'] as bool? ?? false,
        effectiveEmulation: m['effectiveEmulation'] as String? ?? 'ESC/POS',
      );

  @override
  String toString() => 'DiscoveredPrinter($name, $connectionType, ip=$ipAddress, brand=$brand, '
      'model=$model, impact=$isImpact, emu=$effectiveEmulation)';
}

/// Outcome of a print job (mirrors Kotlin `PrintResult`).
class PrintResult {
  const PrintResult.success({this.warnings = const []})
      : isSuccess = true,
        reason = null,
        message = null;

  const PrintResult.error({required this.reason, this.message})
      : isSuccess = false,
        warnings = const [];

  final bool isSuccess;
  final List<PrinterWarning> warnings;
  final PrintErrorReason? reason;
  final String? message;

  factory PrintResult.fromMap(Map<dynamic, dynamic> m) {
    if ((m['status'] as String?) == 'success') {
      final w = (m['warnings'] as List?)?.map((e) => PrinterWarning.fromWire(e as String?)).toList() ?? const [];
      return PrintResult.success(warnings: w);
    }
    return PrintResult.error(
      reason: PrintErrorReason.fromWire(m['reason'] as String?),
      message: m['message'] as String?,
    );
  }

  @override
  String toString() =>
      isSuccess ? 'Success(warnings=$warnings)' : 'Error($reason: $message)';
}

/// A weighted, aligned cell in a multi-column row.
class PrintColumn {
  const PrintColumn(this.text, {this.weight = 1, this.align = PrintAlign.left});
  final String text;
  final int weight;
  final PrintAlign align;

  Map<String, Object?> toJson() => {'text': text, 'weight': weight, 'align': align.wire};
}

/// A receipt to print. Build it with the fluent methods, then hand it to a [Printer] or
/// `UniversalPrinterFlutter.receiptHtml`. Serializes to the MethodChannel contract via [toJson].
class PrintDocument {
  PrintDocument({
    this.paper = PaperWidth.mm80,
    this.cut = CutType.partial,
    this.openDrawer = false,
    this.renderMode = RenderMode.auto,
  });

  final PaperWidth paper;
  final CutType cut;
  final bool openDrawer;
  final RenderMode renderMode;
  final List<Map<String, Object?>> _elements = [];

  PrintDocument text(
    String text, {
    PrintAlign align = PrintAlign.left,
    bool bold = false,
    bool underline = false,
    bool invert = false,
    TextSize size = TextSize.normal,
  }) {
    _elements.add({
      'type': 'text',
      'text': text,
      'align': align.wire,
      'bold': bold,
      'underline': underline,
      'invert': invert,
      'size': size.wire,
    });
    return this;
  }

  PrintDocument columns(List<PrintColumn> cells) {
    _elements.add({'type': 'columns', 'cells': cells.map((c) => c.toJson()).toList()});
    return this;
  }

  PrintDocument row(String left, String right, {int leftWeight = 1, int rightWeight = 1}) {
    _elements.add({
      'type': 'row',
      'left': left,
      'right': right,
      'leftWeight': leftWeight,
      'rightWeight': rightWeight,
    });
    return this;
  }

  /// Image referenced by URL — downloaded + cached offline (Glide) natively before printing.
  PrintDocument imageUrl(String url,
      {PrintAlign align = PrintAlign.center, bool invert = false, bool dither = false}) {
    _elements.add({'type': 'imageUrl', 'url': url, 'align': align.wire, 'invert': invert, 'dither': dither});
    return this;
  }

  /// Raw image bytes (PNG/JPEG). Heavier over the channel than [imageUrl] — prefer pre-scaled bitmaps.
  PrintDocument image(Uint8List bytes,
      {PrintAlign align = PrintAlign.center, bool invert = false, bool dither = false}) {
    _elements.add({'type': 'image', 'bytes': bytes, 'align': align.wire, 'invert': invert, 'dither': dither});
    return this;
  }

  PrintDocument barcode(String data,
      {BarcodeSymbology symbology = BarcodeSymbology.code128,
      int heightDots = 100,
      PrintAlign align = PrintAlign.center}) {
    _elements.add({
      'type': 'barcode',
      'data': data,
      'symbology': symbology.wire,
      'heightDots': heightDots,
      'align': align.wire,
    });
    return this;
  }

  PrintDocument qr(String data,
      {int sizeDots = 200, QrErrorLevel errorLevel = QrErrorLevel.m, PrintAlign align = PrintAlign.center}) {
    _elements.add({
      'type': 'qr',
      'data': data,
      'sizeDots': sizeDots,
      'errorLevel': errorLevel.wire,
      'align': align.wire,
    });
    return this;
  }

  PrintDocument feed([int lines = 1]) {
    _elements.add({'type': 'feed', 'lines': lines});
    return this;
  }

  PrintDocument divider() {
    _elements.add({'type': 'divider'});
    return this;
  }

  PrintDocument raw(Uint8List bytes) {
    _elements.add({'type': 'raw', 'bytes': bytes});
    return this;
  }

  Map<String, Object?> toJson() => {
        'paper': paper.wire,
        'cut': cut.wire,
        'openDrawer': openDrawer,
        'renderMode': renderMode.wire,
        'elements': _elements,
      };
}
