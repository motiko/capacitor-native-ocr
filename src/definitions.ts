export interface NativeOcrPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
