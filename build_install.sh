#!/bin/bash

PROJECT_DIR="$HOME/MinimalBrowser"
cd "$PROJECT_DIR" || exit 1

echo "🧹 Cleaning project..."
./gradlew clean

echo "⚙️ Building debug APK..."
./gradlew assembleDebug --no-daemon

APK=$(find app/build/outputs/apk/debug -name "*.apk" | head -n 1)

if [ -z "$APK" ]; then
    echo "❌ APK not found!"
    exit 1
fi

echo "✅ APK built: $APK"

# Try adb install if available
if command -v adb >/dev/null 2>&1; then
    echo "📱 Installing via ADB..."
    adb install -r "$APK"
    echo "✅ Installed via ADB"
else
    echo "📦 ADB not found — trying Android package installer..."
    am start -a android.intent.action.VIEW -d "file://$APK" -t "application/vnd.android.package-archive"
    echo "👉 Install prompt opened"
fi

echo "🎉 Done!"
