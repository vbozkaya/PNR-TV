package com.pnr.tv.ui.player.handler

import android.content.Intent

/**
 * Intent'ten veri çekme işlemlerini yöneten handler sınıfı
 */
object PlayerIntentHandler {
    const val EXTRA_VIDEO_URL = "extra_video_url"
    const val EXTRA_CHANNEL_ID = "extra_channel_id"
    const val EXTRA_CATEGORY_ID = "extra_category_id"
    const val EXTRA_CONTENT_ID = "extra_content_id"
    const val EXTRA_CONTENT_TITLE = "extra_content_title"
    const val EXTRA_CONTENT_RATING = "extra_content_rating"
    const val EXTRA_EPISODE_ID = "extra_episode_id"
    const val EXTRA_SERIES_ID = "extra_series_id"
    const val EXTRA_SEASON_NUMBER = "extra_season_number"
    const val EXTRA_EPISODE_NUMBER = "extra_episode_number"
    const val RESULT_CHANNEL_ID = "result_channel_id"

    /**
     * Intent'ten tüm player verilerini çeker ve PlayerData nesnesi olarak döndürür
     */
    fun extractPlayerData(intent: Intent): PlayerData {
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val contentId = intent.getStringExtra(EXTRA_CONTENT_ID)
        val contentTitle = intent.getStringExtra(EXTRA_CONTENT_TITLE)
        val contentRating = intent.getDoubleExtra(EXTRA_CONTENT_RATING, -1.0).takeIf { it > 0 }
        val channelId = intent.getIntExtra(EXTRA_CHANNEL_ID, -1).takeIf { it != -1 }
        val categoryId = intent.getIntExtra(EXTRA_CATEGORY_ID, -1).takeIf { it != -1 }
        val episodeId = intent.getStringExtra(EXTRA_EPISODE_ID)
        val seriesId = intent.getIntExtra(EXTRA_SERIES_ID, -1).takeIf { it != -1 }
        val seasonNumber = intent.getIntExtra(EXTRA_SEASON_NUMBER, -1).takeIf { it != -1 }
        val episodeNumber = intent.getIntExtra(EXTRA_EPISODE_NUMBER, -1).takeIf { it != -1 }

        return PlayerData(
            videoUrl = videoUrl,
            contentId = contentId,
            contentTitle = contentTitle,
            contentRating = contentRating,
            channelId = channelId,
            categoryId = categoryId,
            episodeId = episodeId,
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )
    }
}

/**
 * Intent'ten çıkarılan player verilerini tutan data class
 */
data class PlayerData(
    val videoUrl: String?,
    val contentId: String?,
    val contentTitle: String?,
    val contentRating: Double?,
    val channelId: Int?,
    val categoryId: Int?,
    val episodeId: String?,
    val seriesId: Int?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
) {
    val isLiveStream: Boolean
        get() = channelId != null
}
