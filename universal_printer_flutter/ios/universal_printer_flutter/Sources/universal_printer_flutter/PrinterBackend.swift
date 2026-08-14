import Foundation

/// Common surface for a live printer connection, so the plugin can hold Network and Star backends in
/// one handle map. Mirrors the Android `Printer` interface (paperWidthMm + isImpact + print/status/close).
/// Each backend owns its own transport and encoding; the plugin only builds the device-agnostic
/// `ReceiptDocument` (applying impact text-only + paper re-pagination) and hands it over.
protocol PrinterBackend: AnyObject {
    /// Physical paper width (mm) if known from discovery — drives the render width. Nil = use the document's.
    var paperWidthMm: Int? { get }
    /// True for a 9-pin impact model — the plugin forces the document text-only before printing.
    var isImpact: Bool { get }

    /// Encode + send [doc]. Completes with a PrintErrorReason wire string on failure, or nil on success.
    func printDocument(_ doc: ReceiptDocument, completion: @escaping (_ reason: String?) -> Void)
    /// Read live hardware status as the wire status dict (see `Printer.status()` on the Dart side).
    func queryStatus(completion: @escaping (_ status: [String: Any]) -> Void)
    /// Release any persistent resources. No-op for connection-per-job backends.
    func close()
}
