# capacitor-native-ocr

On-device text recognition for Capacitor apps, with positions for every word.

- **iOS:** Apple Vision (`VNRecognizeTextRequest`), with recognition languages, automatic language detection and custom words.
- **Android:** ML Kit Text Recognition v2 with the bundled models, so it works offline from the first launch. Latin script by default; Chinese, Devanagari, Japanese and Korean on request.
- **Web:** `isAvailable()` returns `false`; keep your own engine there (for example Tesseract.js).

Recognition runs on the device; the plugin itself makes no network requests and collects no analytics. The iOS package ships a privacy manifest. On Android the models are bundled and work offline, but ML Kit itself may send Google metrics about its performance and use; see [ML Kit's terms](https://developers.google.com/ml-kit/terms).

## Install

```bash
npm install capacitor-native-ocr
npx cap sync
```

Works with Swift Package Manager and CocoaPods on iOS (iOS 15+) and on Android 7.0+ (API 24). Requires Capacitor 8.

### Android script models

The Latin model (about 4 MB per ABI) is always included. Each other script adds its own model of several MB, so it's off unless the app asks for it in `android/variables.gradle`:

```groovy
ext {
    // ...
    nativeOcrChinese = true    // zh-Hans, zh-Hant
    nativeOcrDevanagari = true // hi, mr, ne, sa
    nativeOcrJapanese = true   // ja
    nativeOcrKorean = true     // ko
}
```

Run `npx cap sync android` after changing them. You can also pin the versions with `mlkitTextRecognitionVersion` (default `16.0.1`) and `androidxExifInterfaceVersion` (default `1.4.2`).

## Usage

```ts
import { NativeOcr } from 'capacitor-native-ocr';

const { available } = await NativeOcr.isAvailable();
if (available) {
  const result = await NativeOcr.recognize({
    path: photo.path, // a file path or file:// URL, e.g. from @capacitor/camera
    languages: ['de-DE', 'en-US'],
  });

  console.log(result.text);
  for (const block of result.blocks) {
    for (const line of block.lines) {
      for (const word of line.words) {
        // Normalized box → pixels in the (EXIF-oriented) image.
        const x = word.box.x * result.imageSize.width;
        const y = word.box.y * result.imageSize.height;
      }
    }
  }
}
```

You can pass `base64` (with or without a `data:` prefix) instead of `path`. Rejected calls carry a `code`: `unavailable`, `invalid-image`, `unsupported-language` or `recognition-failed`.

## Coordinates

Every box is normalized to 0..1 of the image **after its EXIF orientation is applied**, with the origin at the **top left**, on every platform. `imageSize` is that oriented size in pixels.

```
(0,0) ─────────────────────────────▶ x (1)
  │    ┌──────────────┐
  │    │ Invoice      │  box.y = top edge / image height
  │    └──────────────┘
  │    box.x = left edge / image width
  ▼
  y (1)
```

Apple Vision itself uses a bottom-left origin; the plugin converts it. ML Kit's boxes are already top-left; on Android the plugin turns the image by its EXIF orientation before recognition.

## Structure

`blocks` → `lines` → `words`. In `text`, lines are joined by `\n` and blocks by an empty line.

- **Words** are separated by whitespace, so punctuation stays with its word (`Total:`, `1,234.56`).
- **Confidence** is 0..1. Apple Vision scores whole lines, so on iOS each word carries its line's confidence. ML Kit scores lines and words separately.
- **Tables:** Vision returns a table column by column, and ML Kit often makes each price a block of its own. The plugin joins a column of short, mostly numeric cells (prices, amounts, quantities) into the rows on its left, so a receipt reads `Milch 1,5% 1L 3,27` rather than all names followed by all prices. Two-column prose and side-by-side text, such as a business card, are left as columns.
- **Sideways pages:** Vision and ML Kit both read text that runs sideways or upside down, in one pass. The plugin works out which way the lines run (`rotation`) and orders them as if the page were upright; boxes stay in image coordinates. ML Kit orders its blocks by where they sit in the image rather than in the text, and on a sideways page sometimes splits a line in two, so on Android the plugin puts lines into reading order itself (column by column, like Vision) and joins line pieces that sit on one row a word's gap apart.
- **Single characters** standing alone, such as a quantity column of `1` and `2`, are often not returned by Apple Vision at all.
- **Blocks:** Vision returns lines only. The plugin groups lines into blocks when a line sits right below the previous one, overlaps it horizontally and has a similar text height. ML Kit's own blocks are dropped in favour of the same grouping, so both platforms structure a page the same way.
- **`language`** is the dominant language of the recognized text, when it can be identified: from Apple's NaturalLanguage framework on iOS, and on Android from the language ML Kit reports per line, weighted by line length. ML Kit's guess is per line and often wrong for short ones (a German receipt line may come back as `da` or `fr`).

## Languages

`getSupportedLanguages()` lists the tags the engine accepts. On iOS this depends on the OS version and the level: `fast` supports fewer languages than `accurate`. A tag that isn't supported as written falls back to one for the same language, so `de` and `de-AT` both mean `de-DE` on iOS. An unknown language rejects with `unsupported-language`.

On Android, ML Kit recognizes by script rather than by language, so the list is the languages [ML Kit names](https://developers.google.com/ml-kit/vision/text-recognition/v2/languages) for the bundled models, as plain language tags: `af`, `az`, `bs`, `ca`, `ceb`, `cs`, `cy`, `da`, `de`, `en`, `eo`, `es`, `et`, `eu`, `fi`, `fil`, `fr`, `ga`, `gl`, `hr`, `ht`, `hu`, `id`, `is`, `it`, `jv`, `la`, `lt`, `lv`, `ms`, `mt`, `nl`, `no`, `pl`, `pt`, `ro`, `sk`, `sl`, `sq`, `sr-Latn`, `sv`, `sw`, `tr`, `uz`, `vi`, `zu` for Latin, plus `zh-Hans` and `zh-Hant`, `hi`, `mr`, `ne` and `sa`, `ja`, and `ko` for the script models the app enables. `de-DE` resolves to `de`, and `zh` to `zh-Hans`. The requested languages only choose the model: the first non-Latin script among them, otherwise Latin. Each script model reads Latin text too, so `['ja', 'en']` reads both.

### Android differences

- `level`, `detectLanguage`, `languageCorrection` and `customWords` are accepted and ignored: ML Kit has one level, no language hint and no custom vocabulary.
- Recognition runs on a background thread, one image at a time.
- `path` also accepts a `content://` URI.

## Example app

`example-app/` opens with a sample image, recognizes it and draws the word and block boxes over it. Pick your own image to try others.

```bash
npm run build
cd example-app && npm install && npm run build && npx cap sync ios && npx cap open ios
# or: npx cap sync android && npx cap open android
```

## API

<docgen-index>

* [`isAvailable()`](#isavailable)
* [`getSupportedLanguages(...)`](#getsupportedlanguages)
* [`recognize(...)`](#recognize)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### isAvailable()

```typescript
isAvailable() => Promise<{ available: boolean; }>
```

Whether on-device recognition works on this platform.

`true` on iOS and Android, `false` on the web.

**Returns:** <code>Promise&lt;{ available: boolean; }&gt;</code>

**Since:** 0.1.0

--------------------


### getSupportedLanguages(...)

```typescript
getSupportedLanguages(options?: GetSupportedLanguagesOptions | undefined) => Promise<{ languages: string[]; }>
```

The languages the engine can recognize, as BCP-47 tags in the
engine's own spelling (for example `en-US`, `de-DE`, `zh-Hans` on iOS;
`en`, `de`, `zh-Hans` on Android).

On iOS the list depends on the recognition level: `fast` supports
fewer languages than `accurate`. On Android it lists the languages of
the bundled script models; `level` makes no difference.

| Param         | Type                                                                                  |
| ------------- | ------------------------------------------------------------------------------------- |
| **`options`** | <code><a href="#getsupportedlanguagesoptions">GetSupportedLanguagesOptions</a></code> |

**Returns:** <code>Promise&lt;{ languages: string[]; }&gt;</code>

**Since:** 0.1.0

--------------------


### recognize(...)

```typescript
recognize(options: RecognizeOptions) => Promise<RecognizeResult>
```

Recognize the text in one image.

Rejects with one of the {@link NativeOcrErrorCode} codes.

| Param         | Type                                                          |
| ------------- | ------------------------------------------------------------- |
| **`options`** | <code><a href="#recognizeoptions">RecognizeOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#recognizeresult">RecognizeResult</a>&gt;</code>

**Since:** 0.1.0

--------------------


### Interfaces


#### GetSupportedLanguagesOptions

| Prop        | Type                                                          | Default                 | Since |
| ----------- | ------------------------------------------------------------- | ----------------------- | ----- |
| **`level`** | <code><a href="#recognitionlevel">RecognitionLevel</a></code> | <code>'accurate'</code> | 0.1.0 |


#### RecognizeResult

| Prop            | Type                                            | Description                                                                                                                                                                                                                                                                                                                                                                                           | Since |
| --------------- | ----------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----- |
| **`text`**      | <code>string</code>                             | All recognized text in reading order. Lines are separated by `\n`, blocks by an empty line (`\n\n`).                                                                                                                                                                                                                                                                                                  | 0.1.0 |
| **`imageSize`** | <code>{ width: number; height: number; }</code> | Size of the image in pixels after its EXIF orientation is applied. Multiply a {@link <a href="#box">Box</a>} by this to get pixel coordinates.                                                                                                                                                                                                                                                        | 0.1.0 |
| **`blocks`**    | <code>Block[]</code>                            | The text structure: blocks contain lines, lines contain words.                                                                                                                                                                                                                                                                                                                                        | 0.1.0 |
| **`rotation`**  | <code>0 \| 90 \| 180 \| 270</code>              | How far to turn the image clockwise, in degrees, so its text reads upright: `0` for an upright page, `180` for one upside down, `90` or `270` for one on its side. Taken from the direction most of the text runs in; `0` when nothing was recognized. Nothing else needs it: `text` already reads in the right order whatever the rotation, and boxes stay in the coordinates of the image as given. | 0.2.0 |
| **`language`**  | <code>string</code>                             | The dominant language of the recognized text as a BCP-47 tag, when it can be identified.                                                                                                                                                                                                                                                                                                              | 0.1.0 |


#### Block

| Prop        | Type                                |
| ----------- | ----------------------------------- |
| **`text`**  | <code>string</code>                 |
| **`box`**   | <code><a href="#box">Box</a></code> |
| **`lines`** | <code>Line[]</code>                 |


#### Box

A rectangle normalized to 0..1 of the oriented image, with the origin at
the top left on every platform.

| Prop         | Type                |
| ------------ | ------------------- |
| **`x`**      | <code>number</code> |
| **`y`**      | <code>number</code> |
| **`width`**  | <code>number</code> |
| **`height`** | <code>number</code> |


#### Line

| Prop             | Type                                | Description                       |
| ---------------- | ----------------------------------- | --------------------------------- |
| **`text`**       | <code>string</code>                 |                                   |
| **`box`**        | <code><a href="#box">Box</a></code> |                                   |
| **`confidence`** | <code>number</code>                 | 0..1, when the engine reports it. |
| **`words`**      | <code>Word[]</code>                 |                                   |


#### Word

| Prop             | Type                                | Description                                                                                                                                 |
| ---------------- | ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| **`text`**       | <code>string</code>                 |                                                                                                                                             |
| **`box`**        | <code><a href="#box">Box</a></code> |                                                                                                                                             |
| **`confidence`** | <code>number</code>                 | 0..1, when the engine reports it. Apple Vision scores whole lines, so on iOS a word carries its line's confidence; ML Kit scores each word. |


#### RecognizeOptions

| Prop                     | Type                                                          | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                            | Default                                                      | Since |
| ------------------------ | ------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ | ----- |
| **`path`**               | <code>string</code>                                           | The image to read: a file path, a `file://` URL, or a URL made by `Capacitor.convertFileSrc()`. Pass either `path` or `base64`.                                                                                                                                                                                                                                                                                                                                        |                                                              | 0.1.0 |
| **`base64`**             | <code>string</code>                                           | The image as base64 data, with or without a `data:` URL prefix.                                                                                                                                                                                                                                                                                                                                                                                                        |                                                              | 0.1.0 |
| **`languages`**          | <code>string[]</code>                                         | BCP-47 tags in priority order, for example `['de-DE', 'en-US']`. A tag that isn't supported as written falls back to a supported tag for the same language, so `de` and `de-AT` both mean `de-DE` on iOS (and `de-DE` means `de` on Android). Omit for the platform default. On Android the languages pick the script model: the first non-Latin script requested (Chinese, Devanagari, Japanese or Korean), otherwise Latin. Each script model also reads Latin text. |                                                              | 0.1.0 |
| **`detectLanguage`**     | <code>boolean</code>                                          | iOS 16+: let Vision detect the language. Ignored on older versions and on Android.                                                                                                                                                                                                                                                                                                                                                                                     | <code>true when `languages` is empty, otherwise false</code> | 0.1.0 |
| **`level`**              | <code><a href="#recognitionlevel">RecognitionLevel</a></code> | iOS only; Android has one level.                                                                                                                                                                                                                                                                                                                                                                                                                                       | <code>'accurate'</code>                                      | 0.1.0 |
| **`languageCorrection`** | <code>boolean</code>                                          | iOS only: let the engine correct words against its language model.                                                                                                                                                                                                                                                                                                                                                                                                     | <code>true</code>                                            | 0.1.0 |
| **`customWords`**        | <code>string[]</code>                                         | iOS only: extra words for language correction, such as names or product terms. Has no effect when `languageCorrection` is false.                                                                                                                                                                                                                                                                                                                                       |                                                              | 0.1.0 |


### Type Aliases


#### RecognitionLevel

<code>'accurate' | 'fast'</code>

</docgen-api>
