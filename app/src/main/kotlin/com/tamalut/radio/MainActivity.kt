package com.tamalut.radio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.tamalut.radio.core.data.FavoriteStationRepository
import com.tamalut.radio.core.data.RadioStationRepository
import com.tamalut.radio.core.database.TamalutDatabase
import com.tamalut.radio.core.designsystem.TamalutRadioTheme
import com.tamalut.radio.core.designsystem.ThemeMode
import com.tamalut.radio.core.preferences.DataStoreUserPreferencesRepository
import com.tamalut.radio.core.preferences.ThemePreference
import com.tamalut.radio.core.preferences.UserPreferences
import com.tamalut.radio.feature.library.LibraryRoute
import com.tamalut.radio.feature.library.LibraryViewModel
import com.tamalut.radio.feature.library.LibraryViewModelFactory
import com.tamalut.radio.feature.library.Media3LocalPlaybackGateway
import com.tamalut.radio.feature.library.SafFolderAccess
import com.tamalut.radio.feature.library.SafLocalAudioScanner
import com.tamalut.radio.feature.radio.CoreRadioDataSource
import com.tamalut.radio.feature.radio.Media3RadioPlaybackGateway
import com.tamalut.radio.feature.radio.RadioFeatureController
import com.tamalut.radio.feature.radio.RadioRoute
import com.tamalut.radio.feature.radio.RadioViewModel
import com.tamalut.radio.feature.radio.RadioViewModelFactory

private enum class MainDestination {
    RADIO,
    LIBRARY,
}

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
            controller = RadioFeatureController(
                CoreRadioDataSource(
                    stationRepository = stationRepository,
                    favoriteRepository = favoriteRepository,
                ),
            ),
            playbackGateway = Media3RadioPlaybackGateway(applicationContext),
        )
    }

    private val libraryViewModelFactory by lazy {
        LibraryViewModelFactory(
            preferencesRepository = preferencesRepository,
            scanner = SafLocalAudioScanner(contentResolver),
            folderAccess = SafFolderAccess(contentResolver),
            playbackGateway = Media3LocalPlaybackGateway(applicationContext),
        )
    }

    private val radioViewModel by lazy {
        ViewModelProvider(this, radioViewModelFactory)[RadioViewModel::class.java]
    }

    private val libraryViewModel by lazy {
        ViewModelProvider(this, libraryViewModelFactory)[LibraryViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val userPreferences by preferencesRepository.userPreferences.collectAsState(
                initial = UserPreferences(),
            )
            var destination by remember { mutableStateOf(MainDestination.RADIO) }

            TamalutRadioTheme(
                themeMode = userPreferences.themePreference.toThemeMode(),
            ) {
                Scaffold(
                    topBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (destination == MainDestination.RADIO) {
                                Button(onClick = { destination = MainDestination.RADIO }) {
                                    Text("Radio")
                                }
                            } else {
                                OutlinedButton(onClick = { destination = MainDestination.RADIO }) {
                                    Text("Radio")
                                }
                            }
                            if (destination == MainDestination.LIBRARY) {
                                Button(onClick = { destination = MainDestination.LIBRARY }) {
                                    Text("Musica locale")
                                }
                            } else {
                                OutlinedButton(onClick = { destination = MainDestination.LIBRARY }) {
                                    Text("Musica locale")
                                }
                            }
                        }
                    },
                ) { contentPadding ->
                    when (destination) {
                        MainDestination.RADIO -> RadioRoute(
                            viewModel = radioViewModel,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding),
                        )
                        MainDestination.LIBRARY -> LibraryRoute(
                            viewModel = libraryViewModel,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding),
                        )
                    }
                }
            }
        }
    }
}

private fun ThemePreference.toThemeMode(): ThemeMode = when (this) {
    ThemePreference.FOLLOW_SYSTEM -> ThemeMode.FOLLOW_SYSTEM
    ThemePreference.LIGHT -> ThemeMode.LIGHT
    ThemePreference.DARK -> ThemeMode.DARK
}
