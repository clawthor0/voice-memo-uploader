# Voice Memo Uploader - Full Project Spec

## Overview
Android app that scans voice memos, allows selective upload, sends to OpenClaw over Tailscale for transcription, summarization, and topic-based organization.

## Requirements

### Android App (APK)
- **Main Screen:**
  - Button to scan recently created voice memos
  - List view showing detected memos (filename, duration, creation time)
  - Checkboxes to select which memos to upload
  - Upload button (once selected)
  - Status indicator (uploading, done, errors)

- **Backend Communication:**
  - Upload to OpenClaw server via Tailscale IP
  - POST endpoint: `/api/voice-memos/upload`
  - Multipart form data with audio files
  - Handle network errors gracefully

- **Permissions:**
  - `READ_EXTERNAL_STORAGE` (or `READ_MEDIA_AUDIO` on API 33+)
  - `INTERNET`
  - `WRITE_EXTERNAL_STORAGE` (if caching)

- **UI Framework:** Android native (Kotlin) or Jetpack Compose
- **Build:** Android Studio, target API 31+

### OpenClaw Server Receiver
- **Endpoint:** `POST /api/voice-memos/upload`
- **Function:**
  - Receive multipart audio files
  - Save to temp location
  - Call transcription (local STT)
  - Generate summaries per file
  - Categorize by topic
  - Store results (database/files)
  - Return confirmation to app

### Transcription & Processing
- **STT:** Local (Whisper or equivalent)
- **Summarization:** Claude or local LLM
- **Categorization:**
  - Meetings
  - Ideas/To-Do
  - Spiritual Questions/Topics
  - Other
- **Output Format:** JSON with transcription, summary, category, timestamp

### Networking
- Upload over Tailscale IP
- Secure (HTTPS preferred, or Tailscale's built-in encryption)
- Handle offline gracefully (optional: queue for later)

## Deliverables
1. **APK file** — Ready to sideload/test on Android device
2. **Source code** — For modification/distribution
3. **Server code** — OpenClaw handler script
4. **Documentation** — Setup & usage guide

## Timeline
- Build: ~2-3 hours
- QA & fixes: ~1 hour
- Total: Target same-day delivery

## Categories (Hardcoded Classifier)
```
Meetings: calendar, meeting, discussion, project, status, update, sprint
To-Do/Ideas: task, todo, idea, note, remember, action item, shopping
Spiritual: spiritual, meditation, prayer, faith, philosophy, meaning, consciousness
Default: Other
```
