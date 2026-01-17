package com.pnr.tv.ui.player.state

import androidx.media3.common.MediaItem

/**
 * Player ViewModel'e gönderilen action'ları temsil eden sealed class.
 * Activity ve diğer çağıran yerler bu action'ları kullanarak ViewModel ile iletişim kurar.
 */
sealed class PlayerAction {
    /**
     * Tek bir video URL'i oynatır.
     */
    data class PlayVideo(
        val url: String,
        val contentId: String? = null,
    ) : PlayerAction()

    /**
     * Playlist oynatır.
     */
    data class PlayPlaylist(
        val mediaItems: List<MediaItem>,
        val startWindowIndex: Int,
        val contentId: String? = null,
    ) : PlayerAction()

    /**
     * Oynatmayı başlatır.
     */
    object Play : PlayerAction()

    /**
     * Oynatmayı duraklatır.
     */
    object Pause : PlayerAction()

    /**
     * Oynat/Duraklat durumunu değiştirir.
     */
    object TogglePlay : PlayerAction()

    /**
     * Belirtilen pozisyona atlar.
     */
    data class SeekTo(val positionMs: Long) : PlayerAction()

    /**
     * Sonraki kanala geçer.
     */
    object SeekToNextChannel : PlayerAction()

    /**
     * Önceki kanala geçer.
     */
    object SeekToPreviousChannel : PlayerAction()

    /**
     * Player'ı serbest bırakır.
     */
    object ReleasePlayer : PlayerAction()

    /**
     * Player'ı yeniden başlatır (eğer null ise).
     */
    object ReinitializePlayerIfNeeded : PlayerAction()

    /**
     * Episode bilgilerini set eder.
     */
    data class SetEpisodeInfo(
        val episodeId: String,
        val seriesId: Int?,
        val seasonNumber: Int?,
        val episodeNumber: Int?,
    ) : PlayerAction()

    /**
     * Loading mesajını set eder.
     */
    data class SetLoadingMessage(val message: String) : PlayerAction()

    /**
     * Loading durumunu set eder.
     */
    data class SetIsLoading(val isLoading: Boolean) : PlayerAction()

    /**
     * İzlemeyi başlatır (kanal için).
     */
    data class StartWatching(val channelId: Int) : PlayerAction()

    /**
     * İzlemeyi durdurur.
     */
    object StopWatching : PlayerAction()
}
