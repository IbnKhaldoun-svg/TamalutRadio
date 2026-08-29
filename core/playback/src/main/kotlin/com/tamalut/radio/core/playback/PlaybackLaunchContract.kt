package com.tamalut.radio.core.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object PlaybackLaunchContract {
    const val ACTION_OPEN_NOW_PLAYING = "com.tamalut.radio.action.OPEN_NOW_PLAYING"
    const val REQUEST_CODE = 2001

    fun createNowPlayingPendingIntent(context: Context): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launchIntent.action = ACTION_OPEN_NOW_PLAYING
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
