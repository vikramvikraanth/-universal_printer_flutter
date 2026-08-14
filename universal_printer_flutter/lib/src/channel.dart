/// MethodChannel wire contract — the channel name, `createPrinter` kind values, and argument keys.
/// Single source of truth for the Dart side (and asserted by tests); keep in sync with the Kotlin
/// (`UniversalPrinterFlutterPlugin`) and Swift (`UniversalPrinterFlutterPlugin`) handlers.
abstract final class PrinterChannel {
  static const name = 'universal_printer_flutter';

  // method names
  static const methodCreatePrinter = 'createPrinter';

  // createPrinter 'kind' values
  static const kindNetwork = 'network';
  static const kindSunmiCloud = 'sunmiCloud';
  static const kindStar = 'star';
  static const kindSunmi = 'sunmi';
  static const kindImin = 'imin';
  static const kindUsb = 'usb';

  // argument keys
  static const argKind = 'kind';
  static const argHost = 'host';
  static const argPort = 'port';
  static const argIdentifier = 'identifier';
  static const argBrand = 'brand';
  static const argPaperWidthMm = 'paperWidthMm';
  static const argIsImpact = 'isImpact';
  static const argVendorId = 'vendorId';
  static const argProductId = 'productId';
}
