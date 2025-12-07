package com.pnr.tv.ui.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var player: ExoPlayer? = null
    private var positionUpdateJob: Job? = null
    private var currentTracks: Tracks? = null

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

        override fun onTracksChanged(tracks: Tracks) {
            currentTracks = tracks
            Timber.d("📊 Track'ler güncellendi: ${tracks.groups.size} grup")
        }
    }

    init {
        initializeExoPlayer()
    }

    private fun initializeExoPlayer() {
        if (player == null) {
            val trackSelector = DefaultTrackSelector(context)
            player = ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .build()
                .apply {
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
    
    /**
     * Ses dillerini getirir
     */
    @UnstableApi
    fun getAudioTracks(): List<TrackInfo> {
        val tracks = currentTracks ?: return emptyList()
        
        val audioTracks = mutableListOf<TrackInfo>()
        var groupIndex = 0
        
        for (group in tracks.groups) {
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                val trackGroup = group.mediaTrackGroup
                for (i in 0 until trackGroup.length) {
                    val format = trackGroup.getFormat(i)
                    val language = format.language
                    val formatLabel = format.label
                    val isSelected = group.isSelected && group.isTrackSelected(i)
                    
                    // HAM VERİYİ LOGLA - Tek satırda kompakt format
                    Timber.d("🔊 SES[$i] | lang=${format.language} | label='${format.label}' | id=${format.id} | mime=${format.sampleMimeType} | codecs=${format.codecs} | bitrate=${format.bitrate} | channels=${format.channelCount} | rate=${format.sampleRate} | role=${format.roleFlags} | selected=$isSelected")
                    Timber.d("🔊 SES[$i] FULL_FORMAT: $format")
                    
                    // Kaynaktan gelen label'ı direkt kullan, yoksa dil kodundan çevir
                    val displayLabel = if (!formatLabel.isNullOrBlank()) {
                        formatLabel.trim()
                    } else if (!language.isNullOrBlank()) {
                        getLanguageDisplayName(language)
                    } else {
                        "Bilinmeyen"
                    }
                    
                    audioTracks.add(
                        TrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = i,
                            language = language,
                            label = displayLabel,
                            isSelected = isSelected
                        )
                    )
                }
                groupIndex++
            }
        }
        
        Timber.d("🔊 Ses dilleri bulundu: ${audioTracks.size}")
        return audioTracks.distinctBy { it.language }
    }
    
    /**
     * Alt yazı dillerini getirir
     */
    @UnstableApi
    fun getSubtitleTracks(): List<TrackInfo> {
        val tracks = currentTracks ?: return emptyList()
        
        val subtitleTracks = mutableListOf<TrackInfo>()
        var groupIndex = 0
        
        for (group in tracks.groups) {
            if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                val trackGroup = group.mediaTrackGroup
                for (i in 0 until trackGroup.length) {
                    val format = trackGroup.getFormat(i)
                    val language = format.language
                    val formatLabel = format.label
                    val isSelected = group.isSelected && group.isTrackSelected(i)
                    
                    // HAM VERİYİ LOGLA - Tek satırda kompakt format
                    Timber.d("📝 SUB[$i] | lang=${format.language} | label='${format.label}' | id=${format.id} | mime=${format.sampleMimeType} | codecs=${format.codecs} | bitrate=${format.bitrate} | role=${format.roleFlags} | selected=$isSelected")
                    Timber.d("📝 SUB[$i] FULL_FORMAT: $format")
                    
                    // Kaynaktan gelen label'ı direkt kullan, yoksa dil kodundan çevir
                    val displayLabel = if (!formatLabel.isNullOrBlank()) {
                        formatLabel.trim()
                    } else if (!language.isNullOrBlank()) {
                        getLanguageDisplayName(language)
                    } else {
                        "Bilinmeyen"
                    }
                    
                    Timber.d("📝 Alt yazı track: language=$language, formatLabel=$formatLabel, displayLabel=$displayLabel")
                    
                    subtitleTracks.add(
                        TrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = i,
                            language = language,
                            label = displayLabel,
                            isSelected = isSelected
                        )
                    )
                }
                groupIndex++
            }
        }
        
        Timber.d("📝 Alt yazı dilleri bulundu: ${subtitleTracks.size}")
        return subtitleTracks.distinctBy { it.language }
    }
    
    /**
     * Dil kodunu tam dil adına çevirir (örn: "tr" -> "Türkçe", "en" -> "İngilizce")
     */
    private fun getLanguageDisplayName(languageCode: String?): String {
        if (languageCode.isNullOrBlank()) return "Bilinmeyen"
        
        return try {
            val locale = Locale(languageCode)
            locale.getDisplayLanguage(Locale("tr", "TR"))
        } catch (e: Exception) {
            languageCode.uppercase()
        }
    }
    
    /**
     * Ses dilini seçer
     */
    @UnstableApi
    fun selectAudioTrack(trackInfo: TrackInfo) {
        val player = player as? ExoPlayer ?: return
        val trackSelector = player.trackSelector as? DefaultTrackSelector ?: return
        
        Timber.d("🔊 Ses dili seçiliyor: ${trackInfo.label} (group=${trackInfo.groupIndex}, track=${trackInfo.trackIndex})")
        
        val tracks = currentTracks ?: return
        val audioGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
        
        if (trackInfo.groupIndex >= audioGroups.size) {
            Timber.e("❌ Geçersiz groupIndex: ${trackInfo.groupIndex}")
            return
        }
        
        val group = audioGroups[trackInfo.groupIndex]
        val trackGroup = group.mediaTrackGroup
        
        val parameters = trackSelector.parameters.buildUpon()
            .setRendererDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, false)
            .clearSelectionOverrides(androidx.media3.common.C.TRACK_TYPE_AUDIO)
            .build()
        
        val override = androidx.media3.common.TrackSelectionOverride(
            trackGroup,
            trackInfo.trackIndex
        )
        
        val newParameters = parameters.buildUpon()
            .addOverride(override)
            .build()
        
        trackSelector.parameters = newParameters
        Timber.d("✅ Ses dili seçildi: ${trackInfo.label}")
    }
    
    /**
     * Alt yazıyı seçer (null = alt yazıyı kapat)
     */
    @UnstableApi
    fun selectSubtitleTrack(trackInfo: TrackInfo?) {
        val player = player as? ExoPlayer ?: return
        val trackSelector = player.trackSelector as? DefaultTrackSelector ?: return
        
        if (trackInfo == null) {
            Timber.d("📝 Alt yazı kapatılıyor")
            
            // Önce mevcut parametreleri al
            val currentParameters = trackSelector.parameters
            val builder = currentParameters.buildUpon()
            
            // Text renderer'ı devre dışı bırak
            builder.setRendererDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
            
            // Tüm text track selection override'larını temizle
            builder.clearSelectionOverrides(androidx.media3.common.C.TRACK_TYPE_TEXT)
            
            val newParameters = builder.build()
            trackSelector.parameters = newParameters
            
            Timber.d("✅ Alt yazı kapatıldı - renderer disabled: ${newParameters.getRendererDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT)}")
        } else {
            Timber.d("📝 Alt yazı seçiliyor: ${trackInfo.label} (group=${trackInfo.groupIndex}, track=${trackInfo.trackIndex})")
            
            val tracks = currentTracks ?: return
            val textGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
            
            if (trackInfo.groupIndex >= textGroups.size) {
                Timber.e("❌ Geçersiz groupIndex: ${trackInfo.groupIndex}")
                return
            }
            
            val group = textGroups[trackInfo.groupIndex]
            val trackGroup = group.mediaTrackGroup
            
            val parameters = trackSelector.parameters.buildUpon()
                .setRendererDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                .clearSelectionOverrides(androidx.media3.common.C.TRACK_TYPE_TEXT)
                .build()
            
            val override = androidx.media3.common.TrackSelectionOverride(
                trackGroup,
                trackInfo.trackIndex
            )
            
            val newParameters = parameters.buildUpon()
                .addOverride(override)
                .build()
            
            trackSelector.parameters = newParameters
            Timber.d("✅ Alt yazı seçildi: ${trackInfo.label}")
        }
    }

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