# Example app

Opens with a sample image, recognizes it and draws the block and word boxes over it. Pick another image to try it.

```bash
npm run build                # in the repo root: build the plugin
cd example-app
npm install && npm run build && npx cap sync ios && npx cap open ios
```

## Benchmark

When `src/public/bench/manifest.json` exists, the app shows a **Run benchmark** button. It recognizes every image,
scores it against its ground truth and shows, per set, the character error rate (CER), an order-free word error rate
(misread words, ignoring reading order) and the median time per image. **Copy results** puts the recognized text on
the clipboard as JSON for further scoring.

The manifest is a plain list, so any corpus works:

```json
{
  "name": "My corpus",
  "cases": [{ "id": "page-1", "set": "clean print", "image": "images/page-1.png", "languages": ["en-US"], "truth": "text/page-1.txt" }]
}
```

`image` and `truth` are relative to `src/public/bench/`. The folder is gitignored.

QuickScan's benchmark exports its corpus in this format (clean print pages, synthetic phone captures and CORD receipt
photos) and scores the copied results against Tesseract:

```bash
# in a QuickScan checkout
npm run bench:pages && npm run bench:synth -- --seed 1 --positives 100 --negatives 50
node bench/tools/ocr-compare.mjs --set all --export-app <this repo>/example-app/src/public/bench
# then in this folder: npm run build && npx cap sync ios, run on a device, Run benchmark, Copy results into results.json
node bench/tools/ocr-compare.mjs --set all --native results.json
```

Run it on a real device for numbers worth quoting: the Simulator runs Apple Vision without the Neural Engine. Times
include passing the image as base64 over the Capacitor bridge.
