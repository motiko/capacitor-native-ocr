import { NativeOcr } from 'capacitor-native-ocr';

import { initBench } from './bench.js';

const $ = (id) => document.getElementById(id);
let imageData = null;

async function init() {
  const { available } = await NativeOcr.isAvailable();
  $('status').textContent = available ? 'Native OCR is available.' : 'Native OCR is not available on this platform.';
  if (available) {
    const { languages } = await NativeOcr.getSupportedLanguages();
    $('supported').textContent = languages.join(', ');
    await loadSample();
  }
}

function showImage(blob) {
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = () => {
      imageData = reader.result;
      $('preview').src = imageData;
      clearOverlay();
      $('run').disabled = false;
      resolve();
    };
    reader.readAsDataURL(blob);
  });
}

$('file').addEventListener('change', () => {
  const file = $('file').files[0];
  if (file) showImage(file);
});

$('run').addEventListener('click', recognize);

async function recognize() {
  const languages = $('languages')
    .value.split(',')
    .map((tag) => tag.trim())
    .filter(Boolean);
  $('run').disabled = true;
  $('summary').textContent = 'Recognizing…';
  const started = performance.now();
  try {
    const result = await NativeOcr.recognize({
      base64: imageData,
      languages,
      level: $('level').value,
      languageCorrection: $('correction').checked,
    });
    const ms = Math.round(performance.now() - started);
    const words = result.blocks.flatMap((block) => block.lines).flatMap((line) => line.words);
    $('summary').textContent =
      `${ms} ms · ${result.imageSize.width}×${result.imageSize.height} px · ` +
      `${result.blocks.length} blocks · ${words.length} words` +
      (result.language ? ` · language ${result.language}` : '');
    $('text').textContent = result.text;
    drawBoxes(result);
  } catch (error) {
    $('summary').textContent = `${error.code ?? 'error'}: ${error.message}`;
  } finally {
    $('run').disabled = false;
  }
}

function clearOverlay() {
  const canvas = $('overlay');
  canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
  $('text').textContent = '';
  $('summary').textContent = '';
}

// Boxes are normalized with a top-left origin, so they scale straight onto the displayed image.
function drawBoxes(result) {
  const canvas = $('overlay');
  canvas.width = result.imageSize.width;
  canvas.height = result.imageSize.height;
  const context = canvas.getContext('2d');
  const stroke = (box, color, width) => {
    context.strokeStyle = color;
    context.lineWidth = width;
    context.strokeRect(box.x * canvas.width, box.y * canvas.height, box.width * canvas.width, box.height * canvas.height);
  };
  const scale = canvas.width / 400;
  for (const block of result.blocks) {
    stroke(block.box, 'rgba(0, 120, 255, 0.8)', 2 * scale);
    for (const line of block.lines) {
      for (const word of line.words) stroke(word.box, 'rgba(255, 60, 0, 0.9)', scale);
    }
  }
}

// Start with a sample image so the app shows a result right away.
async function loadSample() {
  const response = await fetch('./sample.png');
  await showImage(await response.blob());
  await recognize();
}

init();
initBench();
