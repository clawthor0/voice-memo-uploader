const fs = require('fs');
const path = require('path');

const PROCESSED_DIR = path.join(__dirname, 'processed');

function ensureProcessedDir() {
  if (!fs.existsSync(PROCESSED_DIR)) {
    fs.mkdirSync(PROCESSED_DIR, { recursive: true });
  }
}

async function processUploadedFile(file, uploadId) {
  ensureProcessedDir();
  const targetFileName = `${uploadId}-${Date.now()}-${file.originalname}`;
  const resultPath = path.join(PROCESSED_DIR, targetFileName);

  await fs.promises.copyFile(file.path, resultPath);

  return {
    sourcePath: file.path,
    resultPath,
    size: file.size,
    mimeType: file.mimetype,
    originalName: file.originalname,
  };
}

module.exports = { processUploadedFile };
