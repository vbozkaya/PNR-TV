package com.pnr.tv.ui.player.handler

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.pnr.tv.R
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.ui.player.coordinator.PlayerStateCoordinator
import com.pnr.tv.ui.player.manager.PlayerPlaybackManager
import com.pnr.tv.ui.player.manager.PlayerStateManager
import com.pnr.tv.ui.player.manager.PlayerTrackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Player event listener'larını yöneten handler sınıfı.
 * Player.Listener implementasyonunu ve tüm player event'lerini bu sınıf üstlenir.
 */
class PlayerListenerHandler
    @Inject
    constructor(
        private val playbackManager: PlayerPlaybackManager,
        private val trackManager: PlayerTrackManager,
        private val stateManager: PlayerStateManager,
        private val stateCoordinator: PlayerStateCoordinator,
        private val contentRepository: ContentRepository,
        private val premiumManager: PremiumManager,
        private val savedStateHandle: SavedStateHandle,
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Player.Listener instance'ını oluşturur.
         * @param getPlayer ExoPlayer instance'ını döndüren lambda (pozisyon güncellemeleri için)
         * @param scope CoroutineScope
         * @param updatePositionCallback Pozisyon güncelleme callback'i
         * @param onRenderedFirstFrameCallback İlk frame render edildiğinde çağrılacak callback (kontrol barını göstermek için)
         * @param onIsPlayingChangedCallback Oynatma durumu değiştiğinde çağrılacak callback
         */
        fun createListener(
            getPlayer: () -> ExoPlayer?,
            scope: CoroutineScope,
            updatePositionCallback: () -> Unit,
            onRenderedFirstFrameCallback: (() -> Unit)? = null,
            onIsPlayingChangedCallback: ((Boolean) -> Unit)? = null,
        ): Player.Listener {
            return object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    stateCoordinator.setIsPlaying(isPlaying)
                    onIsPlayingChangedCallback?.invoke(isPlaying)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    stateCoordinator.setIsBuffering(playbackState == Player.STATE_BUFFERING)

                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            stateCoordinator.setIsLoading(true)
                            stateCoordinator.setLoadingMessage(context.getString(R.string.player_loading_buffering))
                        }
                        Player.STATE_READY -> {
                            val currentPlayer = getPlayer()
                            stateCoordinator.setDuration(currentPlayer?.duration?.takeIf { it > 0 })
                        }
                        Player.STATE_IDLE -> {
                            stateCoordinator.setIsLoading(false)
                        }
                        Player.STATE_ENDED -> {
                            stateCoordinator.setIsLoading(false)
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    stateCoordinator.setIsLoading(false)
                    stateCoordinator.setErrorMessage(stateManager.handlePlayerError(error))
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    updatePositionCallback()
                }

                override fun onTracksChanged(tracks: Tracks) {
                    trackManager.handleTracksChanged(tracks)
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    stateCoordinator.setCurrentMediaItem(mediaItem)
                }

                override fun onRenderedFirstFrame() {
                    stateCoordinator.setIsLoading(false)
                    onRenderedFirstFrameCallback?.invoke()
                }
            }
        }
    }
