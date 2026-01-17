package com.pnr.tv.ui.player

import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.pnr.tv.R
import com.pnr.tv.ui.player.coordinator.PlayerStateCoordinator
import com.pnr.tv.ui.player.handler.PlayerListenerHandler
import com.pnr.tv.ui.player.manager.PlayerPlaybackManager
import com.pnr.tv.ui.player.manager.PlayerPlaylistManager
import com.pnr.tv.ui.player.manager.PlayerStateManager
import com.pnr.tv.ui.player.manager.PlayerTrackManager
import com.pnr.tv.ui.player.state.PlayerAction
import com.pnr.tv.ui.player.state.PlayerUiState
import com.pnr.tv.ui.player.state.TrackInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * State-driven Player ViewModel.
 * ExoPlayer'a doğrudan erişimi minimize eder ve action pattern kullanır.
 */
@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val savedStateHandle: SavedStateHandle,
        private val playbackManager: PlayerPlaybackManager,
        private val trackManager: PlayerTrackManager,
        private val playlistManager: PlayerPlaylistManager,
        private val stateManager: PlayerStateManager,
        private val stateCoordinator: PlayerStateCoordinator,
        private val listenerHandler: PlayerListenerHandler,
    ) : ViewModel() {
        private var player: ExoPlayer? = null
        private lateinit var playerListener: androidx.media3.common.Player.Listener
        
        // onRenderedFirstFrame callback'i (PlayerActivity'den set edilecek)
        var onRenderedFirstFrameCallback: (() -> Unit)? = null
        var onIsPlayingChangedCallback: ((Boolean) -> Unit)? = null

        /**
         * Tek birleşik UI state.
         */
        val uiState: kotlinx.coroutines.flow.StateFlow<PlayerUiState> = stateCoordinator.uiState

        // Backward compatibility için ayrı StateFlow'lar (deprecated)
        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.isPlaying"))
        val isPlaying: kotlinx.coroutines.flow.StateFlow<Boolean> = stateCoordinator.isPlaying

        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.isBuffering"))
        val isBuffering: kotlinx.coroutines.flow.StateFlow<Boolean> = stateCoordinator.isBuffering

        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.duration"))
        val duration: kotlinx.coroutines.flow.StateFlow<Long?> = stateCoordinator.duration

        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.currentPosition"))
        val currentPosition: kotlinx.coroutines.flow.StateFlow<Long> = stateCoordinator.currentPosition

        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.errorMessage"))
        val errorMessage: kotlinx.coroutines.flow.StateFlow<String?> = stateCoordinator.errorMessage

        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.currentMediaItem"))
        val currentMediaItem: kotlinx.coroutines.flow.StateFlow<MediaItem?> = stateCoordinator.currentMediaItem

        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.isLoading"))
        val isLoading: kotlinx.coroutines.flow.StateFlow<Boolean> = stateCoordinator.isLoading

        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.loadingMessage"))
        val loadingMessage: kotlinx.coroutines.flow.StateFlow<String> = stateCoordinator.loadingMessage

        init {
            initializeExoPlayer()
        }

        /**
         * Action pattern - ViewModel'e gönderilen action'ları işler.
         */
        fun handleAction(action: PlayerAction) {
            when (action) {
                is PlayerAction.PlayVideo -> {
                    playVideoInternal(action.url, action.contentId)
                }
                is PlayerAction.PlayPlaylist -> {
                    playPlaylistInternal(action.mediaItems, action.startWindowIndex, action.contentId)
                }
                is PlayerAction.Play -> {
                    playbackManager.play(player)
                }
                is PlayerAction.Pause -> {
                    playbackManager.pause(player)
                }
                is PlayerAction.TogglePlay -> {
                    playbackManager.togglePlay(player)
                }
                is PlayerAction.SeekTo -> {
                    playbackManager.seekTo(player, action.positionMs)
                    updateCurrentPosition()
                }
                is PlayerAction.SeekToNextChannel -> {
                    seekToNextChannelInternal()
                }
                is PlayerAction.SeekToPreviousChannel -> {
                    seekToPreviousChannelInternal()
                }
                is PlayerAction.ReleasePlayer -> {
                    releasePlayerInternal()
                }
                is PlayerAction.ReinitializePlayerIfNeeded -> {
                    reinitializePlayerIfNeededInternal()
                }
                is PlayerAction.SetEpisodeInfo -> {
                    setEpisodeInfo(action.episodeId, action.seriesId, action.seasonNumber, action.episodeNumber)
                }
                is PlayerAction.SetLoadingMessage -> {
                    stateCoordinator.setLoadingMessage(action.message)
                }
                is PlayerAction.SetIsLoading -> {
                    stateCoordinator.setIsLoading(action.isLoading)
                }
                is PlayerAction.StartWatching -> {
                    playbackManager.startWatching(action.channelId)
                }
                is PlayerAction.StopWatching -> {
                    playbackManager.stopWatching()
                }
            }
        }

        private fun initializeExoPlayer() {
            if (player == null) {
                playerListener = listenerHandler.createListener(
                    getPlayer = { player },
                    scope = viewModelScope,
                    updatePositionCallback = ::updateCurrentPosition,
                    onRenderedFirstFrameCallback = { onRenderedFirstFrameCallback?.invoke() },
                    onIsPlayingChangedCallback = { isPlaying -> onIsPlayingChangedCallback?.invoke(isPlaying) },
                )
                player = stateManager.buildPlayer(playerListener)
            }
        }

        private fun playVideoInternal(url: String, contentId: String? = null) {
            prepareForNewContent(contentId)
            try {
                ensurePlayerInitialized()
                player?.setMediaItem(MediaItem.fromUri(url))
                player?.prepare()
                startPositionUpdates()
            } catch (e: Exception) {
                handlePlaybackError(e, "playVideo")
            }
        }

        private fun playPlaylistInternal(mediaItems: List<MediaItem>, startWindowIndex: Int, contentId: String? = null) {
            prepareForNewContent(contentId)
            try {
                ensurePlayerInitialized()
                player?.setMediaItems(mediaItems, startWindowIndex, 0L)
                player?.prepare()
                startPositionUpdates()
            } catch (e: Exception) {
                handlePlaybackError(e, "playPlaylist")
            }
        }

        private fun seekToNextChannelInternal(): Boolean = seekToChannel { playlistManager.seekToNextChannel(player) }

        private fun seekToPreviousChannelInternal(): Boolean = seekToChannel { playlistManager.seekToPreviousChannel(player) }

        private fun seekToChannel(seekAction: () -> Boolean): Boolean {
            val success = seekAction()
            if (success) {
                stateCoordinator.setIsLoading(true)
                stateCoordinator.setLoadingMessage(context.getString(R.string.player_loading_buffering))
            }
            return success
        }

        // Backward compatibility için eski metodlar (deprecated)
        @Deprecated("Use handleAction(PlayerAction.PlayVideo) instead")
        fun playVideo(url: String, contentId: String? = null) {
            playVideoInternal(url, contentId)
        }

        @Deprecated("Use handleAction(PlayerAction.Play) instead")
        fun play() {
            handleAction(PlayerAction.Play)
        }

        @Deprecated("Use handleAction(PlayerAction.Pause) instead")
        fun pause() {
            handleAction(PlayerAction.Pause)
        }

        @Deprecated("Use handleAction(PlayerAction.SeekTo) instead")
        fun seekTo(positionMs: Long) {
            handleAction(PlayerAction.SeekTo(positionMs))
        }

        @Deprecated("Use handleAction(PlayerAction.SetLoadingMessage) instead")
        fun setLoadingMessage(message: String) {
            handleAction(PlayerAction.SetLoadingMessage(message))
        }

        @Deprecated("Use handleAction(PlayerAction.SetEpisodeInfo) instead")
        fun setEpisodeInfo(
            episodeId: String,
            seriesId: Int?,
            seasonNumber: Int?,
            episodeNumber: Int?,
        ) {
            savedStateHandle[com.pnr.tv.ui.player.manager.PlayerPlaybackManager.KEY_EPISODE_ID] = episodeId
            savedStateHandle[com.pnr.tv.ui.player.manager.PlayerPlaybackManager.KEY_SERIES_ID] = seriesId
            savedStateHandle[com.pnr.tv.ui.player.manager.PlayerPlaybackManager.KEY_SEASON_NUMBER] = seasonNumber
            savedStateHandle[com.pnr.tv.ui.player.manager.PlayerPlaybackManager.KEY_EPISODE_NUMBER] = episodeNumber
        }

        @Deprecated("Use handleAction(PlayerAction.PlayPlaylist) instead")
        fun playPlaylist(mediaItems: List<MediaItem>, startWindowIndex: Int, contentId: String? = null) {
            playPlaylistInternal(mediaItems, startWindowIndex, contentId)
        }

        @Deprecated("Use handleAction(PlayerAction.SeekToNextChannel) instead")
        fun seekToNextChannel(): Boolean {
            return seekToNextChannelInternal()
        }

        @Deprecated("Use handleAction(PlayerAction.SeekToPreviousChannel) instead")
        fun seekToPreviousChannel(): Boolean {
            return seekToPreviousChannelInternal()
        }

        @Deprecated("Use handleAction(PlayerAction.ReleasePlayer) instead")
        fun releasePlayer() {
            releasePlayerInternal()
        }

        @Deprecated("Use handleAction(PlayerAction.ReinitializePlayerIfNeeded) instead")
        fun reinitializePlayerIfNeeded() {
            reinitializePlayerIfNeededInternal()
        }

        @Deprecated("Use handleAction(PlayerAction.StartWatching) instead")
        fun startWatching(channelId: Int) {
            handleAction(PlayerAction.StartWatching(channelId))
        }

        @Deprecated("Use handleAction(PlayerAction.StopWatching) instead")
        fun stopWatching() {
            handleAction(PlayerAction.StopWatching)
        }

        // Utility metodlar - ExoPlayer erişimi minimize edilmiş
        fun getPlayer(): ExoPlayer? = player

        fun getCurrentChannelId(): Int? = playlistManager.getCurrentChannelId(player)

               suspend fun createPlaylistFromChannels(
                   channels: List<com.pnr.tv.db.entity.LiveStreamEntity>,
                   buildUrlUseCase: com.pnr.tv.domain.BuildLiveStreamUrlUseCase,
               ): List<MediaItem> {
                   stateCoordinator.setIsLoading(true)
                   stateCoordinator.setLoadingMessage(context.getString(R.string.player_loading_fetching_url))
                   return playlistManager.createPlaylistFromChannels(channels, buildUrlUseCase)
               }

        @UnstableApi
        fun getAudioTracks(): List<TrackInfo> = trackManager.getAudioTracks(player)

        @UnstableApi
        fun getSubtitleTracks(): List<TrackInfo> {
            val exoPlayer = player as? ExoPlayer
            return trackManager.getSubtitleTracks(exoPlayer)
        }

               @UnstableApi
               fun selectAudioTrack(trackInfo: TrackInfo) {
                   val exoPlayer = player as? ExoPlayer ?: return
                   trackManager.selectAudioTrack(exoPlayer, trackInfo)
               }

               @UnstableApi
               fun selectSubtitleTrack(trackInfo: TrackInfo?) {
                   val exoPlayer = player as? ExoPlayer ?: return
                   trackManager.selectSubtitleTrack(exoPlayer, trackInfo)
               }

               private fun prepareForNewContent(contentId: String?) {
                   playbackManager.saveCurrentPosition(player)
                   savedStateHandle[com.pnr.tv.ui.player.manager.PlayerPlaybackManager.KEY_CONTENT_ID] = contentId
                   stateCoordinator.setIsLoading(true)
                   stateCoordinator.setLoadingMessage(context.getString(R.string.player_loading_preparing))
               }

        private fun ensurePlayerInitialized() {
            if (player == null) initializeExoPlayer()
        }

               private fun handlePlaybackError(e: Exception, methodName: String) {
                   stateCoordinator.setIsLoading(false)
                   stateCoordinator.setErrorMessage(
                       context.getString(
                           R.string.error_media_load,
                           e.localizedMessage ?: context.getString(R.string.error_unknown),
                       ),
                   )
               }

        /**
         * ExoPlayer'dan mevcut pozisyonu alır ve state coordinator'a günceller.
         * Bu metod her zaman Main thread'de çalışmalıdır çünkü ExoPlayer'a erişim gerektirir.
         * startPositionUpdates() içindeki coroutine zaten Dispatchers.Main'de çalıştığı için
         * bu metod doğrudan çağrılabilir.
         */
        @MainThread
        private fun updateCurrentPosition() {
            player?.let { stateCoordinator.setCurrentPosition(it.currentPosition) }
        }

        private fun startPositionUpdates() {
            stateCoordinator.stopPositionUpdates()
            stateCoordinator.startPositionUpdates(player) {
                updateCurrentPosition()
            }
            playbackManager.startPeriodicPositionSave(player)
        }

        private fun stopPositionUpdates() {
            stateCoordinator.stopPositionUpdates()
            playbackManager.stopPeriodicPositionSave()
        }

               private fun releasePlayerInternal() {
                   playbackManager.saveCurrentPosition(player)
                   stopPositionUpdates()
                   playbackManager.stopWatching()
                   player?.removeListener(playerListener)
                   player?.release()
                   player = null
                   savedStateHandle.remove<String>(com.pnr.tv.ui.player.manager.PlayerPlaybackManager.KEY_CONTENT_ID)
               }

        private fun reinitializePlayerIfNeededInternal() {
            if (player == null) initializeExoPlayer()
        }

        override fun onCleared() {
            super.onCleared()
            releasePlayer()
            playbackManager.cleanup()
            stateCoordinator.cleanup()
        }
    }
