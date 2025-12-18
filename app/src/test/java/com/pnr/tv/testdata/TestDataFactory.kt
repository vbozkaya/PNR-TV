package com.pnr.tv.testdata

import com.pnr.tv.db.entity.FavoriteChannelEntity
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.db.entity.PlaybackPositionEntity
import com.pnr.tv.db.entity.RecentlyWatchedEntity
import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.db.entity.TmdbCacheEntity
import com.pnr.tv.db.entity.UserAccountEntity
import com.pnr.tv.db.entity.ViewerEntity
import com.pnr.tv.network.dto.TmdbMovieDetailsDto
import com.pnr.tv.network.dto.TmdbTvShowDetailsDto

/**
 * Test data factory for creating test entities and DTOs.
 * Provides builder methods for creating test data with sensible defaults.
 */
object TestDataFactory {
    // ==================== UserAccountEntity ====================

    fun createUserAccountEntity(
        id: Int = 1,
        accountName: String = "Test Account",
        username: String = "testuser",
        password: String = "testpass",
        dns: String = "https://test.dns.com",
    ): UserAccountEntity {
        return UserAccountEntity(
            id = id,
            accountName = accountName,
            username = username,
            password = password,
            dns = dns,
        )
    }

    fun createUserAccountEntities(count: Int): List<UserAccountEntity> {
        return (1..count).map { index ->
            createUserAccountEntity(
                id = index,
                accountName = "Test Account $index",
                username = "testuser$index",
            )
        }
    }

    // ==================== MovieEntity ====================

    fun createMovieEntity(
        streamId: Int = 1,
        name: String = "Test Movie",
        streamIconUrl: String? = "https://example.com/poster.jpg",
        rating: Double? = 8.5,
        plot: String? = "Test plot",
        categoryId: String? = "1",
        added: String? = "2024-01-01",
        tmdbId: Int? = 12345,
        containerExtension: String? = "mp4",
    ): MovieEntity {
        return MovieEntity(
            streamId = streamId,
            name = name,
            streamIconUrl = streamIconUrl,
            rating = rating,
            plot = plot,
            categoryId = categoryId,
            added = added,
            tmdbId = tmdbId,
            containerExtension = containerExtension,
        )
    }

    fun createMovieEntities(count: Int): List<MovieEntity> {
        return (1..count).map { index ->
            createMovieEntity(
                streamId = index,
                name = "Test Movie $index",
                tmdbId = 10000 + index,
            )
        }
    }

    // ==================== SeriesEntity ====================

    fun createSeriesEntity(
        streamId: Int = 1,
        name: String = "Test Series",
        coverUrl: String? = "https://example.com/cover.jpg",
        rating: Double? = 9.0,
        plot: String? = "Test plot",
        releaseDate: String? = "2024-01-01",
        categoryId: String? = "2",
        added: String? = "2024-01-01",
        tmdbId: Int? = 54321,
    ): SeriesEntity {
        return SeriesEntity(
            streamId = streamId,
            name = name,
            coverUrl = coverUrl,
            rating = rating,
            plot = plot,
            releaseDate = releaseDate,
            categoryId = categoryId,
            added = added,
            tmdbId = tmdbId,
        )
    }

    fun createSeriesEntities(count: Int): List<SeriesEntity> {
        return (1..count).map { index ->
            createSeriesEntity(
                streamId = index,
                name = "Test Series $index",
                tmdbId = 20000 + index,
            )
        }
    }

    // ==================== LiveStreamEntity ====================

    fun createLiveStreamEntity(
        streamId: Int = 1,
        name: String = "Test Channel",
        streamIconUrl: String? = "https://example.com/icon.jpg",
        categoryId: Int? = 1,
        categoryName: String? = "Sports",
    ): LiveStreamEntity {
        return LiveStreamEntity(
            streamId = streamId,
            name = name,
            streamIconUrl = streamIconUrl,
            categoryId = categoryId,
            categoryName = categoryName,
        )
    }

    fun createLiveStreamEntities(count: Int): List<LiveStreamEntity> {
        return (1..count).map { index ->
            createLiveStreamEntity(
                streamId = index,
                name = "Test Channel $index",
                categoryId = (index % 5) + 1,
            )
        }
    }

    // ==================== ViewerEntity ====================

    fun createViewerEntity(
        id: Int = 1,
        name: String = "Test Viewer",
        userId: Int = 1,
        isDeletable: Boolean = true,
    ): ViewerEntity {
        return ViewerEntity(
            id = id,
            name = name,
            userId = userId,
            isDeletable = isDeletable,
        )
    }

    fun createViewerEntities(count: Int): List<ViewerEntity> {
        // First viewer is not deletable
        return (1..count).map { index ->
            createViewerEntity(
                id = index,
                name = "Test Viewer $index",
                isDeletable = index > 1,
            )
        }
    }

    // ==================== FavoriteChannelEntity ====================

