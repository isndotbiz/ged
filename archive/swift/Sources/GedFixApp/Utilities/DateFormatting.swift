import Foundation

enum GedcomDateFormatter {
    /// Extract a year from a GEDCOM date string for display
    static func extractYear(_ dateString: String) -> String? {
        let pattern = /(\d{4})/
        guard let match = dateString.firstMatch(of: pattern) else { return nil }
        return String(match.1)
    }

    /// Format a GEDCOM date for display (human-readable)
    static func displayDate(_ raw: String) -> String {
        if raw.isEmpty { return "" }

        // Already readable enough for most cases
        // Just capitalize first letter and lowercase the rest of month abbreviations
        let parts = raw.split(separator: " ")
        let formatted: [String] = parts.map { part in
            let s = String(part)
            if ["JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"].contains(s) {
                return String(s.prefix(1)) + s.dropFirst().lowercased()
            }
            if ["ABT","BEF","AFT","BET","EST","CAL"].contains(s) {
                switch s {
                case "ABT": return "About"
                case "BEF": return "Before"
                case "AFT": return "After"
                case "BET": return "Between"
                case "EST": return "Estimated"
                case "CAL": return "Calculated"
                default: return s
                }
            }
            return s
        }
        return formatted.joined(separator: " ")
    }
}
