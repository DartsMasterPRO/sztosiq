package com.melodia.player

import android.app.Application
import com.melodia.player.data.LibraryStore
import com.melodia.player.data.MusicRepository
import com.melodia.player.data.Settings

class MelodiaApp : Application() {
    lateinit var repository: MusicRepository
        private set
    lateinit var library: LibraryStore
        private set
    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository(this)
        library = LibraryStore(this)
        settings = Settings(this)
    }
}
