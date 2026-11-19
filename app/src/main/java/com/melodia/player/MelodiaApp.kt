package com.melodia.player

import android.app.Application
import android.content.Context
import android.media.AudioManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.melodia.player.data.LibraryStore
import com.melodia.player.data.MusicRepository
import com.melodia.player.data.Settings
import com.melodia.player.data.VideoRepository

class MelodiaApp : Application(), ImageLoaderFactory {
    lateinit var repository: MusicRepository
        private set
    lateinit var videos: VideoRepository
        private set
    lateinit var library: LibraryStore
        private set
    lateinit var settings: Settings
        private set

    /** Wspólna sesja audio muzyki i filmów – dzięki temu equalizer działa na obu. */
    val audioSessionId: Int by lazy {
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
    }

    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository(this)
        videos = VideoRepository(this)
        library = LibraryStore(this)
        settings = Settings(this)
    }

    /** Coil potrafi też robić miniaturki z filmów. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
