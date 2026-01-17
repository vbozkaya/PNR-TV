package com.pnr.tv.ui.player.manager

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.domain.BuildLiveStreamUrlUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Player playlist yönetimi için manager sınıfı.
 * Playlist oluşturma, kanal geçişi ve mevcut kanal bilgisi işlemlerini yönetir.
 */
class PlayerPlaylistManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Canlı kanallar için playlist oluşturur.
         * @param channels Kanal listesi
         * @param buildUrlUseCase URL oluşturma use case'i
         * @return MediaItem listesi
         */
        suspend fun createPlaylistFromChannels(
            channels: List<LiveStreamEntity>,
            buildUrlUseCase: BuildLiveStreamUrlUseCase,
        ): List<MediaItem> {
            val mediaItems = mutableListOf<MediaItem>()
            var skippedCount = 0

            for (channel in channels) {
                // ✅ Suspend fonksiyonu await et
                val url = buildUrlUseCase(channel)
                if (url.isNullOrBlank()) {
                    skippedCount++
                    Timber.w("⚠️ Playlist: Kanal için URL oluşturulamadı - channelId=${channel.streamId}, channelName=${channel.name}")
                    continue
                }

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

            if (skippedCount > 0) {
                Timber.w("⚠️ Playlist: $skippedCount kanal için URL oluşturulamadı, ${mediaItems.size} kanal eklendi")
            }

            return mediaItems
        }

        /**
         * Sonraki kanala geçer (playlist içinde).
         * Canlı stream'lerde direkt window index kullanarak geçiş yapar.
         * @param player ExoPlayer instance (null olabilir)
         * @return true eğer geçiş başarılı olduysa, false aksi halde
         */
        fun seekToNextChannel(player: ExoPlayer?): Boolean {
            val exoPlayer = player ?: return false
            val currentIndex = exoPlayer.currentMediaItemIndex
            val totalItems = exoPlayer.mediaItemCount

            if (currentIndex < totalItems - 1) {
                // Mevcut stream'i durdur - canlı stream'lerde playlist geçişi için kritik
                exoPlayer.stop()

                // Direkt window index'e geç (seekToNextMediaItem yerine)
                val nextIndex = currentIndex + 1
                exoPlayer.seekTo(nextIndex, 0L)

                // Yeni stream'i hazırla - canlı stream'lerde kritik
                // ✅ playWhenReady zaten true, manuel play() çağrısına gerek yok
                exoPlayer.prepare()

                return true
            }
            return false
        }

        /**
         * Önceki kanala geçer (playlist içinde).
         * Canlı stream'lerde direkt window index kullanarak geçiş yapar.
         * @param player ExoPlayer instance (null olabilir)
         * @return true eğer geçiş başarılı olduysa, false aksi halde
         */
        fun seekToPreviousChannel(player: ExoPlayer?): Boolean {
            val exoPlayer = player ?: return false
            val currentIndex = exoPlayer.currentMediaItemIndex

            if (currentIndex > 0) {
                // Mevcut stream'i durdur - canlı stream'lerde playlist geçişi için kritik
                exoPlayer.stop()

                // Direkt window index'e geç (seekToPreviousMediaItem yerine)
                val previousIndex = currentIndex - 1
                exoPlayer.seekTo(previousIndex, 0L)

                // Yeni stream'i hazırla - canlı stream'lerde kritik
                // ✅ playWhenReady zaten true, manuel play() çağrısına gerek yok
                exoPlayer.prepare()

                return true
            }
            return false
        }

        /**
         * Mevcut MediaItem'ın tag'inden kanal ID'sini alır.
         * @param player ExoPlayer instance (null olabilir)
         * @return Kanal ID'si, bulunamazsa null
         */
        fun getCurrentChannelId(player: ExoPlayer?): Int? {
            val currentItem = player?.currentMediaItem
            return try {
                currentItem?.localConfiguration?.tag as? Int
            } catch (e: Exception) {
                null
            }
        }
    }
