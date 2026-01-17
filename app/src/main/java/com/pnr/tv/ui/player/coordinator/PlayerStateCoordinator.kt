package com.pnr.tv.ui.player.coordinator

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.pnr.tv.R
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.ui.player.state.PlayerUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Player UI state yönetimini yapan coordinator sınıfı.
 * Tüm StateFlow tanımlarını ve state güncellemelerini bu sınıf üstlenir.
 * Position update mantığını da bu sınıf yönetir.
 */
class PlayerStateCoordinator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var positionUpdateJob: Job? = null

        private val _uiState = MutableStateFlow(
            PlayerUiState(
                isLoading = true, // Başlangıçta loading göster
                loadingMessage = context.getString(R.string.player_loading_preparing),
            ),
        )
        val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

        // Backward compatibility için ayrı StateFlow'lar (deprecated - kullanımdan kaldırılacak)
        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.isPlaying"))
        val isPlaying: StateFlow<Boolean> = _uiState.asStateFlow().map { it.isPlaying }
            .stateIn(scope, SharingStarted.Eagerly, false)

        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.isBuffering"))
        val isBuffering: StateFlow<Boolean> = _uiState.asStateFlow().map { it.isBuffering }
            .stateIn(scope, SharingStarted.Eagerly, false)

        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.duration"))
        val duration: StateFlow<Long?> = _uiState.asStateFlow().map { it.duration }
            .stateIn(scope, SharingStarted.Eagerly, null)

        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.currentPosition"))
        val currentPosition: StateFlow<Long> = _uiState.asStateFlow().map { it.currentPosition }
            .stateIn(scope, SharingStarted.Eagerly, 0L)

        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.errorMessage"))
        val errorMessage: StateFlow<String?> = _uiState.asStateFlow().map { it.errorMessage }
            .stateIn(scope, SharingStarted.Eagerly, null)

        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.currentMediaItem"))
        val currentMediaItem: StateFlow<MediaItem?> = _uiState.asStateFlow().map { it.currentMediaItem }
            .stateIn(scope, SharingStarted.Eagerly, null)

        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.isLoading"))
        val isLoading: StateFlow<Boolean> = _uiState.asStateFlow().map { it.isLoading }
            .stateIn(scope, SharingStarted.Eagerly, _uiState.value.isLoading)

        @Deprecated("Use uiState instead", ReplaceWith("uiState.value.loadingMessage"))
        val loadingMessage: StateFlow<String> = _uiState.asStateFlow().map { it.loadingMessage }
            .stateIn(scope, SharingStarted.Eagerly, _uiState.value.loadingMessage)

        private fun updateState(update: (PlayerUiState) -> PlayerUiState) {
            _uiState.value = update(_uiState.value)
        }

        fun setIsPlaying(value: Boolean) {
            updateState { it.copy(isPlaying = value) }
        }

        fun setIsBuffering(value: Boolean) {
            updateState { it.copy(isBuffering = value) }
        }

        fun setDuration(value: Long?) {
            updateState { it.copy(duration = value) }
        }

        fun setCurrentPosition(value: Long) {
            updateState { it.copy(currentPosition = value) }
        }

        fun setErrorMessage(value: String?) {
            updateState { it.copy(errorMessage = value) }
        }

        fun setCurrentMediaItem(value: MediaItem?) {
            updateState { it.copy(currentMediaItem = value) }
        }

        fun setIsLoading(value: Boolean) {
            val oldValue = _uiState.value.isLoading
            updateState { it.copy(isLoading = value) }
            Timber.tag("LoadingDebug").d("setIsLoading: $oldValue -> $value")
        }

        fun setLoadingMessage(value: String) {
            val oldValue = _uiState.value.loadingMessage
            updateState { it.copy(loadingMessage = value) }
            Timber.tag("LoadingDebug").d("setLoadingMessage: '$oldValue' -> '$value'")
        }

        /**
         * Position update döngüsünü başlatır.
         * @param player ExoPlayer instance (null olabilir)
         * @param updatePositionCallback Position güncelleme callback'i
         */
        fun startPositionUpdates(
            player: ExoPlayer?,
            updatePositionCallback: () -> Unit,
        ) {
            stopPositionUpdates()
            positionUpdateJob =
                scope.launch(Dispatchers.Main) {
                    while (isActive) {
                        player?.let { updatePositionCallback() }
                        delay(UIConstants.DelayDurations.PLAYER_POSITION_UPDATE_INTERVAL)
                    }
                }
        }

        /**
         * Position update döngüsünü durdurur.
         */
        fun stopPositionUpdates() {
            positionUpdateJob?.cancel()
            positionUpdateJob = null
        }

        /**
         * Coordinator'ı temizler ve tüm kaynakları serbest bırakır.
         */
        fun cleanup() {
            stopPositionUpdates()
            scope.cancel()
        }
    }
