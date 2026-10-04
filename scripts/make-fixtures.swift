// Renders the test fixtures in ios/Tests/NativeOcrPluginTests/Fixtures.
// Run from the repo root: swift scripts/make-fixtures.swift
// The tests rely on the positions below; change both together.
import AppKit
import ImageIO
import UniformTypeIdentifiers

let width = 1600
let height = 1000
let outDir = URL(fileURLWithPath: "ios/Tests/NativeOcrPluginTests/Fixtures")

struct Text { let string: String; let x: CGFloat; let top: CGFloat; let size: CGFloat; let bold: Bool }

func render(_ texts: [Text]) -> CGImage {
    let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                            space: CGColorSpace(name: CGColorSpace.sRGB)!,
                            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    context.setFillColor(.white)
    context.fill(CGRect(x: 0, y: 0, width: width, height: height))
    // Flip to a top-left origin so `top` reads like the tests' coordinates.
    context.translateBy(x: 0, y: CGFloat(height))
    context.scaleBy(x: 1, y: -1)
    NSGraphicsContext.current = NSGraphicsContext(cgContext: context, flipped: true)
    for text in texts {
        let font = text.bold ? NSFont.boldSystemFont(ofSize: text.size) : NSFont.systemFont(ofSize: text.size)
        NSAttributedString(string: text.string, attributes: [.font: font, .foregroundColor: NSColor.black])
            .draw(at: CGPoint(x: text.x, y: text.top))
    }
    return context.makeImage()!
}

/// The same pixels turned 90° counter-clockwise, so EXIF orientation 6 shows them upright.
func storedSideways(_ image: CGImage) -> CGImage {
    let context = CGContext(data: nil, width: image.height, height: image.width, bitsPerComponent: 8, bytesPerRow: 0,
                            space: CGColorSpace(name: CGColorSpace.sRGB)!,
                            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    context.translateBy(x: CGFloat(image.height), y: 0)
    context.rotate(by: .pi / 2)
    context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
    return context.makeImage()!
}

func storedUpsideDown(_ image: CGImage) -> CGImage {
    let context = CGContext(data: nil, width: image.width, height: image.height, bitsPerComponent: 8, bytesPerRow: 0,
                            space: CGColorSpace(name: CGColorSpace.sRGB)!,
                            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    context.translateBy(x: CGFloat(image.width), y: CGFloat(image.height))
    context.rotate(by: .pi)
    context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
    return context.makeImage()!
}

func write(_ image: CGImage, _ name: String, type: UTType, properties: [CFString: Any] = [:]) {
    let url = outDir.appendingPathComponent(name)
    let destination = CGImageDestinationCreateWithURL(url as CFURL, type.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, image, properties as CFDictionary)
    precondition(CGImageDestinationFinalize(destination))
    print("wrote \(url.path)")
}

let english = render([
    Text(string: "Invoice 2026-104", x: 100, top: 80, size: 96, bold: true),
    Text(string: "The quick brown fox jumps over", x: 100, top: 320, size: 56, bold: false),
    Text(string: "the lazy dog near the river bank.", x: 100, top: 400, size: 56, bold: false),
    Text(string: "Total: 1,234.56 EUR", x: 100, top: 700, size: 56, bold: false),
])
write(english, "print-en.png", type: .png)
write(storedSideways(english), "print-en-exif6.jpg", type: .jpeg,
      properties: [kCGImagePropertyOrientation: NSNumber(value: 6),
                  kCGImagePropertyTIFFDictionary: [kCGImagePropertyTIFFOrientation: NSNumber(value: 6)],
                  kCGImageDestinationLossyCompressionQuality: 0.95])

write(render([
    Text(string: "Größere Änderungen für Übermorgen", x: 100, top: 300, size: 56, bold: false),
    Text(string: "Straße und Gebühren", x: 100, top: 380, size: 56, bold: false),
]), "print-de.png", type: .png)

// A receipt: names on the left, prices far to the right, so Vision returns two columns.
let receiptRows = [("Roggenbrot 750g", "3,40"), ("Mineralwasser 6x1L", "3,54"), ("Bananen", "1,76"), ("Joghurt Natur", "0,79")]
let receipt = render(
    [Text(string: "MUSTERMARKT", x: 100, top: 80, size: 64, bold: true)]
        + receiptRows.enumerated().flatMap { index, row in
            [Text(string: row.0, x: 100, top: 260 + CGFloat(index) * 90, size: 56, bold: false),
             Text(string: row.1, x: 1250, top: 260 + CGFloat(index) * 90, size: 56, bold: false)]
        }
        + [Text(string: "SUMME EUR", x: 100, top: 700, size: 56, bold: true),
           Text(string: "9,49", x: 1250, top: 700, size: 56, bold: true)]
)
write(receipt, "receipt.png", type: .png)
// The same pixels stored sideways with no EXIF tag: the text itself runs bottom to top.
write(storedSideways(receipt), "receipt-sideways.png", type: .png)
// And upside down, also without a tag.
write(storedUpsideDown(receipt), "receipt-upside-down.png", type: .png)

