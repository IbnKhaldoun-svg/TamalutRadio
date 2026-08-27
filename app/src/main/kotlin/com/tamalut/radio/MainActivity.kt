package com.tamalut.radio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.tamalut.radio.feature.radio.CoreRadioDataSource
import com.tamalut.radio.feature.radio.Media3RadioPlaybackGateway
import com.tamalut.radio.feature.radio.RadioFeatureController
import com.tamalut.radio.feature.radio.RadioRoute
import com.tamalut.radio.feature.radio.RadioViewModel
import com.tamalut.radio.feature.radio.RadioViewModelFactory

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

    private val radioViewModel by lazy {
        ViewModelProvider(this, radioViewModelFactory)[RadioViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val userPreferences by preferencesRepository.userPreferences.collectAsState(
                initial = UserPreferences(),
            )

            TamalutRadioTheme(
                themeMode = userPreferences.themePreference.toThemeMode(),
            ) {
                RadioRoute(
                    viewModel = radioViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun ThemePreference.toThemeMode(): ThemeMode = when (this) {
    ThemePreference.FOLLOW_SYSTEM -> ThemeMode.FOLLOW_SYSTEM
    ThemePreference.LIGHT -> ThemeMode.LIGHT
    ThemePreference.DARK -> ThemeMode.DARK
}
