package com.pnr.tv.ui.player.state

import androidx.media3.common.MediaItem

/**
 * Player UI'nin tek birleşik state'i.
 * Tüm UI state'leri bu data class altında toplanır.
 */
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val duration: Long? = null,
    val currentPosition: Long = 0L,
    val errorMessage: String? = null,
    val currentMediaItem: MediaItem? = null,
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
)
