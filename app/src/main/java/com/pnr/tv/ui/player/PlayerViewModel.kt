package com.pnr.tv.ui.player

import android.content.Context
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.pnr.tv.PlayerConstants
import com.pnr.tv.R
import com.pnr.tv.TimeConstants
import com.pnr.tv.UIConstants
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
class PlayerViewModel
    @Inject
    constructor(
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
            const val TAG_VIDEO_PLAYBACK_ERROR = "VIDEO_PLAYBACK_ERROR"
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

        private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
        val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem.asStateFlow()

        private val playerListener =
            object : Player.Listener {
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
                    // Her zaman aynı mesajı göster (tüm dillere uyarlanmış)
                    _errorMessage.value = context.getString(R.string.error_video_playback_failed)
                    Timber.tag(TAG_VIDEO_PLAYBACK_ERROR).e(error, "Playback failed: ${error.errorCode}")
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

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    _currentMediaItem.value = mediaItem
                }
            }

        init {
            initializeExoPlayer()
        }

        private fun initializeExoPlayer() {
            if (player == null) {
                val trackSelector = DefaultTrackSelector(context)

                // Canlı yayınlar için optimize edilmiş buffer ayarları
                // Daha küçük buffer = daha hızlı kanal değişimi
                val loadControl =
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            PlayerConstants.MIN_BUFFER_DURATION.toInt(), // Min buffer: 5 saniye
                            PlayerConstants.DEFAULT_BUFFER_DURATION.toInt(), // Max buffer: 15 saniye
                            PlayerConstants.MIN_BUFFER_DURATION.toInt(), // Buffer for playback: 5 saniye
                            PlayerConstants.MIN_BUFFER_DURATION.toInt(), // Buffer for playback after rebuffer: 5 saniye
                        )
                        .build()

                player =
                    ExoPlayer.Builder(context)
                        .setTrackSelector(trackSelector)
                        .setLoadControl(loadControl)
                        .build()
                        .apply {
                            addListener(playerListener)
                            // Varsayılan olarak playWhenReady = true (otomatik oynatma)
                            playWhenReady = true
                        }
            }
        }

        fun playVideo(
            url: String,
            contentId: String? = null,
        ) {
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
                _errorMessage.value = context.getString(R.string.error_media_load, e.localizedMessage ?: context.getString(R.string.error_unknown))
                Timber.tag(TAG_VIDEO_PLAYBACK_ERROR).e(e, "playVideo failed: ${e.javaClass.simpleName}")
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
         * Canlı kanallar için playlist oluşturur.
         * @param channels Kanal listesi
         * @param buildUrlUseCase URL oluşturma use case'i
         * @return MediaItem listesi
         */
        suspend fun createPlaylistFromChannels(
            channels: List<com.pnr.tv.db.entity.LiveStreamEntity>,
            buildUrlUseCase: com.pnr.tv.domain.BuildLiveStreamUrlUseCase,
        ): List<MediaItem> {
            val mediaItems = mutableListOf<MediaItem>()

            for (channel in channels) {
                val url = buildUrlUseCase(channel) ?: continue

                val mediaMetadata =
                    MediaMetadata.Builder()
                        .setTitle(channel.name ?: "")
                        .build()

                val mediaItem =
                    MediaItem.Builder()
                        .setUri(url)
                        .setMediaMetadata(mediaMetadata)
                        .setTag(channel.streamId) // Kanal ID'sini tag olarak sakla
                        .build()

                mediaItems.add(mediaItem)
            }

            return mediaItems
        }

        /**
         * Playlist ile player'ı başlatır.
         * @param mediaItems MediaItem listesi
         * @param startWindowIndex Başlangıç kanal indeksi
         * @param contentId Content ID (film/dizi için, canlı yayın için null)
         */
        fun playPlaylist(
            mediaItems: List<MediaItem>,
            startWindowIndex: Int,
            contentId: String? = null,
        ) {
            // Önceki videonun pozisyonunu kaydet
            saveCurrentPosition()

            // Yeni contentId'yi kaydet
            savedStateHandle[KEY_CONTENT_ID] = contentId

            try {
                if (player == null) {
                    initializeExoPlayer()
                }

                // Playlist'i player'a set et
                player?.setMediaItems(mediaItems, startWindowIndex, 0L)
                player?.prepare()

                // Video oynatmayı başlat
                player?.play()

                // Kaldığı yerden devam için coroutine başlat (sadece film/dizi için)
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
                _errorMessage.value = context.getString(R.string.error_media_load, e.localizedMessage ?: context.getString(R.string.error_unknown))
                Timber.tag(TAG_VIDEO_PLAYBACK_ERROR).e(e, "playPlaylist failed: ${e.javaClass.simpleName}")
            }
        }

        /**
         * Sonraki kanala geçer (playlist içinde).
         * Canlı stream'lerde direkt window index kullanarak geçiş yapar.
         */
        fun seekToNextChannel(): Boolean {
            val player = this.player ?: return false
            val currentIndex = player.currentMediaItemIndex
            val totalItems = player.mediaItemCount

            if (currentIndex < totalItems - 1) {
                // Mevcut stream'i durdur - canlı stream'lerde playlist geçişi için kritik
                player.stop()

                // Direkt window index'e geç (seekToNextMediaItem yerine)
                val nextIndex = currentIndex + 1
                player.seekTo(nextIndex, 0L)

                // Yeni stream'i hazırla - canlı stream'lerde kritik
                player.prepare()

                // Otomatik oynatmayı etkinleştir ve başlat
                player.playWhenReady = true
                player.play()

                return true
            }
            return false
        }

        /**
         * Önceki kanala geçer (playlist içinde).
         * Canlı stream'lerde direkt window index kullanarak geçiş yapar.
         */
        fun seekToPreviousChannel(): Boolean {
            val player = this.player ?: return false
            val currentIndex = player.currentMediaItemIndex

            if (currentIndex > 0) {
                // Mevcut stream'i durdur - canlı stream'lerde playlist geçişi için kritik
                player.stop()

                // Direkt window index'e geç (seekToPreviousMediaItem yerine)
                val previousIndex = currentIndex - 1
                player.seekTo(previousIndex, 0L)

                // Yeni stream'i hazırla - canlı stream'lerde kritik
                player.prepare()

                // Otomatik oynatmayı etkinleştir ve başlat
                player.playWhenReady = true
                player.play()

                return true
            }
            return false
        }

        /**
         * Mevcut MediaItem'ın tag'inden kanal ID'sini alır.
         */
        fun getCurrentChannelId(): Int? {
            val currentItem = player?.currentMediaItem
            return try {
                currentItem?.localConfiguration?.tag as? Int
            } catch (e: Exception) {
                Timber.e(e, "❌ Tag'den kanal ID'si alınamadı")
                null
            }
        }

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
                        Timber.d(
                            "🔊 SES[$i] | lang=${format.language} | label='${format.label}' | id=${format.id} | mime=${format.sampleMimeType} | codecs=${format.codecs} | bitrate=${format.bitrate} | channels=${format.channelCount} | rate=${format.sampleRate} | role=${format.roleFlags} | selected=$isSelected",
                        )
                        Timber.d("🔊 SES[$i] FULL_FORMAT: $format")

                        // Kaynaktan gelen label'ı direkt kullan, yoksa dil kodundan çevir
                        val displayLabel =
                            if (!formatLabel.isNullOrBlank()) {
                                formatLabel.trim()
                            } else if (!language.isNullOrBlank()) {
                                getLanguageDisplayName(language)
                            } else {
                                context.getString(R.string.unknown)
                            }

                        audioTracks.add(
                            TrackInfo(
                                groupIndex = groupIndex,
                                trackIndex = i,
                                language = language,
                                label = displayLabel,
                                isSelected = isSelected,
                            ),
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
                        Timber.d(
                            "📝 SUB[$i] | lang=${format.language} | label='${format.label}' | id=${format.id} | mime=${format.sampleMimeType} | codecs=${format.codecs} | bitrate=${format.bitrate} | role=${format.roleFlags} | selected=$isSelected",
                        )
                        Timber.d("📝 SUB[$i] FULL_FORMAT: $format")

                        // Kaynaktan gelen label'ı direkt kullan, yoksa dil kodundan çevir
                        val displayLabel =
                            if (!formatLabel.isNullOrBlank()) {
                                formatLabel.trim()
                            } else if (!language.isNullOrBlank()) {
                                getLanguageDisplayName(language)
                            } else {
                                context.getString(R.string.unknown)
                            }

                        Timber.d("📝 Alt yazı track: language=$language, formatLabel=$formatLabel, displayLabel=$displayLabel")

                        subtitleTracks.add(
                            TrackInfo(
                                groupIndex = groupIndex,
                                trackIndex = i,
                                language = language,
                                label = displayLabel,
                                isSelected = isSelected,
                            ),
                        )
                    }
                    groupIndex++
                }
            }

            Timber.d("📝 Alt yazı dilleri bulundu: ${subtitleTracks.size}")
            return subtitleTracks.distinctBy { it.language }
        }

        /**
         * Dil kodunu tam dil adına çevirir.
         * Uygulamanın mevcut diline göre dil ismini gösterir.
         * Örnek: Uygulama İngilizceyse "ru" -> "Russian", Türkçeyse "ru" -> "Rusça"
         */
        private fun getLanguageDisplayName(languageCode: String?): String {
            if (languageCode.isNullOrBlank()) return context.getString(R.string.unknown)

            return try {
                val locale = Locale(languageCode)
                // Uygulamanın mevcut dilini al
                val currentLocale = getCurrentLocale()
                // Mevcut uygulama dilinde dil ismini göster
                locale.getDisplayLanguage(currentLocale)
            } catch (e: Exception) {
                languageCode.uppercase()
            }
        }

        /**
         * Uygulamanın mevcut locale'ini döndürür
         */
        private fun getCurrentLocale(): Locale {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale
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

            val parameters =
                trackSelector.parameters.buildUpon()
                    .setRendererDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, false)
                    .clearSelectionOverrides(androidx.media3.common.C.TRACK_TYPE_AUDIO)
                    .build()

            val override =
                androidx.media3.common.TrackSelectionOverride(
                    trackGroup,
                    trackInfo.trackIndex,
                )

            val newParameters =
                parameters.buildUpon()
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

                Timber.d(
                    "✅ Alt yazı kapatıldı - renderer disabled: ${newParameters.getRendererDisabled(
                        androidx.media3.common.C.TRACK_TYPE_TEXT,
                    )}",
                )
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

                val parameters =
                    trackSelector.parameters.buildUpon()
                        .setRendererDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                        .clearSelectionOverrides(androidx.media3.common.C.TRACK_TYPE_TEXT)
                        .build()

                val override =
                    androidx.media3.common.TrackSelectionOverride(
                        trackGroup,
                        trackInfo.trackIndex,
                    )

                val newParameters =
                    parameters.buildUpon()
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
                if (elapsedTime >= TimeConstants.WatchingDuration.MILLIS) {
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
            positionUpdateJob =
                viewModelScope.launch {
                    while (isActive) {
                        updateCurrentPosition()
                        delay(UIConstants.DelayDurations.PLAYER_POSITION_UPDATE_INTERVAL)
                    }
                }
        }

        private fun stopPositionUpdates() {
            positionUpdateJob?.cancel()
            positionUpdateJob = null
        }

        /**
         * ExoPlayer'ı ve tüm kaynaklarını serbest bırakır.
         * Video pozisyonu otomatik olarak kaydedilir.
         * Public metod - Activity lifecycle'da çağrılabilir.
         */
        fun releasePlayer() {
            saveCurrentPosition()
            stopPositionUpdates()
            stopWatching()
            player?.removeListener(playerListener)
            player?.release()
            player = null
            Timber.d("🔴 Player release edildi, tüm kaynaklar serbest bırakıldı")
        }

        /**
         * Player null ise yeniden oluşturur.
         * onStart() lifecycle'da kullanılır.
         */
        fun reinitializePlayerIfNeeded() {
            if (player == null) {
                Timber.d("🔄 Player null, yeniden oluşturuluyor...")
                initializeExoPlayer()
                Timber.d("✅ Player yeniden oluşturuldu")
            }
        }

        override fun onCleared() {
            super.onCleared()
            releasePlayer()
        }
    }
