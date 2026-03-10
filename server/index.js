require('dotenv').config();

const express = require('express');
const multer = require('multer');
const crypto = require('crypto');
const path = require('path');
const fs = require('fs');

const { processUploadedFile } = require('./processing/index');
const { upsertUpload, getUpload, INDEX_PATH } = require('./storage-index');

const app = express();
const PORT = Number(process.env.PORT || 3000);

const uploadDir = path.join(__dirname, 'uploads');
const outputDir = path.join(__dirname, 'output');
const publicDir = path.join(__dirname, 'public');
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir, { recursive: true });
}

const upload = multer({ dest: uploadDir });

const webhookPath = process.env.UPLOAD_WEBHOOK_PATH || '/webhook/upload-voice-memo';
const defaultApiPath = '/api/voice-memos/upload';
const uploadAuthToken = process.env.UPLOAD_AUTH_TOKEN;

let processingChain = Promise.resolve();

function loadIndex() {
  try {
    if (!fs.existsSync(INDEX_PATH)) return {};
    return JSON.parse(fs.readFileSync(INDEX_PATH, 'utf8'));
  } catch {
    return {};
  }
}

function loadOutputRecords() {
  if (!fs.existsSync(outputDir)) return [];

  const files = fs.readdirSync(outputDir)
    .filter((f) => f.endsWith('.jsonl'))
    .sort();

  const all = [];
  for (const file of files) {
    const fullPath = path.join(outputDir, file);
    const lines = fs.readFileSync(fullPath, 'utf8').split('\n').filter(Boolean);
    for (const line of lines) {
      try {
        all.push(JSON.parse(line));
      } catch {
        // ignore malformed lines
      }
    }
  }
  return all;
}

function buildRecordings() {
  const index = loadIndex();
  const uploads = Object.entries(index).map(([uploadId, item]) => ({ uploadId, ...item }));
  const outputRecords = loadOutputRecords();

  const bySourcePath = new Map();
  for (const out of outputRecords) {
    if (out.audio_file_path) {
      bySourcePath.set(path.resolve(out.audio_file_path), out);
    }
  }

  const records = [];

  for (const upload of uploads) {
    const uploadId = upload.uploadId;
    const createdAt = upload.createdAt || upload.updatedAt || null;

    if (Array.isArray(upload.results) && upload.results.length) {
      for (const result of upload.results) {
        const output = bySourcePath.get(path.resolve(result.sourcePath || '')) || null;
        records.push({
          uploadId,
          filename: result.originalName || path.basename(result.sourcePath || ''),
          createdAt,
          updatedAt: upload.updatedAt || null,
          processedAt: output?.processed_at || null,
          transcript: result.transcript || output?.transcript || '',
          summary: result.summary || output?.summary || '',
          category: result.category || output?.category || 'other',
          status: result.status || upload.status || output?.status || 'unknown',
          error: result.error || output?.error || null,
        });
      }
      continue;
    }

    if (Array.isArray(upload.files) && upload.files.length) {
      for (const file of upload.files) {
        const output = bySourcePath.get(path.resolve(file.path || '')) || null;
        records.push({
          uploadId,
          filename: file.originalName || path.basename(file.path || ''),
          createdAt,
          updatedAt: upload.updatedAt || null,
          processedAt: output?.processed_at || null,
          transcript: output?.transcript || '',
          summary: output?.summary || '',
          category: output?.category || 'other',
          status: upload.status || output?.status || 'queued',
          error: output?.error || upload.error || null,
        });
      }
      continue;
    }

    records.push({
      uploadId,
      filename: null,
      createdAt,
      updatedAt: upload.updatedAt || null,
      processedAt: null,
      transcript: '',
      summary: '',
      category: 'other',
      status: upload.status || 'unknown',
      error: upload.error || null,
    });
  }

  return records.sort((a, b) => {
    const ta = new Date(a.createdAt || a.updatedAt || a.processedAt || 0).getTime();
    const tb = new Date(b.createdAt || b.updatedAt || b.processedAt || 0).getTime();
    return tb - ta;
  });
}

function enqueueUploadProcessing(uploadId, files) {
  processingChain = processingChain.then(async () => {
    const current = getUpload(uploadId);
    if (!current) return;

    upsertUpload(uploadId, { status: 'processing' });

    const results = [];
    for (const file of files) {
      const result = await processUploadedFile(file, uploadId);
      results.push(result);
    }

    upsertUpload(uploadId, {
      status: 'completed',
      results,
    });
  }).catch((error) => {
    upsertUpload(uploadId, {
      status: 'failed',
      error: error.message,
    });
  });
}

app.get('/health', (req, res) => {
  res.json({
    ok: true,
    service: 'voice-memo-uploader-webhook',
    uploadWebhookPath: webhookPath,
    indexPath: INDEX_PATH,
    authTokenRequired: Boolean(uploadAuthToken),
  });
});

function uploadHandler(req, res) {
  if (uploadAuthToken) {
    const provided = req.header('x-upload-token');
    if (provided !== uploadAuthToken) {
      return res.status(401).json({ error: 'Unauthorized: invalid upload token' });
    }
  }

  const files = req.files || [];
  if (!files.length) {
    return res.status(400).json({ error: 'No files received. Use multipart field name: files' });
  }

  const uploadId = crypto.randomUUID();
  upsertUpload(uploadId, {
    status: 'queued',
    createdAt: new Date().toISOString(),
    fileCount: files.length,
    files: files.map((f) => ({
      originalName: f.originalname,
      path: f.path,
      size: f.size,
      mimeType: f.mimetype,
    })),
  });

  enqueueUploadProcessing(uploadId, files);

  return res.status(202).json({
    uploadId,
    status: 'queued',
    statusUrl: `/api/voice-memos/status/${uploadId}`,
  });
}

app.post(defaultApiPath, upload.array('files'), uploadHandler);
if (webhookPath !== defaultApiPath) {
  app.post(webhookPath, upload.array('files'), uploadHandler);
}

app.get('/api/voice-memos/status/:id', (req, res) => {
  const record = getUpload(req.params.id);
  if (!record) {
    return res.status(404).json({ error: 'Upload id not found' });
  }
  return res.json({
    uploadId: req.params.id,
    ...record,
  });
});

app.get('/api/recordings', (req, res) => {
  const recordings = buildRecordings();
  res.json({ recordings, count: recordings.length });
});

app.get('/api/recordings/:uploadId', (req, res) => {
  const recordings = buildRecordings().filter((r) => r.uploadId === req.params.uploadId);
  if (!recordings.length) {
    return res.status(404).json({ error: 'Recording not found' });
  }

  // Return the latest entry for detail page, and include all entries for multi-file uploads.
  return res.json({
    recording: recordings[0],
    items: recordings,
  });
});

app.use(express.static(publicDir));
app.get(['/', '/voice'], (req, res) => {
  res.redirect('/dashboard');
});
app.get(['/dashboard', '/dashboard/recordings/:uploadId'], (req, res) => {
  res.sendFile(path.join(publicDir, 'index.html'));
});

app.listen(PORT, () => {
  console.log(`Webhook service listening on :${PORT}`);
  console.log(`POST upload paths: ${defaultApiPath}${webhookPath !== defaultApiPath ? `, ${webhookPath}` : ''}`);
  console.log('Dashboard: /dashboard');
  console.log('Recordings API: /api/recordings');
  console.log('Recording detail API: /api/recordings/:uploadId');
  console.log('Status endpoint: /api/voice-memos/status/:id');
});
