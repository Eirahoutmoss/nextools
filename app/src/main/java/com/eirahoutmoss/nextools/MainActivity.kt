package com.eirahoutmoss.nextools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.ui.NexToolsRoot
import com.eirahoutmoss.nextools.ui.components.SplashOverlay
import com.eirahoutmoss.nextools.ui.theme.NexToolsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NexToolsApp).container
        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            NexToolsTheme(mode = settings.theme) {
                // Açılış ekranı yalnızca uygulama soğuk başlarken; ekran dönünce yeniden çıkmaz.
                var splash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                Box(Modifier.fillMaxSize()) {
                    NexToolsRoot()
                    if (splash) SplashOverlay(onFinished = { splash = false })
                }
            }
        }
    }
}
