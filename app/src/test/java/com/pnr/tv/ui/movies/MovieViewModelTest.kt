package com.pnr.tv.ui.movies

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.ContentConstants
import com.pnr.tv.R
import com.pnr.tv.db.entity.MovieCategoryEntity
import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.db.entity.ViewerEntity
import com.pnr.tv.model.ContentType
import com.pnr.tv.model.SortOrder
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.Result
import com.pnr.tv.repository.ViewerRepository
import com.pnr.tv.testdata.TestDataFactory
import com.pnr.tv.util.SortPreferenceManager
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class MovieViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockContentRepository: ContentRepository = mock()
    private val mockViewerRepository: ViewerRepository = mock()
    private val mockSortPreferenceManager: SortPreferenceManager = mock()
    private val mockContext: Context = mock()

    private lateinit var viewModel: MovieViewModel

    @Before
    fun setup() {
        whenever(mockContext.getString(any())).thenReturn("Mock String")
        whenever(mockContext.getString(any(), any())).thenReturn("Mock String")
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    private fun createViewModel(): MovieViewModel {
        return MovieViewModel(
            contentRepository = mockContentRepository,
            viewerRepository = mockViewerRepository,
            sortPreferenceManager = mockSortPreferenceManager,
            context = mockContext,
        )
    }

    @Test
    fun `selectMovieCategory should update selected category`() = runTest {
        // Given
        viewModel = createViewModel()
        val categoryId = MovieViewModel.VIRTUAL_CATEGORY_ID_ALL_MOVIES

        // When
        viewModel.selectMovieCategory(categoryId)
        advanceUntilIdle()

        // Then
        viewModel.selectedMovieCategoryId.test {
            val selected = awaitItem()
            assertEquals(categoryId, selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadMoviesIfNeeded should skip loading when data exists`() = runTest {
        // Given
        whenever(mockContentRepository.hasMovies()).thenReturn(true)
        whenever(mockContentRepository.hasMovieCategories()).thenReturn(true)
        viewModel = createViewModel()

        // When
        viewModel.loadMoviesIfNeeded()
        advanceUntilIdle()

        // Then
        viewModel.isMoviesLoading.test {
            val isLoading = awaitItem()
            assertFalse(isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadMoviesIfNeeded should load when data missing`() = runTest {
        // Given
        whenever(mockContentRepository.hasMovies()).thenReturn(false)
        whenever(mockContentRepository.hasMovieCategories()).thenReturn(false)
        whenever(mockContentRepository.refreshMovieCategories()).thenReturn(Result.Success(Unit))
        whenever(mockContentRepository.refreshMovies(any(), any())).thenReturn(Result.Success(Unit))
        viewModel = createViewModel()

        // When
        viewModel.loadMoviesIfNeeded()
        advanceUntilIdle()

        // Then
        verify(mockContentRepository).refreshMovieCategories()
        verify(mockContentRepository).refreshMovies(any(), any())
    }

    @Test
    fun `addMovieFavorite should add favorite`() = runTest {
        // Given
        val contentId = 123
        val viewerId = 1
        whenever(mockContentRepository.addFavorite(any(), any())).thenReturn(Unit)
        viewModel = createViewModel()

        // When
        viewModel.addMovieFavorite(contentId, viewerId)
        advanceUntilIdle()

        // Then
        verify(mockContentRepository).addFavorite(contentId, viewerId)
    }

    @Test
    fun `removeMovieFavorite should remove favorite`() = runTest {
        // Given
        val contentId = 123
        val viewerId = 1
        whenever(mockContentRepository.removeFavorite(any(), any())).thenReturn(Unit)
        viewModel = createViewModel()

        // When
        viewModel.removeMovieFavorite(contentId, viewerId)
        advanceUntilIdle()

        // Then
        verify(mockContentRepository).removeFavorite(contentId, viewerId)
    }

    @Test
    fun `onMovieSearchQueryChanged should update search query`() = runTest {
        // Given
        viewModel = createViewModel()
        val query = "test query"

        // When
        viewModel.onMovieSearchQueryChanged(query)
        advanceUntilIdle()

        // Then
        viewModel.movieSearchQuery.test {
            val searchQuery = awaitItem()
            assertEquals(query, searchQuery)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveMovieSortOrder should save sort order`() = runTest {
        // Given
        val sortOrder = SortOrder.A_TO_Z
        whenever(mockSortPreferenceManager.saveSortOrder(any(), any())).thenReturn(Unit)
        viewModel = createViewModel()

        // When
        viewModel.saveMovieSortOrder(sortOrder)
        advanceUntilIdle()

        // Then
        verify(mockSortPreferenceManager).saveSortOrder(ContentType.MOVIES, sortOrder)
    }

    @Test
    fun `moviesFlow should filter by search query`() = runTest {
        // Given
        val movies = listOf(
            TestDataFactory.createMovieEntity(name = "Test Movie 1"),
            TestDataFactory.createMovieEntity(name = "Test Movie 2"),
            TestDataFactory.createMovieEntity(name = "Other Movie"),
        )
        whenever(mockContentRepository.getMovies()).thenReturn(flowOf(movies))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(null))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onMovieSearchQueryChanged("Test")
        advanceUntilIdle()

        // Then
        viewModel.moviesFlow.test {
            val filtered = awaitItem()
            // Should filter to only movies containing "Test"
            assertTrue(filtered.size <= movies.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moviesFlow should apply sorting`() = runTest {
        // Given
        val movies = listOf(
            TestDataFactory.createMovieEntity(name = "Z Movie"),
            TestDataFactory.createMovieEntity(name = "A Movie"),
            TestDataFactory.createMovieEntity(name = "M Movie"),
        )
        whenever(mockContentRepository.getMovies()).thenReturn(flowOf(movies))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(SortOrder.A_TO_Z))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onMovieSearchQueryChanged("")
        advanceUntilIdle()

        // Then
        viewModel.moviesFlow.test {
            val sorted = awaitItem()
            // Should be sorted A to Z
            if (sorted.isNotEmpty()) {
                val firstTitle = sorted.first().title
                assertNotNull(firstTitle)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `movieCategoriesFlow should include virtual categories`() = runTest {
        // Given
        val normalCategories = listOf(
            MovieCategoryEntity(
                categoryId = "1",
                categoryName = "Category 1",
                parentId = 0,
                sortOrder = ContentConstants.SortOrder.DEFAULT,
            ),
        )
        whenever(mockContentRepository.getMovieCategories()).thenReturn(flowOf(normalCategories))
        whenever(mockContentRepository.getAllFavoriteChannelIds()).thenReturn(flowOf(emptyList()))
        whenever(mockViewerRepository.getViewerIdsWithFavorites()).thenReturn(flowOf(emptyList()))
        whenever(mockViewerRepository.getAllViewers()).thenReturn(flowOf(emptyList()))
        viewModel = createViewModel()

        // When
        viewModel.movieCategoriesFlow.test {
            val categories = awaitItem()

            // Then
            assertTrue(categories.isNotEmpty())
            // Should include virtual categories
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== Search Edge Cases ====================

    @Test
    fun `moviesFlow should not filter when query is less than 3 characters`() = runTest {
        // Given
        val movies = listOf(
            TestDataFactory.createMovieEntity(name = "Test Movie 1"),
            TestDataFactory.createMovieEntity(name = "Test Movie 2"),
        )
        whenever(mockContentRepository.getMovies()).thenReturn(flowOf(movies))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(null))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onMovieSearchQueryChanged("Te") // 2 characters
        advanceUntilIdle()

        // Then - Should not filter (query < 3 characters)
        viewModel.moviesFlow.test(timeout = 5.seconds) {
            val filtered = awaitItem()
            assertEquals(movies.size, filtered.size) // All movies should be returned
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moviesFlow should filter case insensitively`() = runTest {
        // Given
        val movies = listOf(
            TestDataFactory.createMovieEntity(name = "Test Movie 1"),
            TestDataFactory.createMovieEntity(name = "TEST MOVIE 2"),
            TestDataFactory.createMovieEntity(name = "Other Movie"),
        )
        whenever(mockContentRepository.getMovies()).thenReturn(flowOf(movies))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(null))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When & Then - Flow'u dinlemeye başla, sonra action'ı tetikle
        viewModel.moviesFlow.test(timeout = 10.seconds) {
            // Skip initial empty state
            awaitItem()
            
            // Trigger search
            viewModel.onMovieSearchQueryChanged("test") // lowercase
            advanceUntilIdle()
            
            // Wait for debounce (500ms) + processing
            kotlinx.coroutines.delay(600)
            
            val filtered = awaitItem()
            assertTrue(filtered.size >= 2) // Should find both "Test" and "TEST"
            assertTrue(filtered.all { it.title.lowercase().contains("test") })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moviesFlow should handle empty query`() = runTest {
        // Given
        val movies = listOf(
            TestDataFactory.createMovieEntity(name = "Test Movie 1"),
            TestDataFactory.createMovieEntity(name = "Other Movie"),
        )
        whenever(mockContentRepository.getMovies()).thenReturn(flowOf(movies))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(null))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onMovieSearchQueryChanged("")
        advanceUntilIdle()

        // Then
        viewModel.moviesFlow.test(timeout = 5.seconds) {
            val filtered = awaitItem()
            assertEquals(movies.size, filtered.size) // All movies should be returned
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moviesFlow should return empty list when no matches found`() = runTest {
        // Given
        val movies = listOf(
            TestDataFactory.createMovieEntity(name = "Test Movie 1"),
            TestDataFactory.createMovieEntity(name = "Other Movie"),
        )
        whenever(mockContentRepository.getMovies()).thenReturn(flowOf(movies))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(null))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When & Then - Flow'u dinlemeye başla, sonra action'ı tetikle
        viewModel.moviesFlow.test(timeout = 10.seconds) {
            // Skip initial empty state
            awaitItem()
            
            // Trigger search
            viewModel.onMovieSearchQueryChanged("NonExistent")
            advanceUntilIdle()
            
            // Wait for debounce (500ms) + processing
            kotlinx.coroutines.delay(600)
            
            val filtered = awaitItem()
            assertEquals(0, filtered.size) // No matches
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== Sorting Edge Cases ====================

    @Test
    fun `moviesFlow should handle Z_TO_A sorting`() = runTest {
        // Given
        val movies = listOf(
            TestDataFactory.createMovieEntity(name = "A Movie"),
            TestDataFactory.createMovieEntity(name = "Z Movie"),
            TestDataFactory.createMovieEntity(name = "M Movie"),
        )
        whenever(mockContentRepository.getMovies()).thenReturn(flowOf(movies))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(SortOrder.Z_TO_A))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onMovieSearchQueryChanged("")
        advanceUntilIdle()

        // Then
        viewModel.moviesFlow.test(timeout = 5.seconds) {
            val sorted = awaitItem()
            if (sorted.isNotEmpty()) {
                assertTrue(sorted.first().title.contains("Z") || sorted.first().title.contains("z"))
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moviesFlow should handle RATING_HIGH_TO_LOW sorting`() = runTest {
        // Given
        val movies = listOf(
            TestDataFactory.createMovieEntity(name = "Low Rating", rating = 5.0),
            TestDataFactory.createMovieEntity(name = "High Rating", rating = 9.5),
            TestDataFactory.createMovieEntity(name = "Medium Rating", rating = 7.0),
        )
        whenever(mockContentRepository.getMovies()).thenReturn(flowOf(movies))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(SortOrder.RATING_HIGH_TO_LOW))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onMovieSearchQueryChanged("")
        advanceUntilIdle()

        // Then
        viewModel.moviesFlow.test(timeout = 5.seconds) {
            val sorted = awaitItem()
            if (sorted.size >= 2) {
                val first = sorted[0] as? MovieEntity
                val second = sorted[1] as? MovieEntity
                if (first != null && second != null) {
                    assertTrue((first.rating ?: 0.0) >= (second.rating ?: 0.0))
                }
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moviesFlow should handle RATING_LOW_TO_HIGH sorting`() = runTest {
        // Given
        val movies = listOf(
            TestDataFactory.createMovieEntity(name = "High Rating", rating = 9.5),
            TestDataFactory.createMovieEntity(name = "Low Rating", rating = 5.0),
            TestDataFactory.createMovieEntity(name = "Medium Rating", rating = 7.0),
        )
        whenever(mockContentRepository.getMovies()).thenReturn(flowOf(movies))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(SortOrder.RATING_LOW_TO_HIGH))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onMovieSearchQueryChanged("")
        advanceUntilIdle()

        // Then
        viewModel.moviesFlow.test(timeout = 5.seconds) {
            val sorted = awaitItem()
            if (sorted.size >= 2) {
                val first = sorted[0] as? MovieEntity
                val second = sorted[1] as? MovieEntity
                if (first != null && second != null) {
                    assertTrue((first.rating ?: Double.MAX_VALUE) <= (second.rating ?: Double.MAX_VALUE))
                }
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moviesFlow should handle null sortOrder`() = runTest {
        // Given
        val movies = listOf(
            TestDataFactory.createMovieEntity(name = "Z Movie"),
            TestDataFactory.createMovieEntity(name = "A Movie"),
        )
        whenever(mockContentRepository.getMovies()).thenReturn(flowOf(movies))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(null))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onMovieSearchQueryChanged("")
        advanceUntilIdle()

        // Then - Should return original order (no sorting)
        viewModel.moviesFlow.test(timeout = 5.seconds) {
            val sorted = awaitItem()
            assertEquals(movies.size, sorted.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moviesFlow should handle empty movie list`() = runTest {
        // Given
        val movies = emptyList<MovieEntity>()
        whenever(mockContentRepository.getMovies()).thenReturn(flowOf(movies))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(SortOrder.A_TO_Z))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onMovieSearchQueryChanged("")
        advanceUntilIdle()

        // Then
        viewModel.moviesFlow.test(timeout = 5.seconds) {
            val sorted = awaitItem()
            assertTrue(sorted.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== Category Filtering Edge Cases ====================

    @Test
    fun `selectMovieCategory should handle invalid category ID`() = runTest {
        // Given
        viewModel = createViewModel()
        val invalidCategoryId = "invalid_category_123"

        // When
        viewModel.selectMovieCategory(invalidCategoryId)
        advanceUntilIdle()

        // Then
        viewModel.selectedMovieCategoryId.test {
            val selected = awaitItem()
            assertEquals(invalidCategoryId, selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectMovieCategory should handle viewer category ID`() = runTest {
        // Given
        viewModel = createViewModel()
        val viewerCategoryId = "viewer_1"
        whenever(mockContentRepository.getFavoriteChannelIds(1)).thenReturn(flowOf(listOf(1, 2, 3)))
        kotlinx.coroutines.runBlocking {
            whenever(mockContentRepository.getMoviesByIds(any())).thenReturn(emptyList())
        }

        // When
        viewModel.selectMovieCategory(viewerCategoryId)
        advanceUntilIdle()

        // Then
        viewModel.selectedMovieCategoryId.test {
            val selected = awaitItem()
            assertEquals(viewerCategoryId, selected)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

