package com.pnr.tv.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * TMDB API'den dönen film detay bilgileri
 */
@JsonClass(generateAdapter = true)
data class TmdbMovieDetailsDto(
    @Json(name = "id") val id: Int?,
    @Json(name = "title") val title: String?,
    @Json(name = "overview") val overview: String?,
    @Json(name = "original_language") val originalLanguage: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    @Json(name = "vote_count") val voteCount: Int?,
    @Json(name = "genres") val genres: List<TmdbGenreDto>?,
    @Json(name = "credits") val credits: TmdbCreditsDto?,
)

/**
 * TMDB tür (genre) bilgisi
 */
@JsonClass(generateAdapter = true)
data class TmdbGenreDto(
    @Json(name = "id") val id: Int?,
    @Json(name = "name") val name: String?,
)

/**
 * TMDB film kredileri (oyuncular ve ekip)
 */
@JsonClass(generateAdapter = true)
data class TmdbCreditsDto(
    @Json(name = "cast") val cast: List<TmdbCastDto>?,
    @Json(name = "crew") val crew: List<TmdbCrewDto>?,
)

/**
 * TMDB oyuncu bilgisi
 */
@JsonClass(generateAdapter = true)
data class TmdbCastDto(
    @Json(name = "name") val name: String?,
    @Json(name = "character") val character: String?,
    @Json(name = "order") val order: Int?,
)

/**
 * TMDB ekip bilgisi (yönetmen vb.)
 */
@JsonClass(generateAdapter = true)
data class TmdbCrewDto(
    @Json(name = "name") val name: String?,
    @Json(name = "job") val job: String?,
    @Json(name = "department") val department: String?,
)
