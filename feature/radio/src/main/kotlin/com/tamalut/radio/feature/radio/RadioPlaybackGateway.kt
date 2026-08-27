package com.tamalut.radio.feature.radio

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.playback.RadioMediaItemFactory
import com.tamalut.radio.core.playback.TamalutPlaybackService
import java.util.concurrent.Executor

interface RadioPlaybackGateway {
    fun play(station: RadioStation, onResult: (Result<Unit>) -> Unit)
    fun release() = Unit
}

class Media3RadioPlaybackGateway(
    context: Context,
) : RadioPlaybackGateway {
    private val appContext = context.applicationContext
    private val mainExecutor = Executor { command ->
        Handler(Looper.getMainLooper()).post(command)
    }

    private var browser: MediaBrowser? = null
    private var generation: Long = 0

    override fun play(station: RadioStation, onResult: (Result<Unit>) -> Unit) {
        val requestGeneration = ++generation
        val token = SessionToken(
            appContext,
            ComponentName(appContext, TamalutPlaybackService::class.java),
        )
        val future = MediaBrowser.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { connectedBrowser ->
                        if (requestGeneration != generation) {
                            connectedBrowser.release()
                            return@onSuccess
                        }
                        runCatching {
                            browser?.release()
                            browser = connectedBrowser
                            connectedBrowser.setMediaItem(RadioMediaItemFactory.create(station))
                            connectedBrowser.prepare()
                            connectedBrowser.play()
                        }.onSuccess {
                            onResult(Result.success(Unit))
                        }.onFailure { error ->
                            onResult(Result.failure(error))
                        }
                    }
                    .onFailure { error ->
                        if (requestGeneration == generation) {
                            onResult(Result.failure(error))
                        }
                    }
            },
            mainExecutor,
        )
    }

    override fun release() {
        generation += 1
        browser?.release()
        browser = null
    }
}
