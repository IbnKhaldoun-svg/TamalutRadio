package com.tamalut.radio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.tamalut.radio.core.playback.Media3PlaybackController
import com.tamalut.radio.core.playback.PlaybackLaunchContract
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal enum class MainDestination(val label: String, val icon: ImageVector) {
    RADIO("Radio", Icons.Filled.Radio),
    LIBRARY("Musica", Icons.Filled.LibraryMusic),
    NOW_PLAYING("In Riproduzione", Icons.Filled.PlayCircle),
    SETTINGS("Impostazioni", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    private val preferencesRepository by lazy { DataStoreUserPreferencesRepository(applicationContext) }
    private val playbackController by lazy { Media3PlaybackController(applicationContext) }
    private val selectedDestination = MutableStateFlow(MainDestination.RADIO)
    private val resumeTick = MutableStateFlow(0L)

    private val database by lazy {
        Room.databaseBuilder(applicationContext, TamalutDatabase::class.java, "tamalut-radio.db")
            .setDriver(AndroidSQLiteDriver())
            .build()
    }

    private val radioViewModelFactory by lazy {
        val favoriteRepository = FavoriteStationRepository(database.favoriteStationDao())
        val stationRepository = RadioStationRepository(database.radioStationDao(), database.favoriteStationDao())
        RadioViewModelFactory(
            controller = RadioFeatureController(CoreRadioDataSource(stationRepository, favoriteRepository)),
            playbackGateway = Media3RadioPlaybackGateway(playbackController),
        )
    }

    private val libraryViewModelFactory by lazy {
        LibraryViewModelFactory(
            preferencesRepository = preferencesRepository,
            scanner = SafLocalAudioScanner(contentResolver),
            folderAccess = SafFolderAccess(contentResolver),
            playbackGateway = Media3LocalPlaybackGateway(playbackController),
        )
    }

    private val radioViewModel by lazy { ViewModelProvider(this, radioViewModelFactory)[RadioViewModel::class.java] }
    private val libraryViewModel by lazy { ViewModelProvider(this, libraryViewModelFactory)[LibraryViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLaunchIntent(intent)
        setContent {
            val userPreferences by preferencesRepository.userPreferences.collectAsState(initial = UserPreferences())
            val playbackState by playbackController.state.collectAsState()
            val destination by selectedDestination.collectAsState()
            val resumeVersion by resumeTick.collectAsState()
            val scope = rememberCoroutineScope()
            val overlayPermissionGranted = Settings.canDrawOverlays(this@MainActivity)
            val overlayPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) {
                val granted = Settings.canDrawOverlays(this@MainActivity)
                scope.launch { preferencesRepository.setOverlayEnabled(granted) }
                if (granted) FloatingOverlayWindow.show(this@MainActivity) else FloatingOverlayWindow.hide()
            }

            LaunchedEffect(userPreferences.overlayEnabled, resumeVersion) {
                val granted = Settings.canDrawOverlays(this@MainActivity)
                when {
                    userPreferences.overlayEnabled && granted -> FloatingOverlayWindow.show(this@MainActivity)
                    userPreferences.overlayEnabled && !granted -> {
                        FloatingOverlayWindow.hide()
                        preferencesRepository.setOverlayEnabled(false)
                    }
                    else -> FloatingOverlayWindow.hide()
                }
            }

            TamalutRadioTheme(themeMode = userPreferences.themePreference.toThemeMode()) {
                Scaffold(
                    bottomBar = {
                        Column {
                            PersistentMiniPlayer(state = playbackState, controller = playbackController)
                            NavigationBar {
                                MainDestination.entries.forEach { item ->
                                    NavigationBarItem(
                                        selected = destination == item,
                                        onClick = { selectedDestination.value = item },
                                        icon = { Icon(item.icon, contentDescription = item.label) },
                                        label = { Text(item.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { contentPadding ->
                    when (destination) {
                        MainDestination.RADIO -> RadioRoute(
                            viewModel = radioViewModel,
                            modifier = Modifier.fillMaxSize().padding(contentPadding),
                        )
                        MainDestination.LIBRARY -> LibraryRoute(
                            viewModel = libraryViewModel,
                            modifier = Modifier.fillMaxSize().padding(contentPadding),
                        )
                        MainDestination.NOW_PLAYING -> NowPlayingDestination(
                            state = playbackState,
                            modifier = Modifier.fillMaxSize().padding(contentPadding),
                        )
                        MainDestination.SETTINGS -> SettingsDestination(
                            overlayEnabled = userPreferences.overlayEnabled,
                            overlayPermissionGranted = overlayPermissionGranted,
                            onOverlayEnabledChange = { enabled ->
                                if (enabled) FloatingOverlayWindow.show(this@MainActivity) else FloatingOverlayWindow.hide()
                                scope.launch { preferencesRepository.setOverlayEnabled(enabled) }
                            },
                            onRequestOverlayPermission = {
                                overlayPermissionLauncher.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName"),
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxSize().padding(contentPadding),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.value += 1L
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        destinationForLaunchAction(intent?.action)?.let { selectedDestination.value = it }
    }

    override fun onDestroy() {
        playbackController.release()
        super.onDestroy()
    }
}

internal fun destinationForLaunchAction(action: String?): MainDestination? =
    if (action == PlaybackLaunchContract.ACTION_OPEN_NOW_PLAYING) MainDestination.NOW_PLAYING else null

@Composable
private fun SettingsDestination(
    overlayEnabled: Boolean,
    overlayPermissionGranted: Boolean,
    onOverlayEnabledChange: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPermissionExplanation by remember { mutableStateOf(false) }

    Surface(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Impostazioni", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Preferenze dell’app e controlli opzionali.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Player flottante", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Mostra un piccolo controllo sopra altre app.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = overlayEnabled && overlayPermissionGranted,
                            onCheckedChange = { requestedEnabled ->
                                when (resolveOverlayToggleAction(requestedEnabled, overlayPermissionGranted)) {
                                    OverlayToggleAction.DISABLE -> onOverlayEnabledChange(false)
                                    OverlayToggleAction.SHOW -> onOverlayEnabledChange(true)
                                    OverlayToggleAction.REQUEST_PERMISSION -> showPermissionExplanation = true
                                }
                            },
                        )
                    }
                    Text(
                        if (overlayPermissionGranted) {
                            if (overlayEnabled) "Permesso concesso · overlay attivo" else "Permesso concesso · overlay disattivato"
                        } else {
                            "Permesso “Visualizza sopra altre app” non concesso"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (overlayPermissionGranted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        "È facoltativo: se non lo concedi, Radio, Musica e riproduzione in background continuano a funzionare normalmente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanation = false },
            title = { Text("Consenti il player flottante?") },
            text = {
                Text(
                    "Per mantenere il player visibile sopra Maps, Waze e altre app, Android richiede il permesso speciale “Visualizza sopra altre app”. Il permesso è opzionale e puoi revocarlo in qualsiasi momento.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionExplanation = false
                    onRequestOverlayPermission()
                }) { Text("Continua") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionExplanation = false }) { Text("Non ora") }
            },
        )
    }
}

private fun ThemePreference.toThemeMode(): ThemeMode = when (this) {
    ThemePreference.FOLLOW_SYSTEM -> ThemeMode.FOLLOW_SYSTEM
    ThemePreference.LIGHT -> ThemeMode.LIGHT
    ThemePreference.DARK -> ThemeMode.DARK
}
