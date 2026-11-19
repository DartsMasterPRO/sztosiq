package com.melodia.player.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val track: Int,
    val year: Int,
    val dateAdded: Long,
    val folder: String,
    val path: String,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val artUri: Uri get() = ContentUris.withAppendedId(ALBUM_ART_URI, albumId)
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
) {
    val artUri: Uri get() = ContentUris.withAppendedId(ALBUM_ART_URI, id)
    val duration: Long get() = songs.sumOf { it.duration }
}

data class Artist(val name: String, val songs: List<Song>) {
    val albumCount: Int get() = songs.map { it.albumId }.distinct().size
    val artUri: Uri? get() = songs.firstOrNull()?.artUri
}

data class Folder(val path: String, val songs: List<Song>) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/').ifBlank { path }
}

data class Playlist(val id: Long, val name: String, val songIds: List<Long>)

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setAlbumArtist(artist)
            .setTrackNumber(track)
            .setArtworkUri(artUri)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
    )
    .build()

// ---------- Sortowanie i grupowanie ----------

fun plCollator(): Collator =
    Collator.getInstance(Locale.forLanguageTag("pl-PL")).apply { strength = Collator.PRIMARY }

enum class SongSort(val label: String) {
    TITLE("Tytuł"),
    ARTIST("Wykonawca"),
    ALBUM("Album"),
    DATE_ADDED("Ostatnio dodane"),
    DURATION("Długość");

    fun sort(list: List<Song>): List<Song> {
        val c = plCollator()
        return when (this) {
            TITLE -> list.sortedWith(compareBy(c) { it.title })
            ARTIST -> list.sortedWith(compareBy<Song, String>(c) { it.artist }.thenBy(c) { it.title })
            ALBUM -> list.sortedWith(compareBy<Song, String>(c) { it.album }.thenBy { it.track })
            DATE_ADDED -> list.sortedByDescending { it.dateAdded }
            DURATION -> list.sortedByDescending { it.duration }
        }
    }
}

fun groupAlbums(songs: List<Song>): List<Album> {
    val c = plCollator()
    return songs.groupBy { it.albumId }.map { (id, list) ->
        Album(
            id = id,
            title = list.first().album,
            artist = list.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: "",
            year = list.maxOf { it.year },
            songs = list.sortedWith(compareBy<Song> { it.track }.thenBy(c) { it.title }),
        )
    }.sortedWith(compareBy(c) { it.title })
}

fun groupArtists(songs: List<Song>): List<Artist> {
    val c = plCollator()
    return songs.groupBy { it.artist }.map { (name, list) ->
        Artist(name, list.sortedWith(compareBy<Song, String>(c) { it.album }.thenBy { it.track }))
    }.sortedWith(compareBy(c) { it.name })
}

fun groupFolders(songs: List<Song>): List<Folder> {
    val c = plCollator()
    return songs.groupBy { it.folder }.map { (path, list) ->
        Folder(path, list.sortedWith(compareBy(c) { it.title }))
    }.sortedWith(compareBy(c) { it.name })
}

private val DIACRITICS = Regex("\\p{Mn}+")

/** Wyszukiwanie bez rozróżniania wielkości liter i polskich znaków ("zolw" znajdzie "Żółw"). */
fun String.normalizeForSearch(): String =
    Normalizer.normalize(lowercase().replace('ł', 'l'), Normalizer.Form.NFD).replace(DIACRITICS, "")

fun songsLabel(n: Int): String {
    val word = when {
        n == 1 -> "utwór"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "utwory"
        else -> "utworów"
    }
    return "$n $word"
}
