package com.pnr.tv.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.db.dao.SeriesCategoryDao
import com.pnr.tv.db.dao.SeriesDao
import com.pnr.tv.db.entity.SeriesCategoryEntity
import com.pnr.tv.db.entity.SeriesEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Retrofit

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesRepositoryTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockRetrofitBuilder: Retrofit.Builder = mock()
    private val mockUserRepository: UserRepository = mock()
    private val mockTmdbRepository: TmdbRepository = mock()
    private val mockSeriesDao: SeriesDao = mock()
    private val mockSeriesCategoryDao: SeriesCategoryDao = mock()
    private val mockContext: android.content.Context = mock()

    private lateinit var repository: SeriesRepository

    @Before
    fun setup() {
        repository =
            SeriesRepository(
                retrofitBuilder = mockRetrofitBuilder,
                userRepository = mockUserRepository,
                tmdbRepository = mockTmdbRepository,
                seriesDao = mockSeriesDao,
                seriesCategoryDao = mockSeriesCategoryDao,
                context = mockContext,
            )
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    @Test
    fun `getSeries should return flow from seriesDao`() =
        runTest {
            // Given
            val testSeries =
                listOf(
                    SeriesEntity(
                        streamId = 1,
                        name = "Series 1",
                        coverUrl = null,
                        rating = 8.5,
                        plot = "Plot 1",
                        releaseDate = null,
                        categoryId = "1",
                        added = "2024-01-01",
                        tmdbId = 100,
                    ),
                    SeriesEntity(
                        streamId = 2,
                        name = "Series 2",
                        coverUrl = "http://example.com/icon.png",
                        rating = 9.0,
                        plot = "Plot 2",
                        releaseDate = null,
                        categoryId = "2",
                        added = "2024-01-02",
                        tmdbId = 200,
                    ),
                )
            whenever(mockSeriesDao.getAll()).thenReturn(flowOf(testSeries))

            // When
            val result = repository.getSeries()

            // Then
            result.test {
                val series = awaitItem()
                assertEquals(testSeries, series)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getSeriesByCategoryId should return flow from seriesDao`() =
        runTest {
            // Given
            val categoryId = "1"
            val testSeries =
                listOf(
                    SeriesEntity(
                        streamId = 1,
                        name = "Series 1",
                        coverUrl = null,
                        rating = 8.5,
                        plot = "Plot 1",
                        releaseDate = null,
                        categoryId = categoryId,
                        added = "2024-01-01",
                        tmdbId = 100,
                    ),
                )
            whenever(mockSeriesDao.getByCategoryId(categoryId)).thenReturn(flowOf(testSeries))

            // When
            val result = repository.getSeriesByCategoryId(categoryId)

            // Then
            result.test {
                val series = awaitItem()
                assertEquals(testSeries, series)
                assertEquals(categoryId, series.first().categoryId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getRecentlyAddedSeries should delegate to seriesDao`() =
        runTest {
            // Given
            val limit = 10
            val testSeries =
                listOf(
                    SeriesEntity(
                        streamId = 1,
                        name = "Recent Series",
                        coverUrl = null,
                        rating = 8.5,
                        plot = "Plot",
                        releaseDate = null,
                        categoryId = "1",
                        added = "2024-01-01",
                        tmdbId = 100,
                    ),
                )
            whenever(mockSeriesDao.getRecentlyAdded(limit)).thenReturn(testSeries)

            // When
            val result = repository.getRecentlyAddedSeries(limit)

            // Then
            assertEquals(testSeries, result)
            verify(mockSeriesDao).getRecentlyAdded(limit)
        }

    @Test
    fun `getSeriesCategories should return flow from seriesCategoryDao`() =
        runTest {
            // Given
            val testCategories =
                listOf(
                    SeriesCategoryEntity(
                        categoryId = "1",
                        categoryName = "Action",
                        parentId = 0,
                        sortOrder = 0,
                    ),
                    SeriesCategoryEntity(
                        categoryId = "2",
                        categoryName = "Drama",
                        parentId = 0,
                        sortOrder = 1,
                    ),
                )
            whenever(mockSeriesCategoryDao.getAll()).thenReturn(flowOf(testCategories))

            // When
            val result = repository.getSeriesCategories()

            // Then
            result.test {
                val categories = awaitItem()
                assertEquals(testCategories, categories)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getSeriesByIds should return empty list when empty ids provided`() =
        runTest {
            // Given
            val emptyIds = emptyList<Int>()

            // When
            val result = repository.getSeriesByIds(emptyIds)

            // Then
            assertTrue(result.isEmpty())
            // DAO should not be called for empty list
        }

    @Test
    fun `getSeriesByIds should delegate to seriesDao when ids provided`() =
        runTest {
            // Given
            val seriesIds = listOf(1, 2, 3)
            val testSeries =
                listOf(
                    SeriesEntity(
                        streamId = 1,
                        name = "Series 1",
                        coverUrl = null,
                        rating = 8.5,
                        plot = "Plot 1",
                        releaseDate = null,
                        categoryId = "1",
                        added = "2024-01-01",
                        tmdbId = 100,
                    ),
                    SeriesEntity(
                        streamId = 2,
                        name = "Series 2",
                        coverUrl = null,
                        rating = 9.0,
                        plot = "Plot 2",
                        releaseDate = null,
                        categoryId = "2",
                        added = "2024-01-02",
                        tmdbId = 200,
                    ),
                )
            whenever(mockSeriesDao.getByIds(seriesIds)).thenReturn(testSeries)

            // When
            val result = repository.getSeriesByIds(seriesIds)

            // Then
            assertEquals(testSeries, result)
            verify(mockSeriesDao).getByIds(seriesIds)
        }

    // Note: refreshSeries, refreshSeriesCategories, and getSeriesInfo use safeApiCall
    // which requires complex Retrofit.Builder mocking. These are better tested
    // with integration tests using MockWebServer. Unit tests focus on DAO operations.
}
