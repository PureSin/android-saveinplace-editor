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

## Task Breakdown

### T1 — Project Scaffolding
- [ ] Create `settings.gradle.kts`
- [ ] Create root `build.gradle.kts`
- [ ] Create `app/build.gradle.kts` (Kotlin, ViewBinding, Coil dep, minSdk 30)
- [ ] Create `app/src/main/AndroidManifest.xml` with all permissions and intent filters
- [ ] Create `app/src/main/res/values/strings.xml`

### T2 — TestCardGenerator
- [ ] Implement `TestCardGenerator.kt`
  - Creates a 512×512 `Bitmap`
  - Black background
  - Color bar strip at top (red/green/blue/yellow, each 1/4 width, ~32px tall)
  - Centered white bold monospace text with current date + time (two lines)
  - Returns `Bitmap`

### T3 — MediaStoreRepository
- [ ] Implement `MediaStoreRepository.kt`
  - Data class `MediaImage(id: Long, uri: Uri, displayName: String, dateTaken: Long)`
  - `fun queryImages(context: Context): List<MediaImage>` — queries
    `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`, sorted by `DATE_TAKEN DESC`
  - Projects: `_ID`, `DISPLAY_NAME`, `DATE_TAKEN`

### T4 — MediaStoreEditor
- [ ] Implement `MediaStoreEditor.kt`
  - `fun overwrite(context: Context, id: Long, bitmap: Bitmap): Result<Unit>`
  - Builds content URI from ID
  - Sets `IS_PENDING = 1`
  - Opens output stream with mode `"wt"`, writes JPEG (quality 95)
  - Sets `IS_PENDING = 0`
  - Returns `Result.success` or `Result.failure` with descriptive error

### T5 — ImageGridAdapter
- [ ] Implement `ImageGridAdapter.kt`
  - `RecyclerView.Adapter` with `ListAdapter<MediaImage, ...>` + `DiffUtil`
  - Loads thumbnail via Coil into `ImageView`
  - Overlays MediaStore ID as small text in bottom-left corner of each cell
  - `onItemClick: (MediaImage) -> Unit` callback

### T6 — Grid Item Layout
- [ ] Create `app/src/main/res/layout/item_image_grid.xml`
  - `FrameLayout` root (square, match parent width)
  - `ImageView` filling the frame (scaleType: centerCrop)
  - `TextView` for ID overlay (bottom-left, semi-transparent dark background, white text, small font)

### T7 — Main Activity Layout
- [ ] Create `app/src/main/res/layout/activity_main.xml`
  - `LinearLayout` (vertical)
  - `MaterialToolbar` at top: title "Save-In-Place Editor"
  - `Button` for "Grant MANAGE_MEDIA" (visibility toggled at runtime)
  - `RecyclerView` (weight=1, fills remaining space)
  - `TextView` status bar at bottom (single line, monospace)

### T8 — MainActivity
- [ ] Implement `MainActivity.kt`
  - On `onCreate`/`onNewIntent`: extract `media_store_id` from intent if present
  - Permission check on resume:
    - Android 13+: request `READ_MEDIA_IMAGES` if not granted
    - Android 11–12: request `READ_EXTERNAL_STORAGE` if not granted
    - Show/hide MANAGE_MEDIA banner based on `MediaStore.canManageMedia(context)`
  - Load image grid via `MediaStoreRepository` on a coroutine (IO dispatcher)
  - Submit list to `ImageGridAdapter`
  - Grid item click: copy ID to clipboard + show toast `"ID 42 copied"`
  - If `media_store_id` intent extra present:
    - Call `MediaStoreEditor.overwrite` with `TestCardGenerator.generate()` result
    - Update status bar with result message
    - Reload grid

### T9 — Manual Testing Checklist
- [ ] Build and install: `./gradlew installDebug`
- [ ] Grant MANAGE_MEDIA via in-app button
- [ ] Verify image grid loads and IDs are visible
- [ ] Tap a grid cell → confirm ID is copied to clipboard
- [ ] Run ADB command with a valid ID → confirm image is overwritten with test card
- [ ] Verify grid refreshes after overwrite
- [ ] Run ADB command with an invalid ID → confirm error shown in status bar
