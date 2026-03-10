# Bug: LaunchedEffect Self-Cancellation via Key Mutation

**Date:** 2026-03-08
**File:** `MainActivity.kt` — `LaunchedEffect(pendingMediaId)` block
**Symptom:** Silent coroutine crash; logcat cut off mid-sequence with no FATAL or AndroidRuntime error

---

## Symptom

Running the ADB command to trigger an overwrite:

```bash
adb shell am start -n com.purecomet.saveinplaceeditor/.MainActivity --el media_store_id 20
```

Logcat would show:

```
D MainActivity: pendingMediaId=20, canManageMedia=true
D MediaStoreEditor: overwrite: opening stream for content://media/external/images/media/20
W MediaStoreEditor: overwrite: RecoverableSecurityException: com.purecomet.saveinplaceeditor has no access to ...
```

...and then nothing. No `overwrite result:` log. No `AndroidRuntime` / `FATAL` crash. The app appeared alive but the status bar never updated and the write request dialog never appeared.

---

## Root Cause

The `LaunchedEffect` was using `pendingMediaId` as its key, and `onIdHandled()` — which sets `pendingMediaId = null` — was called *before* the IO work started:

```kotlin
// BUGGY CODE
LaunchedEffect(pendingMediaId) {          // key = 20
    val id = pendingMediaId ?: return@LaunchedEffect
    onIdHandled()                          // ← sets pendingMediaId = null  <-- BUG
    statusMessage = "Writing ID=$id…"
    val result = withContext(Dispatchers.IO) {
        MediaStoreEditor.overwrite(context, id, TestCardGenerator.generate())
    }
    Log.d("MainActivity", "overwrite result: $result")  // never reached
    result.fold(...)
}
```

**What actually happened, step by step:**

1. Intent arrives → `pendingMediaId.value = 20`
2. `LaunchedEffect(20)` starts on the Main dispatcher
3. `onIdHandled()` is called → `pendingMediaId.value = null`
4. Compose schedules a recomposition — the `LaunchedEffect` key has changed from `20` to `null`
5. `withContext(Dispatchers.IO)` suspends the coroutine and dispatches IO work
6. IO thread runs `MediaStoreEditor.overwrite()` — logs appear from the IO thread ✓
7. `MediaStoreEditor` catches the `RecoverableSecurityException`, returns `Result.failure(rse)` — its log appears ✓
8. IO work is done; the runtime tries to **resume the Main-thread coroutine**
9. Compose has already **cancelled** the `LaunchedEffect(20)` coroutine (because the key changed to `null`)
10. A `CancellationException` is thrown at the `withContext` suspension point — swallowed silently
11. `LaunchedEffect(null)` starts, hits `?: return@LaunchedEffect`, exits immediately

This is why the MediaStoreEditor logs appeared (IO thread, already running) but nothing after `withContext` ever fired.

### Why it was hard to diagnose

- No `AndroidRuntime` / `FATAL` in logcat — `CancellationException` is structured concurrency's normal cancellation mechanism, not a crash
- The IO work *did* complete, so the MediaStoreEditor logs gave a false impression the code was running normally
- `canManageMedia=true` was a red herring — the real issue was the coroutine never reaching the `result.fold` branch

---

## Fix

Move `onIdHandled()` to *after* the IO work completes. The LaunchedEffect key stays `20` for the entire duration of the coroutine, so Compose never cancels it mid-flight. Once the work is done and `onIdHandled()` clears the id to `null`, `LaunchedEffect(null)` fires and immediately returns via the guard.

```kotlin
// FIXED CODE
LaunchedEffect(pendingMediaId) {
    val id = pendingMediaId ?: return@LaunchedEffect
    // NOTE: onIdHandled() must NOT be called before the IO work — changing pendingMediaId
    // here would change the LaunchedEffect key, cancelling this coroutine mid-flight.
    Log.d("MainActivity", "pendingMediaId=$id, canManageMedia=$canManageMedia")
    statusMessage = "Writing ID=$id…"
    val result = withContext(Dispatchers.IO) {
        MediaStoreEditor.overwrite(context, id, TestCardGenerator.generate())
    }
    onIdHandled()                          // ← moved here, AFTER IO work
    Log.d("MainActivity", "overwrite result: $result")
    result.fold(...)
}
```

### After the fix — complete log sequence

```
D MainActivity: pendingMediaId=20, canManageMedia=true
D MediaStoreEditor: overwrite: opening stream for content://media/external/images/media/20
W MediaStoreEditor: overwrite: RecoverableSecurityException: ...
D MainActivity: overwrite result: Failure(android.app.RecoverableSecurityException: ...)
E MainActivity: overwrite failed: android.app.RecoverableSecurityException: ...
D MainActivity: cause: android.app.RecoverableSecurityException
D MainActivity: launching createWriteRequest
→ com.android.providers.media.PermissionActivity launched ✓
```

The `createWriteRequest` permission dialog appeared on the device as expected.

---

## Secondary Finding: MANAGE_MEDIA Permission State Discrepancy

During diagnosis, `adb shell dumpsys package` showed:

```
android.permission.MANAGE_MEDIA: granted=false, flags=[ USER_SET]
```

Yet the app logged `canManageMedia=true` because `MediaStore.canManageMedia(context)` queries the MediaStore provider directly and can return a different result than the package manager flag. These two sources of truth can diverge — don't rely solely on one.

When `MANAGE_MEDIA` is not truly granted, `openOutputStream` throws `RecoverableSecurityException`. The correct fallback (which this bug was preventing from running) is `MediaStore.createWriteRequest()`, which shows the system permission dialog for one-time write access.

---

## General Rule

**Never mutate a `LaunchedEffect`'s key state from within that same `LaunchedEffect`**, especially before a suspension point. Changing the key causes Compose to cancel the current coroutine and restart the effect — if you're suspended at `withContext` when this happens, the cancellation is silent and the code after `withContext` never runs.

If you need to clear a trigger value, do it *after* all suspended work is complete.
