package com.pnr.tv.ui.series

/**
 * İzlenme durumu enum'u
 */
enum class WatchStatus {
    NOT_WATCHED, // İzlenmedi (Beyaz çerçeve)
    IN_PROGRESS, // Yarım kaldı (Kırmızı çerçeve)
    FULLY_WATCHED, // Tamamlandı (Yeşil çerçeve)
}

/**
 * Ayrıştırılmış bölüm verisi.
 */
data class ParsedEpisode(
    val episodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String, // Orijinal başlık
    val cleanTitle: String?, // Sadece bölüm adı (nullable)
    val watchStatus: WatchStatus = WatchStatus.NOT_WATCHED, // İzlenme durumu
    val containerExtension: String? = null, // Container format (ts, mp4, mkv, etc.)
)
