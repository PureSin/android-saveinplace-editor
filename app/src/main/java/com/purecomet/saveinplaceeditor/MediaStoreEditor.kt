package com.purecomet.saveinplaceeditor

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

private const val TAG = "MediaStoreEditor"

object MediaStoreEditor {

    fun uriForId(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    fun overwrite(context: Context, id: Long, bitmap: Bitmap, usePending: Boolean = true): Result<Unit> {
        val uri = uriForId(id)
        Log.d(TAG, "overwrite: id=$id usePending=$usePending uri=$uri")
        return try {
            if (usePending) {
                val values = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 1) }
                context.contentResolver.update(uri, values, null, null)
                Log.d(TAG, "overwrite: IS_PENDING=1 set for id=$id")
            }
            try {
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                Log.d(TAG, "overwrite: stream=$stream")
                if (stream == null) throw Exception("openOutputStream returned null for $uri")
                stream.use {
                    val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                    Log.d(TAG, "overwrite: compress ok=$ok")
                    if (!ok) throw Exception("Bitmap.compress returned false")
                }
            } finally {
                if (usePending) {
                    val values = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    context.contentResolver.update(uri, values, null, null)
                    Log.d(TAG, "overwrite: IS_PENDING=0 set for id=$id")
                }
            }
            Log.d(TAG, "overwrite: success")
            Result.success(Unit)
        } catch (e: RecoverableSecurityException) {
            Log.w(TAG, "overwrite: RecoverableSecurityException: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "overwrite: exception ${e.javaClass.name}: ${e.message}", e)
            Result.failure(Exception("Failed to overwrite ID=$id: ${e.message}", e))
        }
    }
}
