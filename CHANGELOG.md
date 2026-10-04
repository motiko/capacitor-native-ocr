# Changelog

## 0.1.0 (unreleased)

- iOS: text recognition with Apple Vision, with word, line and block boxes normalized to a top-left origin.
- iOS: recognition languages with fallback to the same language, automatic language detection (iOS 16+), language correction and custom words.
- iOS: images from a path, `file://` URL, `convertFileSrc()` URL or base64; EXIF orientation applied.
- iOS: table columns of short cells are joined into rows, so receipts and invoices read row by row.
- iOS: privacy manifest; SPM and CocoaPods.
- Android and web: `isAvailable()` returns `false`; other calls reject with `unavailable`.
