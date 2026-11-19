package com.melodia.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.melodia.player.ui.MelodiaRoot
import com.melodia.player.ui.theme.MelodiaTheme

class MainActivity : ComponentActivity() {
    private val vm: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            val artColor by vm.artColor.collectAsStateWithLifecycle()
            MelodiaTheme(ui, artColor) {
                MelodiaRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { vm.playExternal(it) }
        }
    }
}
