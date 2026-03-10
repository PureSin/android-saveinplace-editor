# Verification: ADB Overwrite of Image ID 20

## Goal
Run the ADB command targeting ID 20 and confirm the image is replaced with the 512×512 test card.

---

## Prerequisites

```bash
export ANDROID_HOME=/Volumes/HubSSD/Library/Android/sdk
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

---

## Step 1 — Confirm app is running and MANAGE_MEDIA is granted

```bash
adb shell dumpsys package com.purecomet.saveinplaceeditor | grep -A2 MANAGE_MEDIA
```

Expected: `android.permission.MANAGE_MEDIA: granted=true`

Also visually: open the app and confirm the green "MANAGE_MEDIA permission granted" banner is visible.

---

## Step 2 — Capture the BEFORE state of image ID 20

Pull the original image from the device for comparison:

```bash
adb shell content query --uri content://media/external/images/media/20 --projection _data
# Note the file path, e.g. /storage/emulated/0/Pictures/foo.jpg

adb pull /storage/emulated/0/Pictures/foo.jpg /tmp/before_id20.jpg
open /tmp/before_id20.jpg
```

---

## Step 3 — Clear logcat, then run the ADB command

```bash
adb logcat -c

adb shell am start -n com.purecomet.saveinplaceeditor/.MainActivity --el media_store_id 20
```

---

## Step 4 — Capture logcat output

```bash
sleep 3
adb logcat -d 2>&1 | python3 -c "
import sys
lines = sys.stdin.readlines()
relevant = [l for l in lines if any(x in l for x in ['MediaStoreEditor', 'D MainActivity', 'E MainActivity', 'AndroidRuntime', 'FATAL'])]
print(''.join(relevant))
"
```

Expected log sequence:
```
D MainActivity: pendingMediaId=20, canManageMedia=true
D MediaStoreEditor: overwrite: opening stream for content://media/external/images/media/20
D MediaStoreEditor: overwrite: stream=...
D MediaStoreEditor: overwrite: compress ok=true
D MediaStoreEditor: overwrite: success
D MainActivity: overwrite result: Success(kotlin.Unit)
```

---

## Step 5 — Check the app status bar

```bash
adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png /tmp/screen.png
open /tmp/screen.png
```

Expected: status bar at bottom shows `Status: Written: ID=20 at 2026-03-08 HH:MM:SS`

---

## Step 6 — Pull and inspect the AFTER image

```bash
adb pull /storage/emulated/0/Pictures/foo.jpg /tmp/after_id20.jpg
open /tmp/after_id20.jpg
```

Expected: 512×512 test card with black background, color bar strip, and date/time text.

---

## Current Issue

`openOutputStream` throws `RecoverableSecurityException` even with `canManageMedia=true`.
Logcat stops after the exception — no further MainActivity logs — suggesting a silent coroutine crash.

Suspect: `RecoverableSecurityException` is caught in `MediaStoreEditor` but something in the
`result.fold` path in `MainActivity` is crashing before the log lines fire. Need to check for
`AndroidRuntime` / `FATAL` in logcat from the app PID.
