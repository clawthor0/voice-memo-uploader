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
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir, { recursive: true });
}

const upload = multer({ dest: uploadDir });

const webhookPath = process.env.UPLOAD_WEBHOOK_PATH || '/webhook/upload-voice-memo';
const defaultApiPath = '/api/voice-memos/upload';
const uploadAuthToken = process.env.UPLOAD_AUTH_TOKEN;

let processingChain = Promise.resolve();

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

app.listen(PORT, () => {
  console.log(`Webhook service listening on :${PORT}`);
  console.log(`POST upload paths: ${defaultApiPath}${webhookPath !== defaultApiPath ? `, ${webhookPath}` : ''}`);
  console.log(`Status endpoint: /api/voice-memos/status/:id`);
});
