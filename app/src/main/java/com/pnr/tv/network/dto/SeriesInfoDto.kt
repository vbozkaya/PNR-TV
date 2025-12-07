package com.pnr.tv.network.dto

import com.pnr.tv.db.entity.SeriesEntity
import com.squareup.moshi.Json

/**
 * Dizi detay bilgilerini içeren DTO.
 * API'den get_series_info action'ı ile alınan veri.
 */
data class SeriesInfoDto(
    @Json(name = "seasons") val seasons: List<SeasonDto>?,
    @Json(name = "episodes") val episodes: Map<String, List<EpisodeDto>>?,
    @Json(name = "info") val info: SeriesDetailInfoDto?,
)

/**
 * Sezon bilgisi DTO'su.
 */
data class SeasonDto(
    @Json(name = "season_number") val seasonNumber: Int?,
    @Json(name = "name") val name: String?,
    @Json(name = "episode_count") val episodeCount: Int?,
    @Json(name = "overview") val overview: String?,
    @Json(name = "cover") val coverUrl: String?,
    @Json(name = "cover_big") val coverBigUrl: String?,
)

/**
 * Bölüm bilgisi DTO'su.
 */
data class EpisodeDto(
    @Json(name = "id") val id: String?,
    @Json(name = "episode_num") val episodeNumber: Int?,
    @Json(name = "title") val title: String?,
    @Json(name = "container_extension") val containerExtension: String?,
    @Json(name = "info") val info: EpisodeInfoDto?,
    @Json(name = "custom_sid") val customSid: String?,
    @Json(name = "added") val added: String?,
    @Json(name = "season") val season: Int?,
    @Json(name = "direct_source") val directSource: String?,
)

/**
 * Bölüm detay bilgisi DTO'su.
 */
data class EpisodeInfoDto(
    @Json(name = "plot") val plot: String?,
    @Json(name = "releasedate") val releaseDate: String?,
    @Json(name = "rating") val rating: String?,
    @Json(name = "duration_secs") val durationSecs: Int?,
    @Json(name = "duration") val duration: String?,
)

/**
 * Dizi genel bilgisi DTO'su.
 */
data class SeriesDetailInfoDto(
    @Json(name = "name") val name: String?,
    @Json(name = "cover") val coverUrl: String?,
    @Json(name = "plot") val plot: String?,
    @Json(name = "cast") val cast: String?,
    @Json(name = "director") val director: String?,
    @Json(name = "genre") val genre: String?,
    @Json(name = "releaseDate") val releaseDate: String?,
    @Json(name = "last_modified") val lastModified: String?,
    @Json(name = "rating") val rating: String?,
    @Json(name = "rating_5based") val rating5based: Double?,
    @Json(name = "backdrop_path") val backdropPath: List<String>?,
    @Json(name = "youtube_trailer") val youtubeTrailer: String?,
    @Json(name = "episode_run_time") val episodeRunTime: String?,
    @Json(name = "category_id") val categoryId: String?,
    @Json(name = "tmdb") val tmdb: String?, // TMDB ID'si
)

/**
 * SeriesDetailInfoDto'yu SeriesEntity'ye dönüştürür.
 */
fun SeriesDetailInfoDto.toEntity(seriesId: Int): SeriesEntity {
    // TMDB ID'yi parse et
    val tmdbIdValue = tmdb?.toIntOrNull()
    
    return SeriesEntity(
        streamId = seriesId,
        name = name,
        coverUrl = coverUrl,
        rating = rating?.toDoubleOrNull() ?: rating5based,
        plot = plot,
        releaseDate = releaseDate,
        categoryId = categoryId,
        added = lastModified,
        tmdbId = tmdbIdValue,
    )
}

