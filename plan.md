# Android Save-In-Place Editor — Plan

## Goal

Minimal Android app to overwrite images in MediaStore by ID, triggered via ADB.
Default operation: replace the image with a 512×512 test card (black background, white date/time text centered).
Main activity shows a scrollable grid of all images in MediaStore with their IDs visible.

---

## ADB Interaction

```bash
# Overwrite image ID 42 with a 512x512 test card
adb shell am start -n com.kelvinma.saveinplace/.MainActivity --el media_store_id 42
```

---

## Architecture

```
app/src/main/
  java/com/kelvinma/saveinplace/
    MainActivity.kt          # Permission orchestration, hosts grid + status
    ImageGridAdapter.kt      # RecyclerView adapter for media grid
    MediaStoreEditor.kt      # Write logic: look up URI, open stream, write JPEG
    MediaStoreRepository.kt  # Query MediaStore for image list (ID, URI, name, date)
    TestCardGenerator.kt     # Canvas 512×512 test card with date/time
  res/
    layout/activity_main.xml     # Toolbar + RecyclerView grid + status bar
    layout/item_image_grid.xml   # Grid cell: thumbnail + ID overlay
  AndroidManifest.xml
```

---

## UI Layout

```
┌──────────────────────────────┐
│  [Save-In-Place Editor]      │  ← Toolbar
│  [Grant MANAGE_MEDIA] (if    │  ← Setup banner (hidden when granted)
│   not yet granted)           │
├──────────────────────────────┤
│  [img][img][img]             │  ← 3-column RecyclerView grid
│  [id ] [id ] [id ]           │    Each cell: thumbnail + MediaStore ID overlay
│  [img][img][img]             │
│  ...                         │
├──────────────────────────────┤
│  Status: Written ID=42 ✓     │  ← Status bar at bottom
└──────────────────────────────┘
```

Grid cells show thumbnail + MediaStore `_ID` as a small overlay label.
Tapping a cell copies the ID to clipboard for convenient use with ADB.

---

## Permission Strategy (Android 11+ only, minSdk 30)

| Android | Read Permission | Write Strategy |
|---|---|---|
| 13+ (API 33+) | `READ_MEDIA_IMAGES` | `MANAGE_MEDIA` (no dialogs) or `createWriteRequest` fallback |
| 11–12 (API 30–32) | `READ_EXTERNAL_STORAGE` | `MANAGE_MEDIA` (no dialogs) or `createWriteRequest` fallback |

- `MANAGE_MEDIA` is a special app access granted via Settings. The app shows a setup banner
  with a button directing the user to grant it. Once granted, ADB triggers work silently with
  no system dialogs.
- If `MANAGE_MEDIA` is not granted, the app falls back to `MediaStore.createWriteRequest`,
  which shows a one-time system confirmation dialog per batch.

---

## Core Write Flow

1. Extract `media_store_id` (Long) from incoming intent extras
2. Check `MANAGE_MEDIA` is granted — if not, show error in status bar
3. Build URI: `ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)`
4. Set `IS_PENDING = 1` via `contentResolver.update`
5. Generate 512×512 test card bitmap (`TestCardGenerator`)
6. Open `contentResolver.openOutputStream(uri, "wt")` and write JPEG bytes
7. Set `IS_PENDING = 0` via `contentResolver.update`
8. Update status bar: `"Written: ID=42 at 2026-03-08 14:22:01"`
9. Reload grid from MediaStore

---

## Test Card Design (512×512)

- Black background
- Thin color bar strip at top edge: red / green / blue / yellow bands
- Large white text centered: date (`2026-03-08`) on first line, time (`14:22:01`) on second line
- Font: monospace, bold

---

## Build Setup

- Language: Kotlin
- UI: ViewBinding
- `minSdk 30` (Android 11), `targetSdk 35`
- Image loading: **Coil** (thumbnail loading in grid)
- No other external dependencies

---

## Implementation Notes

