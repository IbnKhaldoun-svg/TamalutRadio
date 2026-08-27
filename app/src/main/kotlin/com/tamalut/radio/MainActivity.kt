package com.tamalut.radio

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.tamalut.radio.core.designsystem.TamalutRadioTheme
import com.tamalut.radio.core.designsystem.ThemeMode
import com.tamalut.radio.core.playback.PlaybackTestCatalog
import com.tamalut.radio.core.playback.TamalutPlaybackService
import com.tamalut.radio.core.preferences.DataStoreUserPreferencesRepository
import com.tamalut.radio.core.preferences.ThemePreference
import com.tamalut.radio.core.preferences.UserPreferences
import java.util.concurrent.Executor

class MainActivity : ComponentActivity() {
    private val preferencesRepository by lazy {
        DataStoreUserPreferencesRepository(applicationContext)
    }
    private var testMediaBrowser: MediaBrowser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val userPreferences by preferencesRepository.userPreferences.collectAsState(
                initial = UserPreferences(),
            )

            TamalutRadioTheme(
                themeMode = userPreferences.themePreference.toThemeMode(),
            ) {
                TamalutRadioPlaceholder(
                    onPrepareRadioAzawan = ::prepareRadioAzawanForSystemControls,
                )
            }
        }
    }

    private fun prepareRadioAzawanForSystemControls(onResult: (String) -> Unit) {
        val sessionToken = SessionToken(
            this,
            ComponentName(this, TamalutPlaybackService::class.java),
        )
        val browserFuture = MediaBrowser.Builder(this, sessionToken).buildAsync()
        browserFuture.addListener(
            {
                runCatching {
                    val browser = browserFuture.get()
                    testMediaBrowser?.release()
                    testMediaBrowser = browser
                    browser.pause()
                    browser.setMediaItem(
                        MediaItem.Builder()
                            .setMediaId(PlaybackTestCatalog.RADIO_AZAWAN_MEDIA_ID)
                            .build(),
                    )
                    browser.prepare()
                }.onSuccess {
                    onResult("Radio Azawan pronta. Premi Play dalla notifica o dalla lock screen.")
                }.onFailure { error ->
                    onResult("Impossibile preparare Radio Azawan: ${error.message ?: "errore sconosciuto"}")
                }
            },
            Executor { runnable -> runOnUiThread(runnable) },
        )
    }

    override fun onDestroy() {
        testMediaBrowser?.release()
        testMediaBrowser = null
        super.onDestroy()
    }
}

private fun ThemePreference.toThemeMode(): ThemeMode = when (this) {
    ThemePreference.FOLLOW_SYSTEM -> ThemeMode.FOLLOW_SYSTEM
    ThemePreference.LIGHT -> ThemeMode.LIGHT
    ThemePreference.DARK -> ThemeMode.DARK
}

@Composable
private fun TamalutRadioPlaceholder(
    onPrepareRadioAzawan: ((String) -> Unit) -> Unit,
) {
    var status by remember { mutableStateOf("Test playback: non preparato") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "TamalutRadio")
            Button(
                modifier = Modifier.padding(top = 24.dp),
                onClick = { onPrepareRadioAzawan { status = it } },
            ) {
                Text(text = "Prepara Radio Azawan")
            }
            Text(
                modifier = Modifier.padding(top = 16.dp),
                text = status,
            )
        }
    }
}
