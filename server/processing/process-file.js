#!/usr/bin/env node
const path = require('path');
const { processAudioFile } = require('./index');

async function main() {
  const filePathArg = process.argv[2];
  if (!filePathArg) {
    console.error('Usage: node server/processing/process-file.js <audio-file-path>');
    process.exit(1);
  }

  const { record, outputPath } = await processAudioFile(path.resolve(filePathArg));
  console.log(JSON.stringify({ outputPath, ...record }, null, 2));
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