- UI is Jetpack Compose (not ViewBinding as originally planned)
- Package: `com.purecomet.saveinplaceeditor` (not `com.kelvinma.saveinplace`)
- No separate layout XML files or RecyclerView adapter — all UI in Compose

---

## Task Breakdown

### T1 — Project Scaffolding ✅
- [x] `settings.gradle.kts`, root and app `build.gradle.kts`
- [x] `app/build.gradle.kts` (Kotlin + Compose + Coil, minSdk 30)
- [x] `AndroidManifest.xml` — `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`, `MANAGE_MEDIA`, `READ_MEDIA_VISUAL_USER_SELECTED`
- [x] `res/values/strings.xml`

### T2 — TestCardGenerator ✅
- [x] Implement `TestCardGenerator.kt`
  - Creates a 512×512 `Bitmap`
  - Black background
  - Color bar strip at top (red/green/blue/yellow, each 1/4 width, ~32px tall)
  - Centered white bold monospace text with current date + time (two lines)
  - Returns `Bitmap`

### T3 — MediaStoreRepository ✅
- [x] `MediaStoreRepository.kt`
  - Data class `MediaImage(id, uri, displayName, dateTaken)`
  - `suspend fun queryImages(context)` — queries `EXTERNAL_CONTENT_URI`, sorted `DATE_TAKEN DESC`

### T4 — MediaStoreEditor ✅
- [x] Implement `MediaStoreEditor.kt`
  - `fun overwrite(context: Context, id: Long, bitmap: Bitmap): Result<Unit>`
  - Builds content URI from ID
  - Sets `IS_PENDING = 1`
  - Opens output stream with mode `"wt"`, writes JPEG (quality 95)
  - Sets `IS_PENDING = 0`
  - Returns `Result.success` or `Result.failure` with descriptive error

### T5 — Image Grid UI ✅
- [x] `LazyVerticalGrid` (3 columns) in `MainScreen` composable
- [x] `ImageGridCell` composable: Coil `AsyncImage` (centerCrop) + ID overlay (bottom-left, semi-transparent)
- [x] Tap copies MediaStore ID to clipboard + shows toast

### T6 — Permission Handling ✅
- [x] Requests `READ_MEDIA_IMAGES` (API 33+) or `READ_EXTERNAL_STORAGE` (API 30–32) on launch
- [x] "Grant Permission" button shown when permission denied

### T7 — Main Activity Layout ✅
- [x] `TopAppBar` with title "Save-In-Place Editor"
- [x] Grid fills remaining space
- [x] MANAGE_MEDIA banner (shown when `canManageMedia` is false)
- [x] Status bar `TextView` at bottom for write results

### T8 — ADB Intent Handling ✅
- [x] Handle `media_store_id` Long extra in `onCreate`/`onNewIntent`
- [x] Call `MediaStoreEditor.overwrite` + `TestCardGenerator.generate()`
- [x] Show result in status bar: `"Written: ID=42 at 2026-03-08 14:22:01"` or error
- [x] Reload grid after overwrite

### T9 — Manual Testing Checklist
- [x] Build and install: `./gradlew installDebug`
- [x] Grant storage permission via in-app button
- [x] Verify image grid loads and IDs are visible
- [x] Tap a grid cell → confirm ID is copied to clipboard
- [x] Grant MANAGE_MEDIA via in-app banner
- [x] Run ADB command with a valid ID → confirm image is overwritten with test card
- [x] Verify grid refreshes after overwrite
- [ ] Run ADB command with an invalid ID → confirm error shown in status bar

### T10 — Coil Cache Invalidation Fix ✅
- [x] After a successful overwrite, invalidate Coil memory and disk cache for the written URI
  before re-querying MediaStore — prevents stale thumbnails since the URI is unchanged
- [x] Added `invalidateCoilCache(context, id)` helper using `context.imageLoader.memoryCache`
  and `context.imageLoader.diskCache`
- [x] Called in both write paths: ADB-triggered and post-permission-dialog retry
