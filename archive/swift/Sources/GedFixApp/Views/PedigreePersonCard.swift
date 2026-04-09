import SwiftUI

struct PedigreePersonCard: View {
    let person: GedcomPerson?
    let generation: Int
    let onTap: (String) -> Void
    let onDoubleTap: (String) -> Void

    private let db = DatabaseService.shared

    private var cardWidth: CGFloat {
        switch generation {
        case 0: return 220
        case 1: return 200
        case 2: return 180
        case 3: return 160
        default: return 140
        }
    }

    private var cardHeight: CGFloat {
        switch generation {
        case 0: return 80
        case 1: return 72
        case 2: return 64
        default: return 56
        }
    }

    private var nameFont: Font {
        switch generation {
        case 0: return .system(size: 14, weight: .bold)
        case 1: return .system(size: 13, weight: .semibold)
        case 2: return .system(size: 12, weight: .semibold)
        default: return .system(size: 11, weight: .medium)
        }
    }

    private var detailFont: Font {
        switch generation {
        case 0: return .system(size: 11)
        case 1: return .system(size: 10)
        default: return .system(size: 9)
        }
    }

    var body: some View {
        if let person = person {
            filledCard(person: person)
        } else {
            emptyCard
        }
    }

    // MARK: - Filled Card

    private func filledCard(person: GedcomPerson) -> some View {
        let sexColor: Color = person.sex == "M" ? .blue : person.sex == "F" ? .pink : .gray
        let birth = db.fetchBirthEvent(forXref: person.xref)
        let death = db.fetchDeathEvent(forXref: person.xref)

        return VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 6) {
                Text(person.displayName.isEmpty ? "(Unknown)" : person.displayName)
                    .font(nameFont)
                    .lineLimit(1)
                    .truncationMode(.tail)

                if person.isLiving {
                    Image(systemName: "shield.fill")
                        .font(.system(size: 8))
                        .foregroundStyle(.green)
                }
            }

            Text(dateRange(birth: birth, death: death, isLiving: person.isLiving))
                .font(detailFont)
                .foregroundStyle(.secondary)
                .lineLimit(1)

            if generation <= 1, let birthPlace = birth?.place, !birthPlace.isEmpty {
                Text(birthPlace)
                    .font(.system(size: 9))
                    .foregroundStyle(.tertiary)
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .frame(width: cardWidth, height: cardHeight, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(sexColor.opacity(0.08))
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .strokeBorder(sexColor.opacity(0.35), lineWidth: 1.5)
                )
        )
        .shadow(color: sexColor.opacity(0.12), radius: 4, x: 0, y: 2)
        .contentShape(Rectangle())
        .onTapGesture(count: 2) {
            onDoubleTap(person.xref)
        }
        .onTapGesture(count: 1) {
            onTap(person.xref)
        }
    }

    // MARK: - Empty Card

    private var emptyCard: some View {
        VStack(spacing: 4) {
            Text("?")
                .font(.system(size: generation <= 2 ? 20 : 16, weight: .light))
                .foregroundStyle(.quaternary)
            if generation <= 2 {
                Text("Unknown")
                    .font(.system(size: 9))
                    .foregroundStyle(.quaternary)
            }
        }
        .frame(width: cardWidth, height: cardHeight)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(.gray.opacity(0.04))
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(.gray.opacity(0.15), style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
                )
        )
    }

    // MARK: - Helpers

    private func dateRange(birth: GedcomEvent?, death: GedcomEvent?, isLiving: Bool) -> String {
        let birthYear = extractYear(from: birth?.dateValue ?? "")
        let deathYear = extractYear(from: death?.dateValue ?? "")

        if !birthYear.isEmpty && !deathYear.isEmpty {
            return "\(birthYear) \u{2013} \(deathYear)"
        } else if !birthYear.isEmpty && isLiving {
            return "\(birthYear) \u{2013} Living"
        } else if !birthYear.isEmpty {
            return "b. \(birthYear)"
        } else if !deathYear.isEmpty {
            return "d. \(deathYear)"
        } else if isLiving {
            return "Living"
        }
        return ""
    }

    private func extractYear(from dateString: String) -> String {
        // Extract 4-digit year from GEDCOM date strings like "12 MAR 1845" or "ABT 1900"
        let pattern = #"\b(\d{4})\b"#
        if let range = dateString.range(of: pattern, options: .regularExpression) {
            return String(dateString[range])
        }
        return ""
    }
}
