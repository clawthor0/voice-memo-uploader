const fs = require('fs');
const path = require('path');
const assert = require('assert');
const { processAudioFile } = require('./index');

async function runValidation() {
  const samplePath = path.join(__dirname, 'samples', 'sample-audio.wav');
  fs.mkdirSync(path.dirname(samplePath), { recursive: true });
  if (!fs.existsSync(samplePath)) {
    fs.writeFileSync(samplePath, 'sample-bytes', 'utf8');
  }

  process.env.MOCK_TRANSCRIPT = 'Meeting notes: follow up with client and set agenda.';
  delete process.env.STT_COMMAND_TEMPLATE;

  const ok = await processAudioFile(samplePath);
  assert.strictEqual(ok.record.status, 'ok');
  assert.strictEqual(ok.record.category, 'meetings');
  assert.ok(ok.record.summary.length > 0);

  delete process.env.MOCK_TRANSCRIPT;
  process.env.STT_COMMAND_TEMPLATE = 'nonexistent-stt-cli {input}';

  const fallback = await processAudioFile(samplePath);
  assert.strictEqual(fallback.record.status, 'error');
  assert.ok(['failed', 'unavailable', 'empty'].includes(fallback.record.metadata.stt_status));
  assert.ok(fs.existsSync(fallback.outputPath));

  console.log('Validation OK: success + fallback scenarios passed.');
}

runValidation().catch((err) => {
  console.error('Validation failed:', err);
  process.exit(1);
});