    fun createFavoriteChannelEntity(
        channelId: Int = 1,
        viewerId: Int = 1,
        userId: Int = 1,
    ): FavoriteChannelEntity {
        return FavoriteChannelEntity(
            channelId = channelId,
            viewerId = viewerId,
            userId = userId,
        )
    }

    // ==================== PlaybackPositionEntity ====================

    fun createPlaybackPositionEntity(
        contentId: String = "movie_1",
        userId: Int = 1,
        // 30 seconds
        positionMs: Long = 30000L,
        // 2 minutes
        durationMs: Long = 120000L,
        lastUpdated: Long = System.currentTimeMillis(),
    ): PlaybackPositionEntity {
        return PlaybackPositionEntity(
            contentId = contentId,
            userId = userId,
            positionMs = positionMs,
            durationMs = durationMs,
            lastUpdated = lastUpdated,
        )
    }

    // ==================== RecentlyWatchedEntity ====================

    fun createRecentlyWatchedEntity(
        channelId: Int = 1,
        userId: Int = 1,
        watchedAt: Long = System.currentTimeMillis(),
    ): RecentlyWatchedEntity {
        return RecentlyWatchedEntity(
            channelId = channelId,
            userId = userId,
            watchedAt = watchedAt,
        )
    }

    // ==================== TmdbCacheEntity ====================

    fun createTmdbCacheEntity(
        tmdbId: Int = 12345,
        title: String = "Test Movie",
        director: String? = "Test Director",
        cast: String? = "Actor 1, Actor 2, Actor 3",
        overview: String? = "Test overview",
        cacheTime: Long = System.currentTimeMillis(),
    ): TmdbCacheEntity {
        return TmdbCacheEntity(
            tmdbId = tmdbId,
            title = title,
            director = director,
            cast = cast,
            overview = overview,
            cacheTime = cacheTime,
        )
    }

    // ==================== TMDB DTOs ====================

    fun createTmdbMovieDetailsDto(
        id: Int? = 12345,
        title: String? = "Test Movie",
        overview: String? = "Test overview",
        originalLanguage: String? = "en",
        genres: List<com.pnr.tv.network.dto.TmdbGenreDto>? =
            listOf(
                com.pnr.tv.network.dto.TmdbGenreDto(id = 1, name = "Action"),
                com.pnr.tv.network.dto.TmdbGenreDto(id = 2, name = "Thriller"),
            ),
        credits: com.pnr.tv.network.dto.TmdbCreditsDto? = createTmdbMovieCredits(),
    ): TmdbMovieDetailsDto {
        return TmdbMovieDetailsDto(
            id = id,
            title = title,
            overview = overview,
            originalLanguage = originalLanguage,
            genres = genres,
            credits = credits,
        )
    }

    fun createTmdbMovieCredits(
        cast: List<com.pnr.tv.network.dto.TmdbCastDto>? =
            listOf(
                com.pnr.tv.network.dto.TmdbCastDto(name = "Actor 1", character = null, order = 0),
                com.pnr.tv.network.dto.TmdbCastDto(name = "Actor 2", character = null, order = 1),
            ),
        crew: List<com.pnr.tv.network.dto.TmdbCrewDto>? =
            listOf(
                com.pnr.tv.network.dto.TmdbCrewDto(name = "Director 1", job = "Director", department = null),
            ),
    ): com.pnr.tv.network.dto.TmdbCreditsDto {
        return com.pnr.tv.network.dto.TmdbCreditsDto(
            cast = cast,
            crew = crew,
        )
    }

    fun createTmdbTvShowDetailsDto(
        id: Int? = 54321,
        name: String? = "Test Series",
        overview: String? = "Test overview",
        originalLanguage: String? = "en",
        genres: List<com.pnr.tv.network.dto.TmdbGenreDto>? =
            listOf(
                com.pnr.tv.network.dto.TmdbGenreDto(id = 1, name = "Drama"),
            ),
        createdBy: List<com.pnr.tv.network.dto.TmdbCreatorDto>? =
            listOf(
                com.pnr.tv.network.dto.TmdbCreatorDto(id = 1, name = "Creator 1", gender = null, profilePath = null),
            ),
        credits: com.pnr.tv.network.dto.TmdbCreditsDto? = createTmdbTvShowCredits(),
    ): TmdbTvShowDetailsDto {
        return TmdbTvShowDetailsDto(
            id = id,
            name = name,
            overview = overview,
            originalLanguage = originalLanguage,
            genres = genres,
            createdBy = createdBy,
            credits = credits,
        )
    }

    fun createTmdbTvShowCredits(
        cast: List<com.pnr.tv.network.dto.TmdbCastDto>? =
            listOf(
                com.pnr.tv.network.dto.TmdbCastDto(name = "Actor 1", character = null, order = 0),
            ),
    ): com.pnr.tv.network.dto.TmdbCreditsDto {
        return com.pnr.tv.network.dto.TmdbCreditsDto(
            cast = cast,
            crew = null,
        )
    }
}
