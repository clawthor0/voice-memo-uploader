# Quick Start - Voice Memo Uploader APK Build

## 30 Seconds to APK

### Step 1: Prerequisites
```bash
# Verify Java is installed
java -version  # Should be 11 or higher

# Set Android SDK path (or use Android Studio)
export ANDROID_HOME=/path/to/android/sdk
```

### Step 2: Build
```bash
cd android/
./gradlew assembleRelease
```

### Step 3: Install
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

**Done!** App should now be on your device.

---

## Troubleshooting

### Build fails - "ANDROID_HOME not set"
```bash
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleRelease
```

### Download fails on first run
- This is normal. Gradle downloads dependencies (~2-3 minutes)
- Re-run the build command if it stalls

### "adb: command not found"
```bash
export PATH=$PATH:$ANDROID_HOME/platform-tools
adb install -r app/build/outputs/apk/release/app-release.apk
```

### App crashes on launch
- Make sure Android 12+ device
- Grant READ_MEDIA_AUDIO permission when prompted
- Check logcat: `adb logcat | grep voicememouploader`

---

## What You're Building

**Name:** Voice Memo Uploader  
**Language:** Kotlin  
**UI:** Jetpack Compose  
**Purpose:** Scan voice memos, upload to OpenClaw via Tailscale  

**Includes:**
- ✅ MediaStore scanning
- ✅ Multi-select checkboxes
- ✅ HTTP multipart upload
- ✅ Tailscale IP configuration
- ✅ Real-time status display
- ✅ Material Design 3 UI

**APK Size:** ~6 MB (release)

---

## After Installation

1. **Open the app** on your Android device
2. **Tap "Scan Voice Memos"** → It finds your audio files
3. **Select files** → Check the boxes you want to upload
4. **Configure server** (optional) → Tap "Server Config", set Tailscale IP
5. **Tap "Upload Selected"** → Sends to OpenClaw

---

## Next Steps

- See `README.md` for full documentation
- See `BUILD_REPORT.md` for technical details
- Check server logs for upload confirmation

---

**Ready? Run:** `cd android/ && ./gradlew assembleRelease`
