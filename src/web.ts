import { WebPlugin } from '@capacitor/core';

import type { NativeOcrPlugin } from './definitions';

export class NativeOcrWeb extends WebPlugin implements NativeOcrPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
