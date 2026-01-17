package com.pnr.tv.repository

import com.pnr.tv.core.constants.TmdbImageHelper
import com.pnr.tv.db.entity.TmdbCacheEntity
import com.pnr.tv.network.dto.TmdbTvShowDetailsDto
import javax.inject.Inject

/**
 * TMDB TV veri dönüştürme ve formatlama işlemleri için mapper sınıfı
 * DTO ve Entity arasındaki dönüşümleri yönetir
 */
class TmdbTvDataMapper
    @Inject
    constructor() {
        /**
         * Cache entity'sini TV DTO'ya çevirir
         */
        fun cacheToDtoTv(cache: TmdbCacheEntity): TmdbTvShowDetailsDto {
            return TmdbTvShowDetailsDto(
                id = cache.tmdbId,
                name = cache.title,
                overview = cache.overview,
                originalLanguage = null,
                posterPath = null,
                backdropPath = null,
                voteAverage = null,
                voteCount = null,
                genres = null,
                createdBy = null,
                credits = null,
            )
        }

        /**
         * İki dizi detay DTO'sunu birleştirir
         */
        fun mergeTvShowDetails(
            primary: TmdbTvShowDetailsDto?,
            fallback: TmdbTvShowDetailsDto,
        ): TmdbTvShowDetailsDto {
            if (primary == null) return fallback

            return TmdbTvShowDetailsDto(
                id = primary.id ?: fallback.id,
                name = primary.name ?: fallback.name,
                overview = primary.overview.takeIf { !it.isNullOrBlank() } ?: fallback.overview,
                originalLanguage = primary.originalLanguage ?: fallback.originalLanguage,
                posterPath = primary.posterPath ?: fallback.posterPath,
                backdropPath = primary.backdropPath ?: fallback.backdropPath,
                voteAverage = primary.voteAverage ?: fallback.voteAverage,
                voteCount = primary.voteCount ?: fallback.voteCount,
                genres = primary.genres ?: fallback.genres,
                createdBy = primary.createdBy ?: fallback.createdBy,
                credits = primary.credits ?: fallback.credits,
            )
        }

        /**
         * Dizi türlerini formatlı string olarak döndürür
         */
        fun getGenresFromTv(tvDetails: TmdbTvShowDetailsDto?): String? {
            val genres =
                tvDetails?.genres
                    ?.mapNotNull { it.name }
                    ?.joinToString(", ")
            return if (genres.isNullOrBlank()) null else genres
        }

        /**
         * Cache'e kaydetmek için dizi oyuncu listesini string'e çevirir
         */
        fun getCastForCacheTv(tvDetails: TmdbTvShowDetailsDto?): String? {
            val cast =
                tvDetails?.credits?.cast
                    ?.sortedBy { it.order }
                    ?.take(5)
                    ?.mapNotNull { it.name }
                    ?.joinToString(", ")
            return if (cast.isNullOrEmpty()) null else cast
        }

        /**
         * Dizi yaratıcısını döndürür (director yerine)
         */
        fun getCreator(tvDetails: TmdbTvShowDetailsDto?): String? {
            return tvDetails?.createdBy?.firstOrNull()?.name
        }

        /**
         * Dizi oyuncularını döndürür
         */
        fun getCastFromTv(tvDetails: TmdbTvShowDetailsDto?): List<String>? {
            val castList =
                tvDetails?.credits?.cast
                    ?.sortedBy { it.order }
                    ?.take(5)
                    ?.mapNotNull { it.name }
            return if (castList.isNullOrEmpty()) null else castList
        }

        /**
         * Dizi açıklamasını döndürür
         */
        fun getOverviewFromTv(tvDetails: TmdbTvShowDetailsDto?): String? {
            return tvDetails?.overview?.takeIf { it.isNotBlank() }
        }

        /**
         * TMDB'den gelen dizi poster görsel URL'ini döndürür
         */
        fun getPosterUrlFromTv(tvDetails: TmdbTvShowDetailsDto?): String? {
            return TmdbImageHelper.getPosterUrl(tvDetails?.posterPath)
        }

        /**
         * TMDB'den gelen dizi backdrop görsel URL'ini döndürür
         */
        fun getBackdropUrlFromTv(tvDetails: TmdbTvShowDetailsDto?): String? {
            return TmdbImageHelper.getBackdropUrl(tvDetails?.backdropPath)
        }

        /**
         * TMDB'den gelen dizi rating'ini döndürür
         */
        fun getRatingFromTv(tvDetails: TmdbTvShowDetailsDto?): Double? {
            return tvDetails?.voteAverage
        }
    }
