package com.melodia.player.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Skanuje bibliotekę muzyczną urządzenia przez MediaStore. */
class MusicRepository(private val context: Context) {

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val result = ArrayList<Song>()
        val genreColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.Audio.AudioColumns.GENRE)
        }.toTypedArray()
        val legacyGenres = if (genreColumn) emptyMap() else loadLegacyGenres()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val iGenre = if (genreColumn) c.getColumnIndex("genre") else -1
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val path = c.getString(iData) ?: ""
                    val title = c.getString(iTitle)?.takeIf { it.isNotBlank() }
                        ?: c.getString(iName)?.substringBeforeLast('.')
                        ?: "Bez tytułu"
                    val artist = c.getString(iArtist)
                        ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                        ?: "Nieznany wykonawca"
                    val album = c.getString(iAlbum)
                        ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                        ?: "Nieznany album"
                    val genre = (if (iGenre >= 0) c.getString(iGenre) else legacyGenres[id])
                        ?.takeIf { it != MediaStore.UNKNOWN_STRING }
                        .orEmpty()
                    result += Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = c.getLong(iAlbumId),
                        duration = c.getLong(iDur),
                        track = c.getInt(iTrack) % 1000,
                        year = c.getInt(iYear),
                        dateAdded = c.getLong(iDate),
                        folder = path.substringBeforeLast('/', ""),
                        path = path,
                        genre = genre,
                    )
                }
            }
        } catch (_: SecurityException) {
            // brak uprawnień – zwracamy pustą listę
        }
        result
    }

    /** Android 8–10: gatunki są w osobnej tabeli. */
    @Suppress("DEPRECATION")
    private fun loadLegacyGenres(): Map<Long, String> {
        val map = HashMap<Long, String>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null, null, null
            )?.use { g ->
                while (g.moveToNext()) {
                    val genreId = g.getLong(0)
                    val name = g.getString(1)
                    if (name.isNullOrBlank()) continue
                    context.contentResolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null, null, null
                    )?.use { m ->
                        while (m.moveToNext()) map[m.getLong(0)] = name
                    }
                }
            }
        }
        return map
    }
}
