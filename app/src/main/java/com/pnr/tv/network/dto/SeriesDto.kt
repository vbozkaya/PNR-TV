package com.pnr.tv.network.dto

import com.pnr.tv.db.entity.SeriesEntity
import com.squareup.moshi.Json

data class SeriesDto(
    @Json(name = "series_id") val seriesId: Int?,
    @Json(name = "name") val name: String?,
    @Json(name = "cover") val coverUrl: String?,
    @Json(name = "plot") val plot: String?,
    @Json(name = "releaseDate") val releaseDate: String?,
    @Json(name = "last_modified") val lastModified: String?,
    @Json(name = "rating") val rating: String?,
    @Json(name = "rating_5based") val rating5based: Float?,
    @Json(name = "backdrop_path") val backdropPath: List<String>?,
    @Json(name = "youtube_trailer") val youtubeTrailer: String?,
    @Json(name = "episode_run_time") val episodeRunTime: String?,
    @Json(name = "category_id") val categoryId: String?, // API'den string olarak geliyor
    @Json(name = "added") val added: String?,
    @Json(name = "tmdb") val tmdb: String?, // TMDB ID'si (string olarak)
)

fun SeriesDto.toEntity(): SeriesEntity? {
    // TMDB ID'yi parse et (String'den Int'e)
    val tmdbIdValue = tmdb?.toIntOrNull()
    
    return seriesId?.let {
        SeriesEntity(
            streamId = it,
            name = name,
            coverUrl = coverUrl,
            rating = rating?.toDoubleOrNull(),
            plot = plot,
            releaseDate = releaseDate,
            categoryId = categoryId,
            added = added,
            tmdbId = tmdbIdValue,
        )
    }
}
