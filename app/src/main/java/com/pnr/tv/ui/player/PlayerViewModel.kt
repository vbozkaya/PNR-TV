package com.pnr.tv.ui.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pnr.tv.Constants
import com.pnr.tv.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var player: ExoPlayer? = null
    private var positionUpdateJob: Job? = null

    private companion object {
        const val KEY_WATCHING_CHANNEL_ID = "watching_channel_id"
        const val KEY_WATCHING_START_TIME = "watching_start_time"
        const val KEY_CONTENT_ID = "content_id"
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _duration = MutableStateFlow<Long?>(null)
    val duration: StateFlow<Long?> = _duration.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) {
                _duration.value = player?.duration?.takeIf { it > 0 }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _errorMessage.value = error.localizedMessage ?: "Oynatma hatası oluştu"
            Timber.e(error, "Player error occurred")
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            updateCurrentPosition()
        }
    }

    init {
        initializeExoPlayer()
    }

    private fun initializeExoPlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(context).build().apply {
                addListener(playerListener)
            }
        }
    }

    fun playVideo(url: String, contentId: String? = null) {
        Timber.d("🎬 playVideo çağrıldı: url=$url, contentId=$contentId")
        Timber.d("🔍 Player null mu? ${player == null}")
        
        // Önceki videonun pozisyonunu kaydet
        saveCurrentPosition()

        // Yeni contentId'yi kaydet
        savedStateHandle[KEY_CONTENT_ID] = contentId

        try {
            if (player == null) {
                Timber.e("❌ Player NULL! initializeExoPlayer çağrılıyor...")
                initializeExoPlayer()
            }
            
            val mediaItem = MediaItem.fromUri(url)
            Timber.d("📺 MediaItem oluşturuldu: $url")
            player?.setMediaItem(mediaItem)
            player?.prepare()
            Timber.d("✅ Player prepare() çağrıldı")

            // Video oynatmayı başlat (coroutine'den bağımsız)
            Timber.d("🎮 player?.play() çağrılıyor (hemen)...")
            player?.play()
            Timber.d("✅ player?.play() çağrıldı, playbackState: ${player?.playbackState}")
            
            // Kaldığı yerden devam için coroutine başlat
            if (contentId != null) {
                Timber.d("🔍 contentId var, coroutine başlatılıyor: $contentId")
                viewModelScope.launch {
                    try {
                        Timber.d("🔍 Coroutine başladı, playback position kontrol ediliyor...")
                        val savedPosition = contentRepository.getPlaybackPosition(contentId)
                        Timber.d("🔍 Saved position: $savedPosition")
                        if (savedPosition != null && savedPosition.durationMs > 0 && savedPosition.positionMs < savedPosition.durationMs * 0.95) {
                            Timber.d("⏩ Kaldığı yerden devam: ${savedPosition.positionMs / 1000}s")
                            player?.seekTo(savedPosition.positionMs)
                        } else {
                            Timber.d("▶️ Yeni video, baştan başlatılıyor")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "❌ Coroutine içinde hata oluştu")
                    }
                }
            }

            startPositionUpdates()
        } catch (e: Exception) {
            _errorMessage.value = "Medya yüklenirken hata oluştu: ${e.localizedMessage}"
            Timber.e(e, "Error playing video")
        }
    }

    fun play() = player?.play()
    fun pause() = player?.pause()
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        updateCurrentPosition()
    }

    fun getPlayer(): ExoPlayer? = player

    private fun saveCurrentPosition() {
        val contentId = savedStateHandle.get<String>(KEY_CONTENT_ID) ?: return
        player?.let { exoPlayer ->
            val currentPos = exoPlayer.currentPosition
            val duration = exoPlayer.duration
            if (duration > 0) {
                viewModelScope.launch {
                    if (currentPos < duration * 0.95) {
                        contentRepository.savePlaybackPosition(contentId, currentPos, duration)
                    } else {
                        contentRepository.deletePlaybackPosition(contentId)
                    }
                }
            }
        }
        savedStateHandle.remove<String>(KEY_CONTENT_ID)
    }

    fun startWatching(channelId: Int) {
        savedStateHandle[KEY_WATCHING_CHANNEL_ID] = channelId
        savedStateHandle[KEY_WATCHING_START_TIME] = System.currentTimeMillis()
    }

    fun stopWatching() {
        val channelId = savedStateHandle.get<Int>(KEY_WATCHING_CHANNEL_ID)
        val startTime = savedStateHandle.get<Long>(KEY_WATCHING_START_TIME)

        if (channelId != null && startTime != null) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= Constants.WatchingDuration.MILLIS) {
                viewModelScope.launch { contentRepository.saveRecentlyWatched(channelId) }
            }
        }

        savedStateHandle.remove<Int>(KEY_WATCHING_CHANNEL_ID)
        savedStateHandle.remove<Long>(KEY_WATCHING_START_TIME)
    }

    private fun updateCurrentPosition() {
        player?.let { _currentPosition.value = it.currentPosition }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                updateCurrentPosition()
                delay(Constants.DelayDurations.PLAYER_POSITION_UPDATE_INTERVAL)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }
    
    private fun releasePlayer() {
        saveCurrentPosition()
        stopPositionUpdates()
        stopWatching()
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}