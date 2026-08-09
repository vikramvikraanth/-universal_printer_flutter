import Foundation

// User-facing print messages — the Swift mirror of Kotlin `PrinterMessages`. Actionable faults get a
// specific instruction; internal/technical failures get the generic message (the app still logs the
// technical `details`). Keyed by the PrintErrorReason / PrinterWarning wire strings.
enum PrinterMessages {

    static let generic = "Printing failed. Please try again. If the problem continues, contact support."

    static func userMessage(_ reason: String) -> String {
        switch reason {
        case "PAPER_OUT": return "The printer is out of paper. Load paper and try again."
        case "COVER_OPEN": return "The printer cover is open. Close it and try again."
        case "CUTTER_ERROR": return "The paper cutter is jammed. Clear the jam and try again."
        case "NOT_CONNECTED": return "Can't reach the printer. Check it's powered on and connected."
        case "TIMEOUT": return "The printer isn't responding. Please try again."
        case "PERMISSION_DENIED": return "Permission to use the printer was denied. Grant access and try again."
        // CONTENT_INVALID / UNSUPPORTED / IO / UNKNOWN → generic (details still sent).
        default: return generic
        }
    }

    static func warningMessage(_ warning: String) -> String {
        switch warning {
        case "PAPER_NEAR_END": return "Paper is running low — please replace the roll soon."
        default: return warning
        }
    }
}
