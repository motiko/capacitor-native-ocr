import { CapacitorException, WebPlugin } from '@capacitor/core';
import type { ExceptionCode } from '@capacitor/core';

import type { NativeOcrErrorCode, NativeOcrPlugin, RecognizeResult } from './definitions';

const unavailableCode: NativeOcrErrorCode = 'unavailable';

/** The web has no on-device engine; web apps keep their own (for example Tesseract.js). */
export class NativeOcrWeb extends WebPlugin implements NativeOcrPlugin {
  async isAvailable(): Promise<{ available: boolean }> {
    return { available: false };
  }

  async getSupportedLanguages(): Promise<{ languages: string[] }> {
    throw this.unavailableError();
  }

  async recognize(): Promise<RecognizeResult> {
    throw this.unavailableError();
  }

  private unavailableError(): CapacitorException {
    return new CapacitorException(
      'Native OCR is not available on the web.',
      unavailableCode as unknown as ExceptionCode,
    );
  }
}
