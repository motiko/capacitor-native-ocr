// Same definitions as QuickScan's bench/tools/metrics.mjs, so numbers from the app and from
// the Node harness compare directly.

export function normalizeText(text) {
  return text.normalize('NFC').replace(/\s+/g, ' ').trim();
}

function editDistance(a, b) {
  let previous = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let i = 1; i <= a.length; i++) {
    const current = [i];
    for (let j = 1; j <= b.length; j++) {
      current[j] = Math.min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    }
    previous = current;
  }
  return previous[b.length];
}

const words = (text) => (normalizeText(text) ? normalizeText(text).split(' ') : []);

/** Character errors by code point after normalizeText. */
export function charErrors(hypothesis, truth) {
  const t = Array.from(normalizeText(truth));
  return { errors: editDistance(Array.from(normalizeText(hypothesis)), t), total: t.length };
}

/** Word errors ignoring order (multiset match), which separates misreads from reading order. */
export function bagOfWordErrors(hypothesis, truth) {
  const h = words(hypothesis);
  const t = words(truth);
  const counts = new Map();
  for (const word of h) counts.set(word, (counts.get(word) ?? 0) + 1);
  let matched = 0;
  for (const word of t) {
    const n = counts.get(word) ?? 0;
    if (n > 0) {
      matched++;
      counts.set(word, n - 1);
    }
  }
  return { errors: Math.max(t.length, h.length) - matched, total: t.length };
}
