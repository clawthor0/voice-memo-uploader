const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');
const { promisify } = require('util');

const execAsync = promisify(exec);
const OUTPUT_DIR = path.join(__dirname, '..', 'output');

function summarizeTranscript(transcript, maxSentences = 3) {
  if (!transcript || !transcript.trim()) return '';
  const clean = transcript.replace(/\s+/g, ' ').trim();
  const sentences = clean.split(/(?<=[.!?])\s+/).filter(Boolean);
  return (sentences.length ? sentences.slice(0, maxSentences).join(' ') : clean.slice(0, 240)).trim();
}

function categorizeTranscript(transcript) {
  const text = (transcript || '').toLowerCase();
  const categories = {
    meetings: ['meeting', 'agenda', 'standup', 'client', 'follow up', 'action item', 'deadline'],
    ideas_todo: ['idea', 'todo', 'task', 'build', 'feature', 'reminder', 'prototype'],
    spiritual: ['prayer', 'gratitude', 'god', 'faith', 'spiritual', 'meditation', 'blessing'],
  };

  for (const [name, keywords] of Object.entries(categories)) {
    if (keywords.some((k) => text.includes(k))) return name;
  }
  return 'other';
}

function outputFileForDate(date = new Date()) {
  const y = date.getUTCFullYear();
  const m = String(date.getUTCMonth() + 1).padStart(2, '0');
  const d = String(date.getUTCDate()).padStart(2, '0');
  return path.join(OUTPUT_DIR, `${y}-${m}-${d}.jsonl`);
}

function appendJsonl(record) {
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  const outputPath = outputFileForDate();
  fs.appendFileSync(outputPath, `${JSON.stringify(record)}\n`, 'utf8');
  return outputPath;
}

async function runStt(audioPath) {
  if (process.env.MOCK_TRANSCRIPT) {
    return { stt_status: 'mocked', transcript: process.env.MOCK_TRANSCRIPT };
  }

  const template = process.env.STT_COMMAND_TEMPLATE;
  if (!template) {
    return {
      stt_status: 'unavailable',
      transcript: '',
      error: 'STT_COMMAND_TEMPLATE not configured',
    };
  }

  const cmd = template.replaceAll('{input}', audioPath.replace(/"/g, '\\"'));
  try {
    const timeout = Number(process.env.STT_TIMEOUT_MS || 180000);
    const { stdout, stderr } = await execAsync(cmd, {
      shell: '/bin/bash',
      timeout,
      maxBuffer: 50 * 1024 * 1024,
    });

    const transcript = (stdout || '').trim();
    if (!transcript) {
      return {
        stt_status: 'empty',
        transcript: '',
        error: (stderr || 'STT produced empty transcript').trim(),
      };
    }

    return {
      stt_status: 'ok',
      transcript,
      stt_stderr: (stderr || '').trim() || null,
    };
  } catch (error) {
    return {
      stt_status: 'failed',
      transcript: '',
      error: error.message,
      stt_exit_code: error.code || null,
      stt_stderr: error.stderr ? String(error.stderr).trim() : null,
    };
  }
}

async function processAudioFile(audioFilePath) {
  const record = {
    schema_version: '1.0.0',
    processed_at: new Date().toISOString(),
    audio_file_path: path.resolve(audioFilePath || ''),
    status: 'ok',
    error: null,
    transcript: '',
    summary: '',
    category: 'other',
    metadata: {
      stt_command_template: process.env.STT_COMMAND_TEMPLATE || null,
      stt_timeout_ms: Number(process.env.STT_TIMEOUT_MS || 180000),
      stt_status: 'not_started',
    },
  };

  if (!audioFilePath || typeof audioFilePath !== 'string') {
    record.status = 'error';
    record.error = 'Invalid audio file path';
    const outputPath = appendJsonl(record);
    return { record, outputPath };
  }

  if (!fs.existsSync(audioFilePath)) {
    record.status = 'error';
    record.error = `Audio file not found: ${audioFilePath}`;
    const outputPath = appendJsonl(record);
    return { record, outputPath };
  }

  const stt = await runStt(audioFilePath);
  record.metadata.stt_status = stt.stt_status;
  if (stt.stt_stderr) record.metadata.stt_stderr = stt.stt_stderr;
  if (stt.stt_exit_code !== undefined && stt.stt_exit_code !== null) record.metadata.stt_exit_code = stt.stt_exit_code;

  record.transcript = stt.transcript || '';
  if (stt.error) {
    record.status = 'error';
    record.error = stt.error;
  }

  record.summary = summarizeTranscript(record.transcript);
  record.category = categorizeTranscript(record.transcript);

  const outputPath = appendJsonl(record);
  return { record, outputPath };
}

async function processUploadedFile(file, uploadId) {
  const { record, outputPath } = await processAudioFile(file.path);

  return {
    uploadId,
    sourcePath: file.path,
    outputPath,
    transcript: record.transcript,
    summary: record.summary,
    category: record.category,
    status: record.status,
    error: record.error,
    originalName: file.originalname,
    mimeType: file.mimetype,
    size: file.size,
  };
}

module.exports = {
  processUploadedFile,
  processAudioFile,
  summarizeTranscript,
  categorizeTranscript,
  outputFileForDate,
};
