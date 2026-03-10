# Voice Memo Uploader Webhook Server

Minimal Express service for receiving Android voice memo uploads.

## Endpoints

- `GET /health` → health check JSON
- `POST /webhook/upload-voice-memo` → multipart upload (`files` field, supports multiple files)

Uploaded files are stored under:

- `server/uploads/<timestamp>/`

## Setup

```bash
cd server
npm install
cp .env.example .env
npm start
```

Default port: `3000`.

## Example request

```bash
curl -X POST http://127.0.0.1:3000/webhook/upload-voice-memo \
  -F "files=@/path/to/memo1.mp3" \
  -F "files=@/path/to/memo2.wav"
```

## Response shape

```json
{
  "uploadId": "uuid",
  "uploadDir": "uploads/2026-03-10T15-57-00-123Z",
  "fileCount": 2,
  "files": [
    {
      "id": "uuid",
      "originalName": "memo1.mp3",
      "savedName": "memo1.mp3",
      "mimeType": "audio/mpeg",
      "size": 12345,
      "path": "uploads/2026-03-10T15-57-00-123Z/memo1.mp3"
    }
  ]
}
```

## Running behind Tailscale Serve (HTTPS)

Assuming this host is on your tailnet and Tailscale is running:

```bash
# Start server locally on 127.0.0.1:3000
npm start

# Expose local HTTP service via Tailscale HTTPS
sudo tailscale serve https / http://127.0.0.1:3000
```

Check current serve config:

```bash
tailscale serve status
```

Your Android app can then target:

- Host: `https://<your-tailnet-device-name>.<tailnet>.ts.net`
- Upload path: `/webhook/upload-voice-memo`
- Port: `443` (or leave empty in URL mode)

## Notes

- Keep this service private to your tailnet.
- `uploads/` is gitignored by default.
