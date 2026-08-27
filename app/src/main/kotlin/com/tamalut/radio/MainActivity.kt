package com.tamalut.radio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tamalut.radio.core.designsystem.TamalutRadioTheme
import com.tamalut.radio.core.designsystem.ThemeMode
import com.tamalut.radio.core.preferences.DataStoreUserPreferencesRepository
import com.tamalut.radio.core.preferences.ThemePreference
import com.tamalut.radio.core.preferences.UserPreferences

class MainActivity : ComponentActivity() {
    private val preferencesRepository by lazy {
        DataStoreUserPreferencesRepository(applicationContext)
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
                TamalutRadioPlaceholder()
            }
        }
    }
}

private fun ThemePreference.toThemeMode(): ThemeMode = when (this) {
    ThemePreference.FOLLOW_SYSTEM -> ThemeMode.FOLLOW_SYSTEM
    ThemePreference.LIGHT -> ThemeMode.LIGHT
    ThemePreference.DARK -> ThemeMode.DARK
}

@Composable
private fun TamalutRadioPlaceholder() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "TamalutRadio")
        }
    }
}
