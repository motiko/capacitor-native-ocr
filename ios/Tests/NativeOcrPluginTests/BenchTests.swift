import XCTest
@testable import NativeOcrPlugin

/// Runs recognition over an external image set for accuracy benchmarks (QuickScan's
/// `bench/tools/ocr-compare.mjs`). Skipped unless `OCR_BENCH_JOBS` names a JSON job file:
/// `[{ "id": "...", "path": "/abs/image.png", "languages": ["en-US"] }]`. Results go to
/// `OCR_BENCH_OUT` as `[{ "id", "text", "ms" }]`. `OCR_BENCH_NO_TABLE_ROWS=1` turns off joining
/// table columns into rows.
///
/// xcodebuild passes environment variables to tests with a `TEST_RUNNER_` prefix:
///   TEST_RUNNER_OCR_BENCH_JOBS=/tmp/jobs.json TEST_RUNNER_OCR_BENCH_OUT=/tmp/native.json \
///   xcodebuild test -scheme CapacitorNativeOcr -destination '...' -only-testing:NativeOcrPluginTests/BenchTests
final class BenchTests: XCTestCase {
    private struct Job: Decodable {
        let id: String
        let path: String
        let languages: [String]
    }

    private struct Output: Encodable {
        let id: String
        let text: String
        let ms: Double // swiftlint:disable:this identifier_name (the JSON key ocr-compare reads)
    }

    func testRunBenchJobs() throws {
        let environment = ProcessInfo.processInfo.environment
        guard let jobsPath = environment["OCR_BENCH_JOBS"], let outPath = environment["OCR_BENCH_OUT"] else {
            throw XCTSkip("Set OCR_BENCH_JOBS and OCR_BENCH_OUT to run the benchmark.")
        }
        let jobs = try JSONDecoder().decode([Job].self, from: Data(contentsOf: URL(fileURLWithPath: jobsPath)))
        let ocr = NativeOcr()
        let tableRows = environment["OCR_BENCH_NO_TABLE_ROWS"] != "1"

        var outputs: [Output] = []
        for job in jobs {
            let image = try ImageLoader.load(path: job.path)
            let started = Date()
            let result = try ocr.recognize(image, options: RecognizeOptions(languages: job.languages, tableRows: tableRows))
            outputs.append(Output(id: job.id, text: result.text, ms: Date().timeIntervalSince(started) * 1000))
        }

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        try encoder.encode(outputs).write(to: URL(fileURLWithPath: outPath))
    }
}
