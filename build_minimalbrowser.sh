#!/bin/bash

export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk
export PATH=$JAVA_HOME/bin:$PATH

echo "JAVA_HOME=$JAVA_HOME"
java -version

cd ~/MinimalBrowser || exit 1

echo "Cleaning..."
./gradlew clean

echo "Building APK..."
./gradlew assembleDebug

echo "Searching APK..."
find app/build/outputs/apk/debug -name "*.apk"
