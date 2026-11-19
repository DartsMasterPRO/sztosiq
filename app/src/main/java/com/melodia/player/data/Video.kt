package com.melodia.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Video(
    val id: Long,
    val title: String,
    val duration: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateAdded: Long,
    val folder: String,
    val path: String,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
    val folderName: String get() = folder.trimEnd('/').substringAfterLast('/').ifBlank { "Pamięć" }
    val mediaId: String get() = "v:$id"

    /** "4K", "1080p", "720p"… */
    val quality: String?
        get() {
            val short = minOf(width, height).takeIf { it > 0 } ?: return null
            return when {
                short >= 2160 -> "4K"
                short >= 1440 -> "1440p"
                short >= 1080 -> "1080p"
                short >= 720 -> "720p"
                else -> "${short}p"
            }
        }
}

enum class VideoSort(val label: String) {
    DATE("Najnowsze"),
    NAME("Nazwa"),
    DURATION("Długość"),
    SIZE("Rozmiar");

    fun sort(list: List<Video>): List<Video> {
        val c = plCollator()
        return when (this) {
            DATE -> list.sortedByDescending { it.dateAdded }
            NAME -> list.sortedWith(compareBy(c) { it.title })
            DURATION -> list.sortedByDescending { it.duration }
            SIZE -> list.sortedByDescending { it.size }
        }
    }
}

class VideoRepository(private val context: Context) {

    suspend fun loadVideos(): List<Video> = withContext(Dispatchers.IO) {
        val result = ArrayList<Video>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATA,
        )
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, null
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val iW = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val iData = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                while (c.moveToNext()) {
                    val path = c.getString(iData).orEmpty()
                    result += Video(
                        id = c.getLong(iId),
                        title = c.getString(iTitle)?.takeIf { it.isNotBlank() }
                            ?: c.getString(iName)?.substringBeforeLast('.')
                            ?: "Film",
                        duration = c.getLong(iDur),
                        size = c.getLong(iSize),
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        dateAdded = c.getLong(iDate),
                        folder = path.substringBeforeLast('/', ""),
                        path = path,
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        result
    }
}
