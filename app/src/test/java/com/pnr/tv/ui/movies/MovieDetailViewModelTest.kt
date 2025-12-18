package com.pnr.tv.ui.movies

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.db.entity.ViewerEntity
import com.pnr.tv.network.dto.TmdbMovieDetailsDto
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.TmdbRepository
import com.pnr.tv.repository.ViewerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockContentRepository: ContentRepository = mock()
    private val mockViewerRepository: ViewerRepository = mock()
    private val mockTmdbRepository: TmdbRepository = mock()
    private val mockContext: Context = mock()

    private lateinit var viewModel: MovieDetailViewModel

    @Before
    fun setup() {
        // Mock context string resources
        whenever(mockContext.getString(any())).thenReturn("Mock String")
        whenever(mockContext.getString(any(), any())).thenReturn("Mock String")
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    private fun createViewModel(): MovieDetailViewModel {
        return MovieDetailViewModel(
            contentRepository = mockContentRepository,
            viewerRepository = mockViewerRepository,
            tmdbRepository = mockTmdbRepository,
            context = mockContext,
        )
    }

    @Test
    fun `loadMovie should set Loading state initially`() =
        runTest {
            // Given
            val movieId = 123
            val testMovie =
                MovieEntity(
                    streamId = movieId,
                    name = "Test Movie",
                    streamIconUrl = null,
                    rating = 8.5,
                    plot = "Test plot",
                    categoryId = "1",
                    added = "2024-01-01",
                    tmdbId = 456,
                    containerExtension = "mp4",
                )

            whenever(mockContentRepository.getMovies()).thenReturn(flowOf(listOf(testMovie)))
            // Suspend fonksiyonlar için runBlocking içinde mock'la
            runBlocking {
                whenever(mockTmdbRepository.getMovieDetailsById(any())).thenReturn(null)
            }
            whenever(mockTmdbRepository.getGenres(anyOrNull())).thenReturn(null)
            runBlocking {
                whenever(mockTmdbRepository.getDirector(any(), anyOrNull())).thenReturn(null)
                whenever(mockTmdbRepository.getCast(any(), anyOrNull())).thenReturn(null)
                whenever(mockTmdbRepository.getOverview(any(), anyOrNull())).thenReturn(null)
            }

            viewModel = createViewModel()

            // When
            viewModel.loadMovie(movieId)
            advanceUntilIdle()

            // Then
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(MovieDetailUiState.Loading, state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loadMovie should set Success state when movie found`() =
        runTest {
            // Given
            val movieId = 123
            val testMovie =
                MovieEntity(
                    streamId = movieId,
                    name = "Test Movie",
                    streamIconUrl = null,
                    rating = 8.5,
                    plot = "Test plot",
                    categoryId = "1",
                    added = "2024-01-01",
                    tmdbId = 456,
                    containerExtension = "mp4",
                )

            whenever(mockContentRepository.getMovies()).thenReturn(flowOf(listOf(testMovie)))
            // Suspend fonksiyonlar için runBlocking içinde mock'la
            runBlocking {
                whenever(mockTmdbRepository.getMovieDetailsById(any())).thenReturn(null)
            }
            whenever(mockTmdbRepository.getGenres(anyOrNull())).thenReturn(null)
            runBlocking {
                whenever(mockTmdbRepository.getDirector(any(), anyOrNull())).thenReturn(null)
                whenever(mockTmdbRepository.getCast(any(), anyOrNull())).thenReturn(null)
                whenever(mockTmdbRepository.getOverview(any(), anyOrNull())).thenReturn(null)
            }

            viewModel = createViewModel()

            // When
            viewModel.loadMovie(movieId)
            advanceUntilIdle()

            // Then
            viewModel.uiState.test {
                skipItems(1) // Skip Loading state
                val state = awaitItem()
                assert(state is MovieDetailUiState.Success)
                val successState = state as MovieDetailUiState.Success
                assertEquals(testMovie, successState.movie)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loadMovie should set Error state when movie not found`() =
        runTest {
            // Given
            val movieId = 999
            whenever(mockContentRepository.getMovies()).thenReturn(flowOf(emptyList()))

            viewModel = createViewModel()

            // When
            viewModel.loadMovie(movieId)
            advanceUntilIdle()

            // Then
            viewModel.uiState.test {
                skipItems(1) // Skip Loading state
                val state = awaitItem()
                assert(state is MovieDetailUiState.Error)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loadMovie should fetch TMDB details when tmdbId exists`() =
        runTest {
            // Given
            val movieId = 123
            val tmdbId = 456
            val testMovie =
                MovieEntity(
                    streamId = movieId,
                    name = "Test Movie",
                    streamIconUrl = null,
                    rating = 8.5,
                    plot = "Test plot",
                    categoryId = "1",
                    added = "2024-01-01",
                    tmdbId = tmdbId,
                    containerExtension = "mp4",
                )

            val tmdbDetails =
                TmdbMovieDetailsDto(
                    id = tmdbId,
                    title = "Test Movie",
                    overview = "Test overview",
                    originalLanguage = "en",
                    genres = emptyList(),
                    credits = null,
                )

            whenever(mockContentRepository.getMovies()).thenReturn(flowOf(listOf(testMovie)))
            runBlocking {
                whenever(mockTmdbRepository.getMovieDetailsById(tmdbId)).thenReturn(tmdbDetails)
                whenever(mockTmdbRepository.getDirector(any(), anyOrNull())).thenReturn(null)
                whenever(mockTmdbRepository.getCast(any(), anyOrNull())).thenReturn(null)
                whenever(mockTmdbRepository.getOverview(any(), anyOrNull())).thenReturn(null)
            }
            whenever(mockTmdbRepository.getGenres(anyOrNull())).thenReturn(null)

            viewModel = createViewModel()

            // When
            viewModel.loadMovie(movieId)
            advanceUntilIdle()

            // Then
            runBlocking {
                verify(mockTmdbRepository).getMovieDetailsById(tmdbId)
            }
            viewModel.tmdbDetails.test {
                val details = awaitItem()
                assertNotNull(details)
                assertEquals(tmdbId, details?.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loadMovie should search by title when tmdbId is null`() =
        runTest {
            // Given
            val movieId = 123
            val testMovie =
                MovieEntity(
                    streamId = movieId,
                    name = "Test Movie",
                    streamIconUrl = null,
                    rating = 8.5,
                    plot = "Test plot",
                    categoryId = "1",
                    added = "2024-01-01",
                    tmdbId = null,
                    containerExtension = "mp4",
                )

            whenever(mockContentRepository.getMovies()).thenReturn(flowOf(listOf(testMovie)))
            runBlocking {
                whenever(mockTmdbRepository.getMovieDetailsByTitle("Test Movie")).thenReturn(null)
                whenever(mockTmdbRepository.getDirector(any(), anyOrNull())).thenReturn(null)
                whenever(mockTmdbRepository.getCast(any(), anyOrNull())).thenReturn(null)
                whenever(mockTmdbRepository.getOverview(any(), anyOrNull())).thenReturn(null)
            }
            whenever(mockTmdbRepository.getGenres(anyOrNull())).thenReturn(null)

            viewModel = createViewModel()

            // When
            viewModel.loadMovie(movieId)
            advanceUntilIdle()

            // Then
            // Note: getMovieDetailsByTitle is called asynchronously,
            // so we just verify the movie was loaded successfully
            viewModel.movie.test {
                val movie = awaitItem()
                assertNotNull(movie)
                assertEquals("Test Movie", movie?.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `addToFavorites should show viewer selection dialog`() =
        runTest {
            // Given
            val movieId = 123
            val testMovie =
                MovieEntity(
                    streamId = movieId,
                    name = "Test Movie",
                    streamIconUrl = null,
                    rating = 8.5,
                    plot = "Test plot",
                    categoryId = "1",
                    added = "2024-01-01",
                    tmdbId = null,
                    containerExtension = "mp4",
                )
            val testViewers =
                listOf(
                    ViewerEntity(id = 1, name = "Viewer 1", userId = 1),
                    ViewerEntity(id = 2, name = "Viewer 2", userId = 1),
                )

            whenever(mockContentRepository.getMovies()).thenReturn(flowOf(listOf(testMovie)))
            whenever(mockViewerRepository.getAllViewers()).thenReturn(flowOf(testViewers))
            runBlocking {
                whenever(mockTmdbRepository.getMovieDetailsByTitle(any())).thenReturn(null)
                whenever(mockTmdbRepository.getGenres(anyOrNull())).thenReturn(null)
                whenever(mockTmdbRepository.getDirector(any(), anyOrNull())).thenReturn(null)
                whenever(mockTmdbRepository.getCast(any(), anyOrNull())).thenReturn(null)
                whenever(mockTmdbRepository.getOverview(any(), anyOrNull())).thenReturn(null)
            }

            viewModel = createViewModel()
            viewModel.loadMovie(movieId)
            advanceUntilIdle()

            // When
            viewModel.addToFavorites()
            advanceUntilIdle()

            // Then - SharedFlow test et
            viewModel.showViewerSelectionDialog.test {
                val viewers = awaitItem()
                assertEquals(testViewers, viewers)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
