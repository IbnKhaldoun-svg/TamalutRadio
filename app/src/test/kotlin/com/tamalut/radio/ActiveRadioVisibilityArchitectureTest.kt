package com.tamalut.radio

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveRadioVisibilityArchitectureTest {
    @Test
    fun radioListUsesEventDrivenRememberedLazyListStateForLiveCardVisibility() {
        val radioScreen = Path.of(
  "../feature/radio/src/main/kotlin/com/tamalut/radio/feature/radio/RadioScreen.kt",
        ).readText()

        assertTrue(radioScreen.contains("autoScrollEnabled = state.searchQuery.isBlank()"))
        assertTrue(radioScreen.contains("val listState = rememberLazyListState()"))
        assertTrue(
  radioScreen.contains(
      "LaunchedEffect(playingStationId, stationIds, autoScrollEnabled, transientError != null)",
  ),
        )
        assertTrue(radioScreen.contains("item.key == playingStationId"))
        assertTrue(radioScreen.contains("listState.animateScrollToItem(stationIndex + leadingItemCount)"))
    }
}
