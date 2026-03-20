import Foundation

/// Bridge to the gedfix Python CLI for batch operations
enum CLIBridge {

    /// Find the gedfix executable on PATH
    static func findGedfix() -> String? {
        let process = Process()
        process.executableURL = URL(filePath: "/usr/bin/which")
        process.arguments = ["gedfix"]
        let pipe = Pipe()
        process.standardOutput = pipe
        try? process.run()
        process.waitUntilExit()
        let path = String(data: pipe.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return path?.isEmpty == false ? path : nil
    }

    /// Run a gedfix command and return stdout
    static func run(arguments: [String]) async throws -> String {
        guard let gedfix = findGedfix() else {
            throw CLIError.notFound
        }

        return try await withCheckedThrowingContinuation { continuation in
            let process = Process()
            process.executableURL = URL(filePath: gedfix)
            process.arguments = arguments

            let outPipe = Pipe()
            let errPipe = Pipe()
            process.standardOutput = outPipe
            process.standardError = errPipe

            process.terminationHandler = { proc in
                let outData = outPipe.fileHandleForReading.readDataToEndOfFile()
                let errData = errPipe.fileHandleForReading.readDataToEndOfFile()
                let stdout = String(data: outData, encoding: .utf8) ?? ""
                let stderr = String(data: errData, encoding: .utf8) ?? ""

                if proc.terminationStatus == 0 {
                    continuation.resume(returning: stdout)
                } else {
                    continuation.resume(throwing: CLIError.failed(code: proc.terminationStatus, stderr: stderr))
                }
            }

            do {
                try process.run()
            } catch {
                continuation.resume(throwing: error)
            }
        }
    }

    /// Run gedfix stats and return parsed JSON
    static func stats(file: URL) async throws -> [String: Any] {
        let output = try await run(arguments: ["stats", file.path, "--json-output"])
        guard let data = output.data(using: .utf8),
              let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return [:]
        }
        return json
    }

    /// Check if gedfix is installed
    static var isAvailable: Bool {
        findGedfix() != nil
    }

    enum CLIError: LocalizedError {
        case notFound
        case failed(code: Int32, stderr: String)

        var errorDescription: String? {
            switch self {
            case .notFound:
                return "gedfix CLI not found. Install with: pip install -e /path/to/ged"
            case .failed(let code, let stderr):
                return "gedfix exited with code \(code): \(stderr)"
            }
        }
    }
}
