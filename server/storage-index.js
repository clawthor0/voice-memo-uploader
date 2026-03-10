const fs = require('fs');
const path = require('path');

const DATA_DIR = path.join(__dirname, 'data');
const INDEX_PATH = path.join(DATA_DIR, 'uploads-index.json');

function ensureStore() {
  if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
  }
  if (!fs.existsSync(INDEX_PATH)) {
    fs.writeFileSync(INDEX_PATH, JSON.stringify({}, null, 2), 'utf8');
  }
}

function loadIndex() {
  ensureStore();
  try {
    return JSON.parse(fs.readFileSync(INDEX_PATH, 'utf8'));
  } catch {
    return {};
  }
}

function saveIndex(index) {
  ensureStore();
  fs.writeFileSync(INDEX_PATH, JSON.stringify(index, null, 2), 'utf8');
}

function upsertUpload(uploadId, payload) {
  const index = loadIndex();
  index[uploadId] = {
    ...(index[uploadId] || {}),
    ...payload,
    updatedAt: new Date().toISOString(),
  };
  saveIndex(index);
  return index[uploadId];
}

function getUpload(uploadId) {
  const index = loadIndex();
  return index[uploadId] || null;
}

module.exports = { INDEX_PATH, upsertUpload, getUpload };
