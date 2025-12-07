package com.pnr.tv.network.dto

import com.pnr.tv.db.entity.MovieEntity
import com.squareup.moshi.Json

data class MovieDto(
    @Json(name = "stream_id") val streamId: Int?,
    @Json(name = "name") val name: String?,
    @Json(name = "stream_icon") val streamIconUrl: String?,
    @Json(name = "rating") val rating: String?,
    @Json(name = "plot") val plot: String?,
    @Json(name = "category_id") val categoryId: String?, // API'den string olarak geliyor
    @Json(name = "rating_5based") val rating5based: Float?,
    @Json(name = "backdrop_path") val backdropPath: List<String>?,
    @Json(name = "num") val num: Int?,
    @Json(name = "stream_type") val streamType: String?,
    @Json(name = "tmdb") val tmdb: String?,
    @Json(name = "trailer") val trailer: String?,
    @Json(name = "added") val added: String?,
    @Json(name = "is_adult") val isAdult: Int?,
    @Json(name = "category_ids") val categoryIds: List<Int>?,
    @Json(name = "container_extension") val containerExtension: String?,
    @Json(name = "custom_sid") val customSid: String?,
    @Json(name = "direct_source") val directSource: String?,
)

fun MovieDto.toEntity(): MovieEntity? {
    val id = streamId ?: num
    val finalCategoryId = categoryId ?: categoryIds?.firstOrNull()?.toString()
    // TMDB ID'yi parse et (String'den Int'e)
    val tmdbIdValue = tmdb?.toIntOrNull()
    
    timber.log.Timber.d("🎬 API'den film: $name, containerExtension: ${containerExtension ?: "null (varsayılan ts kullanılacak)"}")
    
    return id?.let {
        MovieEntity(
            streamId = it,
            name = name,
            streamIconUrl = streamIconUrl,
            rating = rating?.toDoubleOrNull(),
            plot = plot,
            categoryId = finalCategoryId,
            added = added,
            tmdbId = tmdbIdValue,
            containerExtension = containerExtension,
        )
    }
}
