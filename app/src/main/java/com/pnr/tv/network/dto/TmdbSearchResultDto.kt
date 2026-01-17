package com.pnr.tv.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * TMDB arama sonuçları
 */
@JsonClass(generateAdapter = true)
data class TmdbSearchResultDto(
    @Json(name = "results") val results: List<TmdbSearchMovieDto>?,
)

/**
 * TMDB arama sonucunda tek bir film
 */
@JsonClass(generateAdapter = true)
data class TmdbSearchMovieDto(
    @Json(name = "id") val id: Int?,
    @Json(name = "title") val title: String?,
    @Json(name = "original_title") val originalTitle: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
)
