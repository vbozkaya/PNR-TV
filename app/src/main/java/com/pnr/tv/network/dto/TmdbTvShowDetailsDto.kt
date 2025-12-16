package com.pnr.tv.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * TMDB API'den dönen dizi detay bilgileri
 */
@JsonClass(generateAdapter = true)
data class TmdbTvShowDetailsDto(
    @Json(name = "id") val id: Int?,
    @Json(name = "name") val name: String?,
    @Json(name = "overview") val overview: String?,
    @Json(name = "original_language") val originalLanguage: String?,
    @Json(name = "genres") val genres: List<TmdbGenreDto>?,
    @Json(name = "created_by") val createdBy: List<TmdbCreatorDto>?,
    @Json(name = "credits") val credits: TmdbCreditsDto?,
)

/**
 * TMDB dizi yaratıcısı bilgisi
 */
@JsonClass(generateAdapter = true)
data class TmdbCreatorDto(
    @Json(name = "id") val id: Int?,
    @Json(name = "name") val name: String?,
    @Json(name = "gender") val gender: Int?,
    @Json(name = "profile_path") val profilePath: String?,
)

/**
 * TMDB dizi arama sonuçları
 */
@JsonClass(generateAdapter = true)
data class TmdbTvSearchResultDto(
    @Json(name = "results") val results: List<TmdbSearchTvShowDto>?,
)

/**
 * TMDB arama sonucunda tek bir dizi
 */
@JsonClass(generateAdapter = true)
data class TmdbSearchTvShowDto(
    @Json(name = "id") val id: Int?,
    @Json(name = "name") val name: String?,
    @Json(name = "original_name") val originalName: String?,
)
