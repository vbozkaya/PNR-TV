package com.pnr.tv.ui.player.manager

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.pnr.tv.core.constants.PlayerConstants
import com.pnr.tv.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * ExoPlayer kurulumu ve hata yönetimi ile ilgili işlemleri yöneten sınıf.
 * PlayerViewModel'den ExoPlayer kurulum mantığını ve hata işleme mantığını ayırmak için oluşturulmuştur.
 */
class PlayerStateManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            const val TAG_VIDEO_PLAYBACK_ERROR = "VIDEO_PLAYBACK_ERROR"
        }

        /**
         * ExoPlayer nesnesini kurup döndürür.
         * @param listener Player.Listener instance
         * @return Yapılandırılmış ExoPlayer instance
         */
        fun buildPlayer(listener: Player.Listener): ExoPlayer {
            // 1. ADIM: TrackSelector ayarlarını "Zorla Oynat" moduna al
            val trackSelector = DefaultTrackSelector(context)
            trackSelector.parameters =
                trackSelector.parameters.buildUpon()
                    // KRİTİK AYARLAR: Cihaz limitlerini aşsa bile oynatmayı dene
                    .setExceedRendererCapabilitiesIfNecessary(true) // Cihazın "dekoderim bunu çözemez" dediği durumlarda bile render etmeye çalış
                    .setExceedVideoConstraintsIfNecessary(true) // Cihazın "ekranım bu boyutu desteklemiyor" dediği durumlarda bile oynat
                    .build()

            // 2. ADIM: LoadControl (Canlı yayınlar için optimize edilmiş buffer ayarları)
            // Daha küçük buffer = daha hızlı kanal değişimi
            val loadControl =
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        // Min buffer: 5 saniye
                        PlayerConstants.MIN_BUFFER_DURATION.toInt(),
                        // Max buffer: 15 saniye
                        PlayerConstants.DEFAULT_BUFFER_DURATION.toInt(),
                        // Buffer for playback: 5 saniye
                        PlayerConstants.MIN_BUFFER_DURATION.toInt(),
                        // Buffer for playback after rebuffer: 5 saniye
                        PlayerConstants.MIN_BUFFER_DURATION.toInt(),
                    )
                    .build()

            // 3. ADIM: RenderersFactory (Decoder fallback desteği ile)
            // DefaultRenderersFactory: Fallback decoder mekanizması ile codec desteği
            // AC3 desteklenmeyen cihazlarda otomatik olarak alternatif codec'e geçer
            // setEnableDecoderFallback(true): Donanım decoder başarısız olursa Android'in yazılımsal decoder'ına geçer
            // Not: Media3 için çalışan bir FFmpeg extension bulunamadı (UnnoTed repository 401 hatası veriyor)
            // Bu yüzden Android'in kendi yazılımsal decoder'ı kullanılacak
            val renderersFactory =
                androidx.media3.exoplayer.DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true) // Yazılımsal dekodere düşmeye izin ver

            return ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build()
                .apply {
                    addListener(listener)
                    // Varsayılan olarak playWhenReady = true (otomatik oynatma)
                    playWhenReady = true
                }
        }

        /**
         * Player hatalarını işler ve kullanıcıya gösterilecek hata mesajını döndürür.
         * @param error PlaybackException instance
         * @return Kullanıcıya gösterilecek hata mesajı (String)
         */
        fun handlePlayerError(error: PlaybackException): String {
            // AC3 codec hatası kontrolü
            val errorMessage = error.message ?: ""
            val causeMessage = error.cause?.message ?: ""
            val isAc3Error =
                errorMessage.contains("audio/ac3", ignoreCase = true) ||
                    errorMessage.contains("audio/eac3", ignoreCase = true) ||
                    errorMessage.contains("ac3", ignoreCase = true) ||
                    causeMessage.contains("audio/ac3", ignoreCase = true) ||
                    causeMessage.contains("audio/eac3", ignoreCase = true) ||
                    causeMessage.contains("ac3", ignoreCase = true) ||
                    (
                        error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED &&
                            (
                                errorMessage.contains("Decoder init failed", ignoreCase = true) ||
                                    causeMessage.contains("Decoder init failed", ignoreCase = true)
                            )
                    )

            return if (isAc3Error) {
                // AC3 codec desteklenmiyor - kullanıcıya özel mesaj göster
                Timber.tag(
                    TAG_VIDEO_PLAYBACK_ERROR,
                ).w(error, "AC3 codec desteklenmiyor: ${error.errorCode}, message: $errorMessage")
                context.getString(R.string.error_audio_codec_ac3_not_supported)
            } else {
                // Diğer hatalar için genel mesaj
                Timber.tag(TAG_VIDEO_PLAYBACK_ERROR).e(error, "Playback failed: ${error.errorCode}")
                context.getString(R.string.error_video_playback_failed)
            }
        }
    }
