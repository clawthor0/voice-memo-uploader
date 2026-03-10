# Voice Memo Uploader - Android App

Complete Android application for scanning and uploading voice memos to OpenClaw over Tailscale.

## Features

✅ **Voice Memo Scanning**
- Automatically detects MP3 and WAV files from device storage
- Displays filename, duration, creation date, and file size
- Sorts by most recent first

✅ **Multi-Select Upload**
- Checkbox selection for each memo
- Upload button triggers batch upload
- Real-time status updates

✅ **Tailscale Integration**
- Configurable server IP and port
- Direct HTTP POST via Tailscale network
- Multipart form data file uploads

✅ **Runtime Permissions**
- `READ_MEDIA_AUDIO` (Android 13+)
- `READ_EXTERNAL_STORAGE` (Android 12 and below)
- `INTERNET` for network access

✅ **Modern UI**
- Built with Jetpack Compose
- Material Design 3
- Responsive layout

## Project Structure

```
android/
├── app/
│   ├── build.gradle.kts
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/example/voicememouploader/
│   │       │   ├── MainActivity.kt          (Main UI, Compose)
│   │       │   ├── VoiceMemo.kt            (Data model)
│   │       │   ├── MediaStoreRepository.kt (MediaStore queries)
│   │       │   └── UploadService.kt        (Network/OkHttp)
│   │       └── res/
│   │           ├── values/strings.xml
│   │           ├── values/colors.xml
│   │           └── values/themes.xml
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew (Unix shell script)
└── README.md (this file)
```

## Prerequisites

- **Java 11+** (JDK)
- **Android SDK** (API 34 recommended, min API 31)
- **Gradle** (managed by gradlew wrapper)

### Setup

1. Install Android SDK via Android Studio or command line:
   ```bash
   sdkmanager "platforms;android-34"
   sdkmanager "build-tools;34.0.0"
   ```

2. Set `ANDROID_HOME` environment variable:
   ```bash
   export ANDROID_HOME=/path/to/android/sdk
   ```

## Building the APK

### Quick Build (Release)

```bash
cd android/
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### Debug Build

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Full Build Report

```bash
./gradlew build --info
```

## Installation

### On Connected Device

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Sideload (Manual)

Transfer the APK file to the device and install manually through the file manager or:

```bash
adb push app/build/outputs/apk/release/app-release.apk /sdcard/Download/
```

Then tap the file on the device to install.

## Configuration

### Server IP & Port

Launch the app and tap **"Server Config"** to set:
- **Tailscale IP**: Default `100.100.100.100` (replace with actual Tailscale IP)
- **Port**: Default `8080` (must match OpenClaw receiver)

## Usage

1. **Tap "Scan Voice Memos"** → App queries MediaStore for audio files
2. **Select memos** → Tap checkboxes next to each file
3. **Configure server** (optional) → Tap "Server Config" if needed
4. **Tap "Upload Selected"** → Starts multipart HTTP POST
5. **Check status** → Real-time status text shows upload progress

## Architecture

### `MainActivity.kt`
- Jetpack Compose UI with Material Design 3
- State management for memos, selections, upload status
- Permission request handling

### `VoiceMemo.kt`
- Data class representing audio metadata
- Fields: id, title, path, duration, dateAdded, size

### `MediaStoreRepository.kt`
- Queries MediaStore for audio files
- Filters by MIME type (audio/mpeg, audio/wav)
- Formatting utilities (duration, date)

### `UploadService.kt`
- OkHttp3 client with logging interceptor
- Multipart form upload
- Callback-based async handling
- 30s timeout on connections

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| androidx.core:core-ktx | 1.12.0 | Core Android |
| androidx.compose.ui:ui | Latest | UI Framework |
| androidx.compose.material3:material3 | 1.1.1 | Material Design |
| com.squareup.okhttp3:okhttp | 4.11.0 | HTTP Client |
| com.google.code.gson:gson | 2.10.1 | JSON parsing |
| com.google.accompanist:permissions | 0.33.2 | Permission helpers |

## Troubleshooting

### Build Fails: `ANDROID_HOME not found`
```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
```

### Upload Fails: Network Error
- Verify Tailscale is running and connected
- Check Tailscale IP address (use `tailscale status`)
- Ensure server is listening on the configured port
- Check firewall rules

### MediaStore Query Returns Empty
- Ensure app has `READ_MEDIA_AUDIO` permission (granted at runtime)
- Verify voice memos exist in device storage
- Try adding test files manually

### APK Installation Fails
- Uninstall previous version: `adb uninstall com.example.voicememouploader`
- Re-install: `adb install -r app-release.apk`

## Minification

ProGuard is configured for release builds. Keeps:
- OkHttp classes
- Gson classes
- Application classes
- Kotlin metadata

## Next Steps

- [ ] Add progress bar for upload
- [ ] Store upload history
- [ ] Background upload with WorkManager
- [ ] HTTPS/certificate pinning
- [ ] Offline queue with retry logic
- [ ] Notification on upload complete

## Support

For issues with:
- **Android SDK**: See Android Developers docs
- **Jetpack Compose**: See Android Compose docs
- **Tailscale**: See Tailscale docs
- **This app**: Check server logs and app status text

---

**Built with:** Kotlin, Jetpack Compose, OkHttp3
**Target API:** 34 (min 31)
**Build System:** Gradle 8.2
