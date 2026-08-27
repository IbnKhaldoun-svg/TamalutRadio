package com.tamalut.radio.core.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
internal class PlaybackSessionCallback(
    private val stopAndExit: () -> Unit,
) : MediaLibrarySession.Callback {

    override fun onConnectAsync(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaSession.ConnectionResult> {
        val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
        val canStopExit = canStopExit(session, controller)
        if (canStopExit) {
            val commands = builder.build().availableSessionCommands
                .buildUpon()
                .add(PlaybackCommands.stopExitCommand)
                .build()
            builder.setAvailableSessionCommands(commands)
        }
        builder.setMediaButtonPreferences(PlaybackControls.mediaButtonPreferences(canStopExit))
        return Futures.immediateFuture(builder.build())
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: androidx.media3.session.SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (!PlaybackCommands.isStopExit(customCommand.customAction)) {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
        if (!canStopExit(session, controller)) {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED))
        }
        stopAndExit()
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(PlaybackLibrary.rootItem, params))

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val items = PlaybackLibrary.children(parentId, page, pageSize).map(PlaybackLibrary::toMediaItem)
        return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val node = PlaybackLibrary.nodeById(mediaId)
            ?: return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
        return Futures.immediateFuture(LibraryResult.ofItem(PlaybackLibrary.toMediaItem(node), null))
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        if (mediaItems.isNotEmpty() && mediaItems.all { it.localConfiguration != null }) {
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs),
            )
        }

        val requestedId = mediaItems.singleOrNull()?.mediaId.orEmpty()
        val resolved = PlaybackTestCatalog.resolve(requestedId)
            ?: return Futures.immediateFailedFuture(
                UnsupportedOperationException("Unknown or non-playable media item: $requestedId"),
            )
        val playableItems = resolved.stations.map(RadioMediaItemFactory::create)
        return Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(
                playableItems,
                resolved.startIndex,
                C.TIME_UNSET,
            ),
        )
    }

    private fun canStopExit(session: MediaSession, controller: MediaSession.ControllerInfo): Boolean =
        PlaybackControls.shouldExposeStopExit(
            isTrusted = controller.isTrusted,
            isMediaNotificationController = session.isMediaNotificationController(controller),
            isAutoCompanionController = session.isAutoCompanionController(controller),
            isAutomotiveController = session.isAutomotiveController(controller),
        )
}
