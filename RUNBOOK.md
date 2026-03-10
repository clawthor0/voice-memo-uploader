# RUNBOOK - Voice Memo Uploader Webhook Service

## Location

Server code lives in `server/`.

## Install

```bash
cd /home/siem/.openclaw/workspace/projects/voice-memo-uploader/server
npm install
```

## Environment

Create `.env` in `server/` (example):

```env
PORT=3000
UPLOAD_WEBHOOK_PATH=/webhook/upload-voice-memo
# Optional:
# UPLOAD_AUTH_TOKEN=replace-with-long-random-token
```

## Run manually

```bash
cd /home/siem/.openclaw/workspace/projects/voice-memo-uploader/server
npm start
```

Health check:

```bash
curl -s http://127.0.0.1:3000/health | jq
```

---

## Run with systemd

Create `/etc/systemd/system/voice-memo-webhook.service`:

```ini
[Unit]
Description=Voice Memo Uploader Webhook
After=network.target

[Service]
Type=simple
User=siem
WorkingDirectory=/home/siem/.openclaw/workspace/projects/voice-memo-uploader/server
Environment=NODE_ENV=production
Environment=PORT=3000
Environment=UPLOAD_WEBHOOK_PATH=/webhook/upload-voice-memo
# Environment=UPLOAD_AUTH_TOKEN=replace-with-long-random-token
ExecStart=/usr/bin/node /home/siem/.openclaw/workspace/projects/voice-memo-uploader/server/index.js
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Enable/start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now voice-memo-webhook
sudo systemctl status voice-memo-webhook
```

Logs:

```bash
journalctl -u voice-memo-webhook -f
```

---

## Run with PM2

```bash
cd /home/siem/.openclaw/workspace/projects/voice-memo-uploader/server
pm2 start index.js --name voice-memo-webhook --update-env
pm2 save
pm2 status
pm2 logs voice-memo-webhook
```

Optional env before start:

```bash
export PORT=3000
export UPLOAD_WEBHOOK_PATH=/webhook/upload-voice-memo
# export UPLOAD_AUTH_TOKEN=replace-with-long-random-token
```

---

## Test upload from curl

```bash
curl -s -X POST "http://127.0.0.1:3000/webhook/upload-voice-memo" \
  -F "files=@/path/to/test1.mp3" \
  -F "files=@/path/to/test2.m4a"
```

Response contains `uploadId` and status URL.

Check status:

```bash
curl -s "http://127.0.0.1:3000/api/voice-memos/status/<uploadId>" | jq
```

Status transitions: `queued` -> `processing` -> `completed` (or `failed`).

Index file is stored at:

`server/data/uploads-index.json`
