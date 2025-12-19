package com.pnr.tv.ui.series

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.network.dto.EpisodeDto
import com.pnr.tv.network.dto.SeriesDetailInfoDto
import com.pnr.tv.network.dto.SeriesInfoDto
import com.pnr.tv.network.dto.TmdbTvShowDetailsDto
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.Result
import com.pnr.tv.repository.TmdbRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: SeriesDetailViewModel
    private val mockContentRepository: ContentRepository = mock()
    private val mockTmdbRepository: TmdbRepository = mock()
    private val mockWatchedEpisodeDao: com.pnr.tv.db.dao.WatchedEpisodeDao = mock()
    private val mockViewerRepository: com.pnr.tv.repository.ViewerRepository = mock()
    private val mockSessionManager: com.pnr.tv.SessionManager = mock()
    private val mockContext: Context = mock()

    @Before
    fun setup() {
        // Mock context string resources
        whenever(mockContext.getString(any())).thenReturn("Mock String")
        whenever(mockContext.getString(any(), any())).thenReturn("Mock String")
        // Mock getString with 3 parameters (for season_format_with_episodes)
        whenever(mockContext.getString(any(), any(), any())).thenReturn("Mock String")
        // Mock sessionManager - getCurrentUserId returns null (no user logged in)
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    private fun createViewModel(): SeriesDetailViewModel {
        return SeriesDetailViewModel(
            contentRepository = mockContentRepository,
            tmdbRepository = mockTmdbRepository,
            watchedEpisodeDao = mockWatchedEpisodeDao,
            viewerRepository = mockViewerRepository,
            sessionManager = mockSessionManager,
            context = mockContext,
        )
    }

    /**
     * Test: loadSeries başarılı - temel bilgiler ve sezonlar doğru yüklenir
     */
    @Test
    fun `loadSeries should update series, seasons and episodes on success`() =
        runTest {
            // Given
            val seriesId = 123
            val mockSeriesInfo = createMockSeriesInfo()
            // Mock suspend function - use runBlocking for suspend functions
            kotlinx.coroutines.runBlocking {
                whenever(mockContentRepository.getSeriesInfo(seriesId))
                    .thenReturn(Result.Success(mockSeriesInfo))
            }
            
            // Mock watchedEpisodeDao for selectSeason
            kotlinx.coroutines.runBlocking {
                whenever(mockWatchedEpisodeDao.getWatchedEpisode(any(), any())).thenReturn(null)
            }

            viewModel = createViewModel()

            // When
            viewModel.loadSeries(seriesId)
            advanceUntilIdle()

            // Then - Series (StateFlow'un güncel değerini al)
            val series = viewModel.series.value
            assertNotNull("Series should not be null", series)
            assertEquals("Breaking Bad", series?.name)
            assertEquals(123, series?.streamId)

            // Then - Seasons
            val seasons = viewModel.seasons.value
            assertEquals(2, seasons.size)
            assertEquals(1, seasons[0].seasonNumber)
            assertEquals(2, seasons[1].seasonNumber)

            // Then - Episodes (İlk sezon otomatik seçilir)
            val episodes = viewModel.episodes.value
            assertEquals(2, episodes.size)
            assertEquals(1, episodes[0].episodeNumber)
            assertEquals(2, episodes[1].episodeNumber)
        }

    /**
     * Test: Bölüm başlığından cleanTitle doğru ayrıştırılır (S01E01 - Pilot → "Pilot")
     */
    @Test
    fun `loadSeries should parse cleanTitle correctly from episode title`() =
        runTest {
            // Given
            val seriesId = 123
            val mockSeriesInfo = createMockSeriesInfo()
            runBlocking {
                whenever(mockContentRepository.getSeriesInfo(seriesId))
                    .thenReturn(Result.Success(mockSeriesInfo))
            }

            viewModel = createViewModel()

            // When
            viewModel.loadSeries(seriesId)
            advanceUntilIdle()

            // Then
            val episodes = viewModel.episodes.value
            assertTrue("Episodes should not be empty", episodes.isNotEmpty())
            
            // "S01E01 - Pilot" → cleanTitle = "Pilot"
            assertEquals("Pilot", episodes[0].cleanTitle)

            // "S01E02: Cat's in the Bag..." → cleanTitle = "Cat's in the Bag..."
            assertEquals("Cat's in the Bag...", episodes[1].cleanTitle)
        }

    /**
     * Test: selectSeason() çağrıldığında doğru sezon bölümleri gösterilir
     */
    @Test
    fun `selectSeason should update episodes with correct season episodes`() =
        runTest {
            // Given
            val seriesId = 123
            val mockSeriesInfo = createMockSeriesInfo()
            runBlocking {
                whenever(mockContentRepository.getSeriesInfo(seriesId))
                    .thenReturn(Result.Success(mockSeriesInfo))
            }

            viewModel = createViewModel()
            viewModel.loadSeries(seriesId)
            advanceUntilIdle()

            // When - Sezon 2'yi seç
            viewModel.selectSeason(2)
            advanceUntilIdle()

            // Then - Selected Season
            val selectedSeason = viewModel.selectedSeasonNumber.value
            assertEquals(2, selectedSeason)

            // Then - Episodes
            val episodes = viewModel.episodes.value
            assertEquals(1, episodes.size) // Sezon 2'de 1 bölüm var
            assertEquals(2, episodes[0].seasonNumber)
            assertEquals(1, episodes[0].episodeNumber)
        }

    /**
     * Test: API hata döndüğünde error state tetiklenir
     */
    @Test
    fun `loadSeries should emit error when repository returns Error`() =
        runTest {
            // Given
            val seriesId = 123
            val errorMessage = "API Error: Network failure"
            runBlocking {
                whenever(mockContentRepository.getSeriesInfo(seriesId))
                    .thenReturn(Result.Error(errorMessage))
            }

            viewModel = createViewModel()

            // When
            viewModel.loadSeries(seriesId)
            advanceUntilIdle()

            // Then
            val error = viewModel.error.value
            assertTrue(error.contains("yüklenemedi"))
        }

    /**
     * Test: Loading state başlangıçta false, yükleme sırasında true, sonunda false olur
     */
    @Test
    fun `loadSeries should update loading state correctly`() =
        runTest {
            // Given
            val seriesId = 123
            val mockSeriesInfo = createMockSeriesInfo()
            runBlocking {
                whenever(mockContentRepository.getSeriesInfo(seriesId))
                    .thenReturn(Result.Success(mockSeriesInfo))
            }

            viewModel = createViewModel()

            // When - Flow'u önce başlat, sonra loadSeries çağır
            viewModel.isLoading.test {
                // Başlangıç: false
                val initial = awaitItem()
                assertEquals(false, initial)

                viewModel.loadSeries(seriesId)

                // Yükleme sırasında: true
                val loading = awaitItem()
                assertEquals(true, loading)

                advanceUntilIdle()

                // Yükleme bittikten sonra: false
                val finished = awaitItem()
                assertEquals(false, finished)

                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * Test: Exception fırlatıldığında error state tetiklenir ve loading false olur
     */
    @Test
    fun `loadSeries should emit error and stop loading on exception`() =
        runTest {
            // Given
            val seriesId = 123
            runBlocking {
                whenever(mockContentRepository.getSeriesInfo(seriesId))
                    .thenThrow(RuntimeException("Unexpected error"))
            }

            viewModel = createViewModel()

            // When
            viewModel.loadSeries(seriesId)
            advanceUntilIdle()

            // Then - Error emit edilir
            val error = viewModel.error.value
            assertTrue(error.contains("beklenmeyen bir hata"))

            // Then - Loading false olur (exception sonrası)
            val isLoading = viewModel.isLoading.value
            assertEquals(false, isLoading)
        }

    /**
     * Test: getEpisodeStreamUrl doğru URL formatını döndürür
     */
    @Test
    fun `getEpisodeStreamUrl should return correct URL format`() =
        runTest {
            // Given
            viewModel = createViewModel()
            val baseUrl = "http://server.com:8080"
            val username = "testuser"
            val password = "testpass"
            val episodeId = 12345

            // When
            val streamUrl = viewModel.getEpisodeStreamUrl(baseUrl, username, password, episodeId, "ts")

            // Then
            assertEquals("http://server.com:8080/series/testuser/testpass/12345.ts", streamUrl)
        }

    /**
     * Test: TMDB detayları başarıyla yüklenir
     */
    @Test
    fun `loadSeries should fetch TMDB details when tmdbId is available`() =
        runTest {
            // Given
            val seriesId = 123
            val mockSeriesInfo = createMockSeriesInfo()
            val mockTmdbDetails =
                TmdbTvShowDetailsDto(
                    id = 1396,
                    name = "Breaking Bad",
                    overview = "A high school chemistry teacher...",
                    originalLanguage = "en",
                    genres = null,
                    createdBy = null,
                    credits = null,
                )

            runBlocking {
                whenever(mockContentRepository.getSeriesInfo(seriesId))
                    .thenReturn(Result.Success(mockSeriesInfo))
                whenever(mockTmdbRepository.getTvShowDetailsById(1396))
                    .thenReturn(mockTmdbDetails)
            }

            viewModel = createViewModel()

            // When
            viewModel.loadSeries(seriesId)
            advanceUntilIdle()

            // Then - Verify mock çağrısı
            runBlocking {
                verify(mockTmdbRepository).getTvShowDetailsById(1396)
            }

            // Then - TMDB details
            val details = viewModel.tmdbDetails.value
            assertNotNull(details)
            assertEquals("Breaking Bad", details?.name)
        }

    /**
     * Test: Sezon/bölüm olmayan dizi için empty list döner
     */
    @Test
    fun `loadSeries should handle empty episodes list gracefully`() =
        runTest {
            // Given
            val seriesId = 123
            val emptySeriesInfo =
                SeriesInfoDto(
                    seasons = emptyList(),
                    episodes = emptyMap(),
                    info =
                        SeriesDetailInfoDto(
                            name = "Empty Series",
                            coverUrl = null,
                            plot = null,
                            cast = null,
                            director = null,
                            genre = null,
                            releaseDate = null,
                            lastModified = null,
                            rating = null,
                            rating5based = null,
                            tmdb = null,
                            backdropPath = null,
                            youtubeTrailer = null,
                            episodeRunTime = null,
                            categoryId = null,
                        ),
                )

            runBlocking {
                whenever(mockContentRepository.getSeriesInfo(seriesId))
                    .thenReturn(Result.Success(emptySeriesInfo))
            }

            viewModel = createViewModel()

            // When
            viewModel.loadSeries(seriesId)
            advanceUntilIdle()

            // Then
            val seasons = viewModel.seasons.value
            assertTrue(seasons.isEmpty())

            val episodes = viewModel.episodes.value
            assertTrue(episodes.isEmpty())
        }

    // ═══════════════════════════════════════════════════════════
    // Helper Functions - Mock Data Oluşturma
    // ═══════════════════════════════════════════════════════════

    private fun createMockSeriesInfo(): SeriesInfoDto {
        return SeriesInfoDto(
            seasons = emptyList(),
            episodes =
                mapOf(
                    "1" to
                        listOf(
                            EpisodeDto(
                                id = "1001",
                                episodeNumber = 1,
                                title = "S01E01 - Pilot",
                                containerExtension = "ts",
                                info = null,
                                customSid = null,
                                added = null,
                                season = 1,
                                directSource = null,
                            ),
                            EpisodeDto(
                                id = "1002",
                                episodeNumber = 2,
                                title = "S01E02: Cat's in the Bag...",
                                containerExtension = "ts",
                                info = null,
                                customSid = null,
                                added = null,
                                season = 1,
                                directSource = null,
                            ),
                        ),
                    "2" to
                        listOf(
                            EpisodeDto(
                                id = "2001",
                                episodeNumber = 1,
                                title = "S02E01 - Seven Thirty-Seven",
                                containerExtension = "ts",
                                info = null,
                                customSid = null,
                                added = null,
                                season = 2,
                                directSource = null,
                            ),
                        ),
                ),
            info =
                SeriesDetailInfoDto(
                    name = "Breaking Bad",
                    coverUrl = "http://example.com/poster.jpg",
                    plot = "A chemistry teacher turns to crime",
                    cast = "Bryan Cranston, Aaron Paul",
                    director = "Vince Gilligan",
                    genre = "Drama, Crime",
                    releaseDate = "2008",
                    lastModified = null,
                    rating = "9.5",
                    rating5based = null,
                    tmdb = "1396",
                    backdropPath = null,
                    youtubeTrailer = null,
                    episodeRunTime = null,
                    categoryId = "5",
                ),
        )
    }
}
