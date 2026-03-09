package com.purecomet.saveinplaceeditor

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

private const val TAG = "MediaStoreEditor"

object MediaStoreEditor {

    fun uriForId(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    fun overwrite(context: Context, id: Long, bitmap: Bitmap): Result<Unit> {
        val uri = uriForId(id)
        Log.d(TAG, "overwrite: opening stream for $uri")
        return try {
            val stream = context.contentResolver.openOutputStream(uri, "wt")
            Log.d(TAG, "overwrite: stream=$stream")
            if (stream == null) throw Exception("openOutputStream returned null for $uri")
            stream.use {
                val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                Log.d(TAG, "overwrite: compress ok=$ok")
                if (!ok) throw Exception("Bitmap.compress returned false")
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
