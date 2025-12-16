package com.pnr.tv.network

import com.pnr.tv.network.dto.TmdbMovieDetailsDto
import com.pnr.tv.network.dto.TmdbSearchResultDto
import com.pnr.tv.network.dto.TmdbTvSearchResultDto
import com.pnr.tv.network.dto.TmdbTvShowDetailsDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TMDB (The Movie Database) API servisi
 * Film ve dizi detayları için endpoint'ler içerir
 */
interface TmdbApiService {
    /**
     * Film adına göre TMDB'de arama yapar
     */
    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String,
        @Query("region") region: String? = null,
        @Query("year") year: Int? = null,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbSearchResultDto

    /**
     * Film ID'sine göre detaylı bilgi getirir (oyuncular ve ekip dahil)
     */
    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits",
        @Query("language") language: String = "tr-TR",
    ): TmdbMovieDetailsDto

    // ==================== TV SHOWS (DİZİLER) ====================

    /**
     * Dizi adına göre TMDB'de arama yapar
     */
    @GET("search/tv")
    suspend fun searchTvShow(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String,
        @Query("region") region: String? = null,
        @Query("first_air_date_year") year: Int? = null,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbTvSearchResultDto

    /**
     * Dizi ID'sine göre detaylı bilgi getirir (oyuncular, yaratıcı dahil)
     */
    @GET("tv/{tv_id}")
    suspend fun getTvShowDetails(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits",
        @Query("language") language: String = "tr-TR",
    ): TmdbTvShowDetailsDto
}
