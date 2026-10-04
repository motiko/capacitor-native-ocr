# Changelog

## Unreleased

- Android: R8 consumer rules ship with the plugin, so apps with minify on build without their own `-dontwarn` for the script models they don't enable.

## 0.2.0 (2026-10-04)

- `rotation` in the result: how far to turn the image clockwise so its text reads upright (iOS: from the direction Vision's lines run, no extra passes).
- Android: text recognition with ML Kit Text Recognition v2 and the bundled Latin model, with the same result shape, layout and `rotation` as iOS: word, line and block boxes; receipts read row by row; sideways and upside-down pages ordered as if upright (ML Kit reads them in one pass, so no extra passes); EXIF orientation applied; `isAvailable()` returns `true`.
- Android: Chinese, Devanagari, Japanese and Korean models as opt-in Gradle flags (`nativeOcrChinese` and so on).
- Android: `level`, `detectLanguage`, `languageCorrection` and `customWords` are accepted and ignored.

## 0.1.1 (2026-10-04)

- iOS: table columns of short numeric cells are joined into rows, so receipts and invoices read row by row.
- iOS: sideways and upside-down pages are ordered as if upright, without needing an EXIF tag.

## 0.1.0 (2026-10-04)

- iOS: text recognition with Apple Vision, with word, line and block boxes normalized to a top-left origin.
- iOS: recognition languages with fallback to the same language, automatic language detection (iOS 16+), language correction and custom words.
- iOS: images from a path, `file://` URL, `convertFileSrc()` URL or base64; EXIF orientation applied.
- iOS: privacy manifest; SPM and CocoaPods.
- Android and web: `isAvailable()` returns `false`; other calls reject with `unavailable`.
