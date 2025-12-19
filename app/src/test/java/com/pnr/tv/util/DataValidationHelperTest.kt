package com.pnr.tv.util

import com.pnr.tv.network.dto.LiveStreamDto
import com.pnr.tv.network.dto.MovieDto
import com.pnr.tv.network.dto.SeriesDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataValidationHelperTest {
    @Test
    fun `validateMovies should return empty report for empty list`() {
        // When
        val report = DataValidationHelper.validateMovies(emptyList())

        // Then
        assertEquals(0, report.totalCount)
        assertEquals(0, report.getMostMissingFields().size)
    }

    @Test
    fun `validateMovies should detect missing critical fields`() {
        // Given
        val movies = listOf(
            MovieDto(
                streamId = null,
                num = null,
                name = null,
                streamIconUrl = null,
                rating = null,
                plot = null,
                categoryId = null,
                rating5based = null,
                backdropPath = null,
                streamType = null,
                tmdb = null,
                trailer = null,
                added = null,
                isAdult = null,
                categoryIds = null,
                containerExtension = null,
                customSid = null,
                directSource = null,
            ),
        )

        // When
        val report = DataValidationHelper.validateMovies(movies)

        // Then
        assertEquals(1, report.totalCount)
        assertTrue(report.getMostMissingFields().isNotEmpty())
    }

    @Test
    fun `validateMovies should pass for valid movies`() {
        // Given
        val movies = listOf(
            MovieDto(
                streamId = 1,
                num = null,
                name = "Test Movie",
                streamIconUrl = "http://example.com/icon.jpg",
                rating = "8.5",
                plot = "Test plot",
                categoryId = "1",
                rating5based = null,
                backdropPath = null,
                streamType = null,
                tmdb = "123",
                trailer = null,
                added = null,
                isAdult = null,
                categoryIds = null,
                containerExtension = "mp4",
                customSid = null,
                directSource = null,
            ),
        )

        // When
        val report = DataValidationHelper.validateMovies(movies)

        // Then
        assertEquals(1, report.totalCount)
        // Should have no critical missing fields
        val criticalMissing = report.getMostMissingFields().filter { it.first in listOf("streamId/num", "name") }
        assertTrue(criticalMissing.isEmpty() || criticalMissing.all { it.second == 0 })
    }

    @Test
    fun `validateSeries should detect missing critical fields`() {
        // Given
        val series = listOf(
            SeriesDto(
                seriesId = null,
                name = null,
                coverUrl = null,
                plot = null,
                releaseDate = null,
                lastModified = null,
                rating = null,
                rating5based = null,
                backdropPath = null,
                youtubeTrailer = null,
                episodeRunTime = null,
                categoryId = null,
                added = null,
                tmdb = null,
            ),
        )

        // When
        val report = DataValidationHelper.validateSeries(series)

        // Then
        assertEquals(1, report.totalCount)
        assertTrue(report.getMostMissingFields().isNotEmpty())
    }

    @Test
    fun `validateSeries should pass for valid series`() {
        // Given
        val series = listOf(
            SeriesDto(
                seriesId = 1,
                name = "Test Series",
                coverUrl = "http://example.com/cover.jpg",
                plot = "Test plot",
                releaseDate = "2024-01-01",
                lastModified = null,
                rating = "9.0",
                rating5based = null,
                backdropPath = null,
                youtubeTrailer = null,
                episodeRunTime = null,
                categoryId = "1",
                added = null,
                tmdb = "456",
            ),
        )

        // When
        val report = DataValidationHelper.validateSeries(series)

        // Then
        assertEquals(1, report.totalCount)
    }

    @Test
    fun `validateLiveStreams should detect missing critical fields`() {
        // Given
        val streams = listOf(
            LiveStreamDto(
                streamId = null,
                name = null,
                streamIconUrl = null,
                categoryId = null,
                categoryName = null,
            ),
        )

        // When
        val report = DataValidationHelper.validateLiveStreams(streams)

        // Then
        assertEquals(1, report.totalCount)
        assertTrue(report.getMostMissingFields().isNotEmpty())
    }

    @Test
    fun `validateLiveStreams should pass for valid streams`() {
        // Given
        val streams = listOf(
            LiveStreamDto(
                streamId = 1,
                name = "Test Stream",
                streamIconUrl = "http://example.com/icon.jpg",
                categoryId = "1",
                categoryName = null,
            ),
        )

        // When
        val report = DataValidationHelper.validateLiveStreams(streams)

        // Then
        assertEquals(1, report.totalCount)
    }

    @Test
    fun `getFieldMissingPercentage should return correct percentage`() {
        // Given
        val movies = listOf(
            MovieDto(
                streamId = 1,
                num = null,
                name = "Movie 1",
                streamIconUrl = null, // Missing
                rating = null,
                plot = null,
                categoryId = "1",
                rating5based = null,
                backdropPath = null,
                streamType = null,
                tmdb = null,
                trailer = null,
                added = null,
                isAdult = null,
                categoryIds = null,
                containerExtension = null,
                customSid = null,
                directSource = null,
            ),
            MovieDto(
                streamId = 2,
                num = null,
                name = "Movie 2",
                streamIconUrl = null, // Missing
                rating = null,
                plot = null,
                categoryId = "1",
                rating5based = null,
                backdropPath = null,
                streamType = null,
                tmdb = null,
                trailer = null,
                added = null,
                isAdult = null,
                categoryIds = null,
                containerExtension = null,
                customSid = null,
                directSource = null,
            ),
        )

        // When
        val report = DataValidationHelper.validateMovies(movies)

        // Then
        val percentage = report.getFieldMissingPercentage("streamIconUrl")
        assertEquals(100, percentage) // Both missing
    }

    @Test
    fun `getMostMissingFields should return sorted list`() {
        // Given
        val movies = listOf(
            MovieDto(
                streamId = 1,
                num = null,
                name = "Movie 1",
                streamIconUrl = null,
                rating = null,
                plot = null,
                categoryId = "1",
                rating5based = null,
                backdropPath = null,
                streamType = null,
                tmdb = null,
                trailer = null,
                added = null,
                isAdult = null,
                categoryIds = null,
                containerExtension = null,
                customSid = null,
                directSource = null,
            ),
        )

        // When
        val report = DataValidationHelper.validateMovies(movies)
        val mostMissing = report.getMostMissingFields(3)

        // Then
        assertTrue(mostMissing.isNotEmpty())
        // Should be sorted by count descending
        for (i in 0 until mostMissing.size - 1) {
            assertTrue(mostMissing[i].second >= mostMissing[i + 1].second)
        }
    }
}

