package com.purecomet.saveinplaceeditor

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaImage(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateTaken: Long,
    val generationModified: Long,
)

object MediaStoreRepository {
    suspend fun queryImages(context: Context): List<MediaImage> = withContext(Dispatchers.IO) {
        val images = mutableListOf<MediaImage>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.GENERATION_MODIFIED,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val genCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.GENERATION_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(collection, id)
                val name = cursor.getString(nameCol) ?: ""
                val date = cursor.getLong(dateCol)
                val gen = cursor.getLong(genCol)
                images += MediaImage(id, uri, name, date, gen)
            }
        }
        images
    }

    suspend fun queryGenerationModified(context: Context, id: Long): Long = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        val projection = arrayOf(MediaStore.Images.Media.GENERATION_MODIFIED)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        } ?: 0L
    }
}
