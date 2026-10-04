export interface NativeOcrPlugin {
  /**
   * Whether on-device recognition works on this platform.
   *
   * `true` on iOS. `false` on the web, and on Android until its
   * implementation ships.
   *
   * @since 0.1.0
   */
  isAvailable(): Promise<{ available: boolean }>;

  /**
   * The languages the engine can recognize, as BCP-47 tags in the
   * engine's own spelling (for example `en-US`, `de-DE`, `zh-Hans`).
   *
   * On iOS the list depends on the recognition level: `fast` supports
   * fewer languages than `accurate`.
   *
   * @since 0.1.0
   */
  getSupportedLanguages(options?: GetSupportedLanguagesOptions): Promise<{ languages: string[] }>;

  /**
   * Recognize the text in one image.
   *
   * Rejects with one of the {@link NativeOcrErrorCode} codes.
   *
   * @since 0.1.0
   */
  recognize(options: RecognizeOptions): Promise<RecognizeResult>;
}

export type RecognitionLevel = 'accurate' | 'fast';

export interface GetSupportedLanguagesOptions {
  /**
   * @default 'accurate'
   * @since 0.1.0
   */
  level?: RecognitionLevel;
}

export interface RecognizeOptions {
  /**
   * The image to read: a file path, a `file://` URL, or a URL made by
   * `Capacitor.convertFileSrc()`. Pass either `path` or `base64`.
   *
   * @since 0.1.0
   */
  path?: string;

  /**
   * The image as base64 data, with or without a `data:` URL prefix.
   *
   * @since 0.1.0
   */
  base64?: string;

  /**
   * BCP-47 tags in priority order, for example `['de-DE', 'en-US']`.
   * A tag that isn't supported as written falls back to a supported tag for
   * the same language, so `de` and `de-AT` both mean `de-DE`. Omit for the
   * platform default.
   *
   * @since 0.1.0
   */
  languages?: string[];

  /**
   * iOS 16+: let Vision detect the language. Ignored on older versions.
   *
   * @default true when `languages` is empty, otherwise false
   * @since 0.1.0
   */
  detectLanguage?: boolean;

  /**
   * @default 'accurate'
   * @since 0.1.0
   */
  level?: RecognitionLevel;

  /**
   * Let the engine correct words against its language model.
   *
   * @default true
   * @since 0.1.0
   */
  languageCorrection?: boolean;

  /**
   * iOS only: extra words for language correction, such as names or
   * product terms. Has no effect when `languageCorrection` is false.
   *
   * @since 0.1.0
   */
  customWords?: string[];
}

export interface RecognizeResult {
  /**
   * All recognized text in reading order. Lines are separated by `\n`,
   * blocks by an empty line (`\n\n`).
   *
   * @since 0.1.0
   */
  text: string;

  /**
   * Size of the image in pixels after its EXIF orientation is applied.
   * Multiply a {@link Box} by this to get pixel coordinates.
   *
   * @since 0.1.0
   */
  imageSize: { width: number; height: number };

  /**
   * The text structure: blocks contain lines, lines contain words.
   *
   * @since 0.1.0
   */
  blocks: Block[];

  /**
   * How far to turn the image clockwise, in degrees, so its text reads
   * upright: `0` for an upright page, `180` for one upside down, `90` or
   * `270` for one on its side. Taken from the direction most of the text
   * runs in; `0` when nothing was recognized.
   *
   * Nothing else needs it: `text` already reads in the right order whatever
   * the rotation, and boxes stay in the coordinates of the image as given.
   *
   * @since 0.2.0
   */
  rotation: 0 | 90 | 180 | 270;

  /**
   * The dominant language of the recognized text as a BCP-47 tag, when it
   * can be identified.
   *
   * @since 0.1.0
   */
  language?: string;
}

/**
 * A rectangle normalized to 0..1 of the oriented image, with the origin at
 * the top left on every platform.
 */
export interface Box {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface Block {
  text: string;
  box: Box;
  lines: Line[];
}

export interface Line {
  text: string;
  box: Box;
  /** 0..1, when the engine reports it. */
  confidence?: number;
  words: Word[];
}

export interface Word {
  text: string;
  box: Box;
  /**
   * 0..1, when the engine reports it. Apple Vision scores whole lines, so
   * on iOS a word carries its line's confidence.
   */
  confidence?: number;
}

/**
 * The `code` of a rejected call.
 *
 * - `unavailable`: no on-device engine on this platform.
 * - `invalid-image`: no image given, or the file or data can't be decoded.
 * - `unsupported-language`: a requested language isn't supported.
 * - `recognition-failed`: the engine returned an error.
 */
export type NativeOcrErrorCode = 'unavailable' | 'invalid-image' | 'unsupported-language' | 'recognition-failed';
