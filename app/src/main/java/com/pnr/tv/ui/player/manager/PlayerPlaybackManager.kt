package com.pnr.tv.ui.player.manager

import androidx.lifecycle.SavedStateHandle
import androidx.media3.exoplayer.ExoPlayer
import com.pnr.tv.core.constants.TimeConstants
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.repository.ContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Player playback ile ilgili işlemleri yöneten sınıf.
 * Pozisyon kaydetme, premium kontrolü, izleme süresi gibi işlevleri içerir.
 */
class PlayerPlaybackManager
    @Inject
    constructor(
        private val contentRepository: ContentRepository,
        private val premiumManager: PremiumManager,
        private val savedStateHandle: SavedStateHandle,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var positionSaveJob: Job? = null

        companion object {
            const val KEY_WATCHING_CHANNEL_ID = "watching_channel_id"
            const val KEY_WATCHING_START_TIME = "watching_start_time"
            const val KEY_CONTENT_ID = "content_id"
            const val KEY_EPISODE_ID = "episode_id"
            const val KEY_SERIES_ID = "series_id"
            const val KEY_SEASON_NUMBER = "season_number"
            const val KEY_EPISODE_NUMBER = "episode_number"
        }

        /**
         * Mevcut pozisyonu kaydeder.
         * @param player ExoPlayer instance (null olabilir)
         */
        fun saveCurrentPosition(player: ExoPlayer?) {
            scope.launch(Dispatchers.Main) {
                val contentId = savedStateHandle.get<String>(KEY_CONTENT_ID) ?: return@launch
                player?.let { exoPlayer ->
                    // ExoPlayer değerlerini Main thread'de oku (zaten Main thread'deyiz)
                    val currentPos = exoPlayer.currentPosition
                    val duration = exoPlayer.duration

                    if (duration > 0) {
                        // Veritabanı işlemleri için IO dispatcher'a geç
                        withContext(Dispatchers.IO) {
                            // Premium kontrolü: Sadece Premium kullanıcılar için pozisyon kaydedilir
                            val isPremium = premiumManager.isPremiumSync()
                            if (isPremium) {
                                if (currentPos < duration * 0.95) {
                                    contentRepository.savePlaybackPosition(contentId, currentPos, duration)
                                } else {
                                    contentRepository.deletePlaybackPosition(contentId)
                                }
                            }
                            // Premium değilse pozisyon kaydedilmez

                            // Watch progress güncelle (Premium kontrolü yok, her zaman güncelle)
                            updateWatchProgress(contentId, currentPos, duration)
                        }
                    }
                }
                // contentId'yi silme - release edilene kadar saklamalıyız
            }
        }

        /**
         * Watch progress'i günceller (episode bilgileri varsa).
         */
        private suspend fun updateWatchProgress(
            contentId: String,
            positionMs: Long,
            durationMs: Long,
        ) {
            val episodeId = savedStateHandle.get<String>(KEY_EPISODE_ID)
            val seriesId = savedStateHandle.get<Int>(KEY_SERIES_ID)
            val seasonNumber = savedStateHandle.get<Int>(KEY_SEASON_NUMBER)
            val episodeNumber = savedStateHandle.get<Int>(KEY_EPISODE_NUMBER)

            // Episode bilgileri varsa watch progress güncelle
            if (episodeId != null) {
                contentRepository.updateWatchProgress(
                    contentId = contentId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    episodeId = episodeId,
                    seriesId = seriesId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                )
            }
        }

        /**
         * İzleme başlatır (kanal için).
         */
        fun startWatching(channelId: Int) {
            savedStateHandle[KEY_WATCHING_CHANNEL_ID] = channelId
            savedStateHandle[KEY_WATCHING_START_TIME] = System.currentTimeMillis()
        }

        /**
         * İzlemeyi durdurur ve gerekirse son izlenenlere ekler.
         */
        fun stopWatching() {
            val channelId = savedStateHandle.get<Int>(KEY_WATCHING_CHANNEL_ID)
            val startTime = savedStateHandle.get<Long>(KEY_WATCHING_START_TIME)

            if (channelId != null && startTime != null) {
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime >= TimeConstants.WatchingDuration.MILLIS) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            contentRepository.saveRecentlyWatched(channelId)
                        }
                    }
                }
            }

            savedStateHandle.remove<Int>(KEY_WATCHING_CHANNEL_ID)
            savedStateHandle.remove<Long>(KEY_WATCHING_START_TIME)
        }

        /**
         * Pozisyonu düzenli aralıklarla kaydeder (her 30 saniyede bir).
         * Bu sayede kullanıcı çıktığında en son pozisyon kaydedilmiş olur.
         * @param player ExoPlayer instance (null olabilir)
         */
        fun startPeriodicPositionSave(player: ExoPlayer?) {
            stopPeriodicPositionSave()
            positionSaveJob =
                scope.launch(Dispatchers.Main) {
                    while (isActive) {
                        delay(TimeConstants.Intervals.THIRTY_SECONDS_MS) // 30 saniye
                        val contentId = savedStateHandle.get<String>(KEY_CONTENT_ID) ?: continue
                        // Delay'den sonra player referansının hala geçerli olduğunu kontrol et
                        player?.let { exoPlayer ->
                            // ExoPlayer değerlerini Main thread'de oku (zaten Main thread'deyiz)
                            val currentPos = exoPlayer.currentPosition
                            val duration = exoPlayer.duration

                            if (duration > 0 && currentPos > 0) {
                                // Veritabanı işlemleri için IO dispatcher'a geç
                                withContext(Dispatchers.IO) {
                                    // Premium kontrolü: Sadece Premium kullanıcılar için pozisyon kaydedilir
                                    val isPremium = premiumManager.isPremiumSync()
                                    if (isPremium) {
                                        if (currentPos < duration * 0.95) {
                                            contentRepository.savePlaybackPosition(contentId, currentPos, duration)
                                        } else {
                                            contentRepository.deletePlaybackPosition(contentId)
                                        }
                                    }

                                    // Watch progress güncelle (Premium kontrolü yok, her zaman güncelle)
                                    updateWatchProgress(contentId, currentPos, duration)
                                }
                            }
                        }
                    }
                }
        }

        /**
         * Periyodik pozisyon kaydetmeyi durdurur.
         */
        fun stopPeriodicPositionSave() {
            positionSaveJob?.cancel()
            positionSaveJob = null
        }

        /**
         * Manager'ı temizler ve tüm kaynakları serbest bırakır.
         */
        fun cleanup() {
            stopPeriodicPositionSave()
            scope.cancel()
        }

        /**
         * ExoPlayer kontrol metodları - ViewModel'den ExoPlayer erişimini minimize etmek için
         */

        /**
         * Oynatmayı başlatır.
         * @param player ExoPlayer instance (null olabilir)
         */
        fun play(player: ExoPlayer?) {
            player?.play()
        }

        /**
         * Oynatmayı duraklatır.
         * @param player ExoPlayer instance (null olabilir)
         */
        fun pause(player: ExoPlayer?) {
            player?.pause()
        }

        /**
         * Oynat/Duraklat durumunu değiştirir.
         * @param player ExoPlayer instance (null olabilir)
         */
        fun togglePlay(player: ExoPlayer?) {
            player?.let {
                if (it.isPlaying) {
                    it.pause()
                } else {
                    it.play()
                }
            }
        }

        /**
         * Belirtilen pozisyona atlar.
         * @param player ExoPlayer instance (null olabilir)
         * @param positionMs Pozisyon (milisaniye cinsinden)
         */
        fun seekTo(player: ExoPlayer?, positionMs: Long) {
            player?.seekTo(positionMs)
        }
    }
