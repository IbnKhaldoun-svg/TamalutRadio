package com.tamalut.radio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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

private enum class MainDestination(
    val label: String,
    val icon: ImageVector,
) {
    RADIO("Radio", Icons.Filled.Radio),
    LIBRARY("Musica", Icons.Filled.LibraryMusic),
    NOW_PLAYING("In Riproduzione", Icons.Filled.PlayCircle),
    SETTINGS("Impostazioni", Icons.Filled.Settings),
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
                    bottomBar = {
                        NavigationBar {
                            MainDestination.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = destination == item,
                                    onClick = { destination = item },
                                    icon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label,
                                        )
                                    },
                                    label = { Text(item.label) },
                                )
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

                        MainDestination.NOW_PLAYING -> PlaceholderDestination(
                            title = "In Riproduzione",
                            subtitle = "I controlli completi del player arriveranno nel modulo dedicato.",
                            icon = Icons.Filled.PlayCircle,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding),
                        )

                        MainDestination.SETTINGS -> PlaceholderDestination(
                            title = "Impostazioni",
                            subtitle = "Tema e preferenze avanzate saranno raccolti qui.",
                            icon = Icons.Filled.Settings,
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

@androidx.compose.runtime.Composable
private fun PlaceholderDestination(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ThemePreference.toThemeMode(): ThemeMode = when (this) {
    ThemePreference.FOLLOW_SYSTEM -> ThemeMode.FOLLOW_SYSTEM
    ThemePreference.LIGHT -> ThemeMode.LIGHT
    ThemePreference.DARK -> ThemeMode.DARK
}
