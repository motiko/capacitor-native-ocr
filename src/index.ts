import { registerPlugin } from '@capacitor/core';

import type { NativeOcrPlugin } from './definitions';

const NativeOcr = registerPlugin<NativeOcrPlugin>('NativeOcr', {
  web: () => import('./web').then((m) => new m.NativeOcrWeb()),
});

export * from './definitions';
export { NativeOcr };
