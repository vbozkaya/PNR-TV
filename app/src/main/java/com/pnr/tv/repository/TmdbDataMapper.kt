package com.pnr.tv.repository

import com.pnr.tv.core.constants.TmdbImageHelper
import com.pnr.tv.db.entity.TmdbCacheEntity
import com.pnr.tv.network.dto.TmdbMovieDetailsDto
import javax.inject.Inject

/**
 * TMDB veri dönüştürme ve formatlama işlemleri için mapper sınıfı
 * DTO ve Entity arasındaki dönüşümleri yönetir
 */
class TmdbDataMapper
    @Inject
    constructor() {
        /**
         * Cache entity'sini DTO'ya çevirir
         */
        fun cacheToDto(cache: TmdbCacheEntity): TmdbMovieDetailsDto {
            return TmdbMovieDetailsDto(
                id = cache.tmdbId,
                title = cache.title,
                overview = cache.overview,
                // Cache'de saklanmıyor
                originalLanguage = null,
                // Cache'de saklanmıyor
                posterPath = null,
                // Cache'de saklanmıyor
                backdropPath = null,
                // Cache'de saklanmıyor
                voteAverage = null,
                // Cache'de saklanmıyor
                voteCount = null,
                // Cache'de saklanmıyor
                genres = null,
                credits = null,
            )
        }

        /**
         * İki film detay DTO'sunu birleştirir
         * İlk DTO'daki boş alanları ikinci DTO'dan doldurur
         */
        fun mergeMovieDetails(
            primary: TmdbMovieDetailsDto?,
            fallback: TmdbMovieDetailsDto,
        ): TmdbMovieDetailsDto {
            if (primary == null) return fallback

            return TmdbMovieDetailsDto(
                id = primary.id ?: fallback.id,
                title = primary.title ?: fallback.title,
                overview = primary.overview.takeIf { !it.isNullOrBlank() } ?: fallback.overview,
                originalLanguage = primary.originalLanguage ?: fallback.originalLanguage,
                posterPath = primary.posterPath ?: fallback.posterPath,
                backdropPath = primary.backdropPath ?: fallback.backdropPath,
                voteAverage = primary.voteAverage ?: fallback.voteAverage,
                voteCount = primary.voteCount ?: fallback.voteCount,
                genres = primary.genres ?: fallback.genres,
                credits = primary.credits ?: fallback.credits,
            )
        }

        /**
         * Film türlerini formatlı string olarak döndürür
         * @return Virgülle ayrılmış tür listesi (örn: "Action, Sci-Fi, Thriller")
         */
        fun getGenres(movieDetails: TmdbMovieDetailsDto?): String? {
            val genres =
                movieDetails?.genres
                    ?.mapNotNull { it.name }
                    ?.joinToString(", ")
            return if (genres.isNullOrBlank()) null else genres
        }

        /**
         * Film detaylarından yönetmen bilgisini çıkarır (DTO'dan)
         */
        fun getDirectorFromDto(movieDetails: TmdbMovieDetailsDto?): String? {
            return movieDetails?.credits?.crew?.firstOrNull { it.job == "Director" }?.name
        }

        /**
         * Film detaylarından açıklamayı çıkarır (DTO'dan)
         */
        fun getOverviewFromDto(movieDetails: TmdbMovieDetailsDto?): String? {
            return movieDetails?.overview?.takeIf { it.isNotBlank() }
        }

        /**
         * Cache'e kaydetmek için oyuncu listesini string'e çevirir
         */
        fun getCastForCache(movieDetails: TmdbMovieDetailsDto?): String? {
            val cast =
                movieDetails?.credits?.cast
                    ?.sortedBy { it.order }
                    ?.take(5)
                    ?.mapNotNull { it.name }
                    ?.joinToString(", ")

            return if (cast.isNullOrEmpty()) null else cast
        }

        /**
         * TMDB'den gelen poster görsel URL'ini döndürür
         * @return Tam poster URL'i veya null
         */
        fun getPosterUrl(movieDetails: TmdbMovieDetailsDto?): String? {
            return TmdbImageHelper.getPosterUrl(movieDetails?.posterPath)
        }

        /**
         * TMDB'den gelen backdrop görsel URL'ini döndürür
         * @return Tam backdrop URL'i veya null
         */
        fun getBackdropUrl(movieDetails: TmdbMovieDetailsDto?): String? {
            return TmdbImageHelper.getBackdropUrl(movieDetails?.backdropPath)
        }

        /**
         * TMDB'den gelen rating'i döndürür
         * @return Rating (0-10 arası) veya null
         */
        fun getRating(movieDetails: TmdbMovieDetailsDto?): Double? {
            return movieDetails?.voteAverage
        }
    }
