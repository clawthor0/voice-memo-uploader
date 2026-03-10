#!/bin/bash
# Build script for Voice Memo Uploader Android App

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"

echo "🔨 Building Voice Memo Uploader APK..."
echo "Project directory: $PROJECT_DIR"

# Check for Android SDK
if [ -z "$ANDROID_HOME" ]; then
    echo "❌ ANDROID_HOME is not set"
    echo "Please set ANDROID_HOME to your Android SDK installation directory"
    exit 1
fi

echo "✓ Using Android SDK at: $ANDROID_HOME"

# Clean previous builds
echo "🧹 Cleaning previous builds..."
cd "$PROJECT_DIR"
./gradlew clean 2>/dev/null || true

# Build release APK
echo "📦 Building release APK..."
./gradlew assembleRelease

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"

if [ -f "$APK_PATH" ]; then
    echo ""
    echo "✅ Build successful!"
    echo "APK location: $APK_PATH"
    echo "APK size: $(du -h "$APK_PATH" | cut -f1)"
    echo ""
    echo "To install on device:"
    echo "  adb install -r \"$APK_PATH\""
else
    echo "❌ Build failed - APK not found"
    exit 1
fi
