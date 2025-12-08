package com.pnr.tv.network

import com.pnr.tv.network.dto.AuthenticationResponseDto
import com.pnr.tv.network.dto.LiveStreamCategoryDto
import com.pnr.tv.network.dto.LiveStreamDto
import com.pnr.tv.network.dto.MovieCategoryDto
import com.pnr.tv.network.dto.MovieDto
import com.pnr.tv.network.dto.SeriesCategoryDto
import com.pnr.tv.network.dto.SeriesDto
import com.pnr.tv.network.dto.SeriesInfoDto
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("player_api.php")
    suspend fun getMovies(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String,
    ): List<MovieDto>

    @GET("player_api.php")
    suspend fun getMovieCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories",
    ): List<MovieCategoryDto>

    @GET("player_api.php")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String,
    ): List<SeriesDto>

    @GET("player_api.php")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories",
    ): List<SeriesCategoryDto>

    @GET("player_api.php")
    suspend fun getSeriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: Int,
    ): SeriesInfoDto

    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String,
    ): List<LiveStreamDto>

    @GET("player_api.php")
    suspend fun getLiveStreamCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String,
    ): List<LiveStreamCategoryDto>

    @GET("player_api.php")
    suspend fun getUserInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        // Bazı IPTV API'leri action parametresi olmadan kullanıcı bilgilerini döndürür
        // @Query("action") action: String = "get_user_info",
    ): AuthenticationResponseDto
}
