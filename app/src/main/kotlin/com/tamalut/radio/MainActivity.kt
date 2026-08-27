package com.tamalut.radio

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.tamalut.radio.core.data.FavoriteStationRepository
import com.tamalut.radio.core.data.RadioStationRepository
import com.tamalut.radio.core.database.TamalutDatabase
import com.tamalut.radio.core.designsystem.TamalutRadioTheme
import com.tamalut.radio.core.designsystem.ThemeMode
import com.tamalut.radio.core.playback.PlaybackTestCatalog
import com.tamalut.radio.core.playback.TamalutPlaybackService
import com.tamalut.radio.core.preferences.DataStoreUserPreferencesRepository
import com.tamalut.radio.core.preferences.ThemePreference
import com.tamalut.radio.core.preferences.UserPreferences
import com.tamalut.radio.feature.radio.CoreRadioDataSource
import com.tamalut.radio.feature.radio.RadioFeatureController
import com.tamalut.radio.feature.radio.RadioRoute
import com.tamalut.radio.feature.radio.RadioViewModel
import com.tamalut.radio.feature.radio.RadioViewModelFactory
import java.util.concurrent.Executor

class MainActivity : ComponentActivity() {
    private val preferencesRepository by lazy {
        DataStoreUserPreferencesRepository(applicationContext)
    }

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            TamalutDatabase::class.java,
            "tamalut-radio.db",
        )
            .setDriver(AndroidSQLiteDriver())
            .build()
    }

    private val radioViewModelFactory by lazy {
        val favoriteRepository = FavoriteStationRepository(database.favoriteStationDao())
        val stationRepository = RadioStationRepository(
            stationDao = database.radioStationDao(),
            favoriteStationDao = database.favoriteStationDao(),
        )
        RadioViewModelFactory(
            RadioFeatureController(
                CoreRadioDataSource(
                    stationRepository = stationRepository,
                    favoriteRepository = favoriteRepository,
                ),
            ),
        )
    }

    private val radioViewModel by lazy {
        ViewModelProvider(this, radioViewModelFactory)[RadioViewModel::class.java]
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
                RadioFeatureHost(
                    viewModel = radioViewModel,
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
                    onResult("Radio Azawan pronta dalla sessione di test")
                }.onFailure { error ->
                    onResult("Test playback non disponibile: ${error.message ?: "errore sconosciuto"}")
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
private fun RadioFeatureHost(
    viewModel: RadioViewModel,
    onPrepareRadioAzawan: ((String) -> Unit) -> Unit,
) {
    var status by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            RadioRoute(
                viewModel = viewModel,
                onStationSelected = { station ->
                    status = "${station.name} selezionata · playback nel passaggio 2/2"
                },
                modifier = Modifier.weight(1f),
            )
            status?.let {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    text = it,
                )
            }
            TextButton(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                onClick = { onPrepareRadioAzawan { status = it } },
            ) {
                Text("Test temporaneo Radio Azawan")
            }
        }
    }
}
