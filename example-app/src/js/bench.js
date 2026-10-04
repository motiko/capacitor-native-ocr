import { NativeOcr } from 'capacitor-native-ocr';

import { bagOfWordErrors, charErrors } from './metrics.js';

// Runs the plugin over the cases in public/bench/manifest.json:
// { "name": "...", "cases": [{ "id", "set", "image", "languages", "truth" }] }
// with `image` and `truth` relative to public/bench/. See the example app's README.

const $ = (id) => document.getElementById(id);
const BASE = './bench/';
let lastResults = null;

export async function initBench() {
  let manifest;
  try {
    const response = await fetch(BASE + 'manifest.json');
    if (!response.ok) return;
    manifest = await response.json();
  } catch {
    return;
  }
  $('bench').hidden = false;
  $('bench-name').textContent = `${manifest.name ?? 'Benchmark'}: ${manifest.cases.length} images`;
  $('bench-run').addEventListener('click', () => run(manifest));
  $('bench-copy').addEventListener('click', copyResults);
}

async function run(manifest) {
  $('bench-run').disabled = true;
  $('bench-copy').disabled = true;
  const results = [];
  const scores = new Map(); // set → totals
  try {
    for (const [index, item] of manifest.cases.entries()) {
      $('bench-status').textContent = `${index + 1} / ${manifest.cases.length}: ${item.id}`;
      const [base64, truth] = await Promise.all([loadBase64(BASE + item.image), loadText(BASE + item.truth)]);
      const started = performance.now();
      // A failed image scores as empty text, so errors count against the engine.
      let text = '';
      let error;
      try {
        text = (await NativeOcr.recognize({ base64, languages: item.languages })).text;
      } catch (caught) {
        error = `${caught.code}: ${caught.message}`;
      }
      const ms = performance.now() - started;
      results.push({ id: item.id, text, ms, ...(error && { error }) });

      const set = scores.get(item.set) ?? { chars: [0, 0], words: [0, 0], ms: [] };
      const c = charErrors(text, truth);
      const w = bagOfWordErrors(text, truth);
      set.chars[0] += c.errors;
      set.chars[1] += c.total;
      set.words[0] += w.errors;
      set.words[1] += w.total;
      set.ms.push(ms);
      scores.set(item.set, set);
      showScores(scores);
    }
    $('bench-status').textContent = `Done: ${manifest.cases.length} images.`;
    lastResults = {
      corpus: manifest.name,
      date: new Date().toISOString(),
      userAgent: navigator.userAgent,
      results,
    };
    $('bench-copy').disabled = false;
  } catch (error) {
    $('bench-status').textContent = `Stopped: ${error.message}`;
  } finally {
    $('bench-run').disabled = false;
  }
}

function showScores(scores) {
  const pct = (part, total) => `${((100 * part) / Math.max(total, 1)).toFixed(2)} %`;
  const median = (values) => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)];
  const rows = [...scores].map(
    ([set, s]) =>
      `<tr><td>${set}</td><td>${s.ms.length}</td><td>${pct(...s.chars)}</td><td>${pct(...s.words)}</td><td>${Math.round(median(s.ms))} ms</td></tr>`,
  );
  $('bench-table').innerHTML =
    '<tr><th>Set</th><th>Images</th><th>CER</th><th>Order-free WER</th><th>Median time</th></tr>' + rows.join('');
}

async function copyResults() {
  const json = JSON.stringify(lastResults);
  $('bench-output').value = json;
  $('bench-output').hidden = false;
  try {
    await navigator.clipboard.writeText(json);
    $('bench-status').textContent = 'Results copied. Paste them into a file for ocr-compare.mjs --native.';
  } catch {
    $('bench-output').select();
    $('bench-status').textContent = 'Select the text below and copy it.';
  }
}

async function loadText(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${url}: ${response.status}`);
  return response.text();
}

async function loadBase64(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${url}: ${response.status}`);
  const blob = await response.blob();
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}
