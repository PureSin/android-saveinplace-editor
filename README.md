# Save-In-Place Editor

An Android utility that overwrites images in the device's MediaStore by their database ID without changing their URI or file path. Primarily triggered via ADB, it's useful for testing image processing pipelines and gallery refresh behavior.

## What It Does

- Displays all device images in a 3-column grid with their MediaStore IDs overlaid
- Overwrites any image with a 512×512 test card (color bars + timestamp) via ADB
- The overwritten image keeps its original MediaStore ID and URI — no copies, no moves

## ADB Usage

```bash
# Overwrite image with MediaStore ID 42
adb shell am start -n com.purecomet.saveinplaceeditor/.MainActivity --el media_store_id 42
```

Tap any image in the grid to copy its ID to the clipboard.

## Test Card Format

The generated test card is a 512×512 JPEG containing:
- Color bar strip at top (red, green, blue, yellow bands)
- Current date and time centered in white monospace text

## Setup

### Prerequisites

| Tool | Version |
|------|---------|
| Java | OpenJDK 17 |
| Android SDK | `/Volumes/HubSSD/Library/Android/sdk` |
| Build Tools | 36.1.0 |
| Min SDK | 30 (Android 11) |

### Build & Install

```bash
export JAVA_HOME=<path>
export ANDROID_HOME=<android path>

./gradlew installDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### Launch

```bash
# Open the app
adb shell am start -n com.purecomet.saveinplaceeditor/.MainActivity
```

## Permissions

| Permission | Purpose |
|-----------|---------|
| `READ_MEDIA_IMAGES` | Read image gallery (Android 13+) |
| `READ_EXTERNAL_STORAGE` | Read image gallery (Android 11–12) |
| `MANAGE_MEDIA` | Write images without per-file dialogs |

If `MANAGE_MEDIA` is not granted, the app falls back to the system `MediaStore.createWriteRequest()` dialog.

## Project Structure

```
app/src/main/java/com/purecomet/saveinplaceeditor/
  MainActivity.kt           # Compose UI, permission handling, intent dispatch
  MediaStoreRepository.kt   # Queries device images from MediaStore
  MediaStoreEditor.kt       # Overwrites an image by MediaStore ID
  TestCardGenerator.kt      # Generates the 512×512 test card bitmap
  ui/theme/                 # Material3 theme
```

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material3
- **Image loading:** Coil
- **Min SDK:** 30 (Android 11)
- **Target SDK:** 36
