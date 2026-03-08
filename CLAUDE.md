# Save-In-Place Editor — Claude Code Guide

## Build Instructions

### Prerequisites

Set these environment variables before running any Gradle commands:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/Volumes/HubSSD/Library/Android/sdk
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$ANDROID_HOME/cmdline-tools/latest/bin:$JAVA_HOME/bin:$PATH"
```

The `local.properties` file already points the SDK to `/Volumes/HubSSD/Library/Android/sdk`.

### Common Commands

```bash
# Build debug APK
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Volumes/HubSSD/Library/Android/sdk ./gradlew assembleDebug

# Install to connected device
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Volumes/HubSSD/Library/Android/sdk ./gradlew installDebug

# Run unit tests
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Volumes/HubSSD/Library/Android/sdk ./gradlew test

# Clean build
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Volumes/HubSSD/Library/Android/sdk ./gradlew clean assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### ADB Usage

```bash
# Overwrite image ID 42 with a 512x512 test card
adb shell am start -n com.kelvinma.saveinplace/.MainActivity --el media_store_id 42
```

## Project Structure

```
app/src/main/java/com/purecomet/saveinplaceeditor/
  MainActivity.kt        # Entry point (Compose-based, being migrated per plan.md)
  ui/theme/              # Material3 theme files

app/src/main/res/
  values/strings.xml     # App name: "SaveInPlace Editor"
  values/themes.xml
  values/colors.xml
```

## Key Config

- **Package**: `com.purecomet.saveinplaceeditor`
- **minSdk**: 30 (Android 11)
- **targetSdk**: 36
- **Language**: Kotlin + Jetpack Compose
- **Java**: OpenJDK 17 (`/opt/homebrew/opt/openjdk@17`)
- **Android SDK**: `/Volumes/HubSSD/Library/Android/sdk` (build-tools 36.1.0, platform android-36.1)
