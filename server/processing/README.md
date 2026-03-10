# Local Processing Module

Path: `server/processing/`

## Capabilities

- Accepts uploaded audio file path
- Runs local STT command placeholder (`STT_COMMAND_TEMPLATE`)
- Summarizes transcript
- Categorizes transcript into:
  - `meetings`
  - `ideas_todo`
  - `spiritual`
  - `other`
- Appends one JSON record to `server/output/YYYY-MM-DD.jsonl`
- Handles missing/broken STT tool gracefully (error state, no crash)

## Config

- `STT_COMMAND_TEMPLATE` (optional): command template with `{input}` placeholder
  - Example: `whisper-cli --model base --output-format txt {input}`
- `STT_TIMEOUT_MS` (optional): default `180000`
- `MOCK_TRANSCRIPT` (optional, test/dev only)

## Run manually

```bash
node server/processing/process-file.js server/uploads/<file>.mp3
```

## Validate behavior

```bash
cd server
npm run validate-processing
```

Validation includes:
1. success path via `MOCK_TRANSCRIPT`
2. fallback path via invalid STT command

## Webhook wiring

`server/index.js` already wires this in queue processing via:

```js
const { processUploadedFile } = require('./processing/index');
```

Each upload result now includes transcript summary metadata and category. Even when STT fails, webhook flow continues and status is stored in upload results.

## Output schema

See: `server/processing/schema.json`

Each JSONL line contains:
- `schema_version`
- `processed_at`
- `audio_file_path`
- `status` (`ok` | `error`)
- `error`
- `transcript`
- `summary`
- `category`
- `metadata` (`stt_status`, optional stderr/exit code, timeout)
