#!/usr/bin/env bash
set -x
export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.13.102-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/c/Users/lain/AppData/Local/Android/Sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
cd /c/Users/lain/Documents/FemboyGram/FemboyGram/tg-src || exit 1
bash ./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone --no-daemon --stacktrace 2>&1
echo "BUILD_EXIT=$?"
