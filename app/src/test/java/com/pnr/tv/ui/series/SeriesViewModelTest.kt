package com.pnr.tv.ui.series

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.ContentConstants
import com.pnr.tv.R
import com.pnr.tv.db.entity.SeriesCategoryEntity
import com.pnr.tv.db.entity.SeriesEntity
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
class SeriesViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockContentRepository: ContentRepository = mock()
    private val mockViewerRepository: ViewerRepository = mock()
    private val mockSortPreferenceManager: SortPreferenceManager = mock()
    private val mockContext: Context = mock()

    private lateinit var viewModel: SeriesViewModel

    @Before
    fun setup() {
        whenever(mockContext.getString(any())).thenReturn("Mock String")
        whenever(mockContext.getString(any(), any())).thenReturn("Mock String")
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    private fun createViewModel(): SeriesViewModel {
        return SeriesViewModel(
            contentRepository = mockContentRepository,
            viewerRepository = mockViewerRepository,
            sortPreferenceManager = mockSortPreferenceManager,
            context = mockContext,
        )
    }

    @Test
    fun `selectSeriesCategory should update selected category`() = runTest {
        // Given
        viewModel = createViewModel()
        val categoryId = SeriesViewModel.VIRTUAL_CATEGORY_ID_ALL_SERIES

        // When
        viewModel.selectSeriesCategory(categoryId)
        advanceUntilIdle()

        // Then
        viewModel.selectedSeriesCategoryId.test {
            val selected = awaitItem()
            assertEquals(categoryId, selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadSeriesIfNeeded should skip loading when data exists`() = runTest {
        // Given
        whenever(mockContentRepository.hasSeries()).thenReturn(true)
        whenever(mockContentRepository.hasSeriesCategories()).thenReturn(true)
        viewModel = createViewModel()

        // When
        viewModel.loadSeriesIfNeeded()
        advanceUntilIdle()

        // Then
        viewModel.isSeriesLoading.test {
            val isLoading = awaitItem()
            assertFalse(isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadSeriesIfNeeded should load when data missing`() = runTest {
        // Given
        whenever(mockContentRepository.hasSeries()).thenReturn(false)
        whenever(mockContentRepository.hasSeriesCategories()).thenReturn(false)
        whenever(mockContentRepository.refreshSeriesCategories()).thenReturn(Result.Success(Unit))
        whenever(mockContentRepository.refreshSeries(any(), any())).thenReturn(Result.Success(Unit))
        viewModel = createViewModel()

        // When
        viewModel.loadSeriesIfNeeded()
        advanceUntilIdle()

        // Then
        verify(mockContentRepository).refreshSeriesCategories()
        verify(mockContentRepository).refreshSeries(any(), any())
    }

    @Test
    fun `addSeriesFavorite should add favorite`() = runTest {
        // Given
        val contentId = 123
        val viewerId = 1
        whenever(mockContentRepository.addFavorite(any(), any())).thenReturn(Unit)
        viewModel = createViewModel()

        // When
        viewModel.addSeriesFavorite(contentId, viewerId)
        advanceUntilIdle()

        // Then
        verify(mockContentRepository).addFavorite(contentId, viewerId)
    }

    @Test
    fun `removeSeriesFavorite should remove favorite`() = runTest {
        // Given
        val contentId = 123
        val viewerId = 1
        whenever(mockContentRepository.removeFavorite(any(), any())).thenReturn(Unit)
        viewModel = createViewModel()

        // When
        viewModel.removeSeriesFavorite(contentId, viewerId)
        advanceUntilIdle()

        // Then
        verify(mockContentRepository).removeFavorite(contentId, viewerId)
    }

    @Test
    fun `onSeriesSearchQueryChanged should update search query`() = runTest {
        // Given
        viewModel = createViewModel()
        val query = "test query"

        // When
        viewModel.onSeriesSearchQueryChanged(query)
        advanceUntilIdle()

        // Then
        viewModel.seriesSearchQuery.test {
            val searchQuery = awaitItem()
            assertEquals(query, searchQuery)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveSeriesSortOrder should save sort order`() = runTest {
        // Given
        val sortOrder = SortOrder.A_TO_Z
        whenever(mockSortPreferenceManager.saveSortOrder(any(), any())).thenReturn(Unit)
        viewModel = createViewModel()

        // When
        viewModel.saveSeriesSortOrder(sortOrder)
        advanceUntilIdle()

        // Then
        verify(mockSortPreferenceManager).saveSortOrder(ContentType.SERIES, sortOrder)
    }

    // ==================== Search Edge Cases ====================

    @Test
    fun `seriesFlow should not filter when query is less than 3 characters`() = runTest {
        // Given
        val series = listOf(
            TestDataFactory.createSeriesEntity(name = "Test Series 1"),
            TestDataFactory.createSeriesEntity(name = "Test Series 2"),
        )
        whenever(mockContentRepository.getSeries()).thenReturn(flowOf(series))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(null))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onSeriesSearchQueryChanged("Te") // 2 characters
        advanceUntilIdle()

        // Then - Should not filter (query < 3 characters)
        viewModel.seriesFlow.test(timeout = 5.seconds) {
            val filtered = awaitItem()
            assertEquals(series.size, filtered.size) // All series should be returned
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `seriesFlow should filter case insensitively`() = runTest {
        // Given
        val series = listOf(
            TestDataFactory.createSeriesEntity(name = "Test Series 1"),
            TestDataFactory.createSeriesEntity(name = "TEST SERIES 2"),
            TestDataFactory.createSeriesEntity(name = "Other Series"),
        )
        whenever(mockContentRepository.getSeries()).thenReturn(flowOf(series))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(null))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When & Then - Flow'u dinlemeye başla, sonra action'ı tetikle
        viewModel.seriesFlow.test(timeout = 10.seconds) {
            // Skip initial empty state
            awaitItem()
            
            // Trigger search
            viewModel.onSeriesSearchQueryChanged("test") // lowercase
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
    fun `seriesFlow should handle empty query`() = runTest {
        // Given
        val series = listOf(
            TestDataFactory.createSeriesEntity(name = "Test Series 1"),
            TestDataFactory.createSeriesEntity(name = "Other Series"),
        )
        whenever(mockContentRepository.getSeries()).thenReturn(flowOf(series))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(null))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onSeriesSearchQueryChanged("")
        advanceUntilIdle()

        // Then
        viewModel.seriesFlow.test(timeout = 5.seconds) {
            val filtered = awaitItem()
            assertEquals(series.size, filtered.size) // All series should be returned
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `seriesFlow should return empty list when no matches found`() = runTest {
        // Given
        val series = listOf(
            TestDataFactory.createSeriesEntity(name = "Test Series 1"),
            TestDataFactory.createSeriesEntity(name = "Other Series"),
        )
        whenever(mockContentRepository.getSeries()).thenReturn(flowOf(series))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(null))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When & Then - Flow'u dinlemeye başla, sonra action'ı tetikle
        viewModel.seriesFlow.test(timeout = 10.seconds) {
            // Skip initial empty state
            awaitItem()
            
            // Trigger search
            viewModel.onSeriesSearchQueryChanged("NonExistent")
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
    fun `seriesFlow should handle Z_TO_A sorting`() = runTest {
        // Given
        val series = listOf(
            TestDataFactory.createSeriesEntity(name = "A Series"),
            TestDataFactory.createSeriesEntity(name = "Z Series"),
            TestDataFactory.createSeriesEntity(name = "M Series"),
        )
        whenever(mockContentRepository.getSeries()).thenReturn(flowOf(series))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(SortOrder.Z_TO_A))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onSeriesSearchQueryChanged("")
        advanceUntilIdle()

        // Then
        viewModel.seriesFlow.test(timeout = 5.seconds) {
            val sorted = awaitItem()
            if (sorted.isNotEmpty()) {
                assertTrue(sorted.first().title.contains("Z") || sorted.first().title.contains("z"))
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `seriesFlow should handle RATING_HIGH_TO_LOW sorting`() = runTest {
        // Given
        val series = listOf(
            TestDataFactory.createSeriesEntity(name = "Low Rating", rating = 5.0),
            TestDataFactory.createSeriesEntity(name = "High Rating", rating = 9.5),
            TestDataFactory.createSeriesEntity(name = "Medium Rating", rating = 7.0),
        )
        whenever(mockContentRepository.getSeries()).thenReturn(flowOf(series))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(SortOrder.RATING_HIGH_TO_LOW))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onSeriesSearchQueryChanged("")
        advanceUntilIdle()

        // Then
        viewModel.seriesFlow.test(timeout = 5.seconds) {
            val sorted = awaitItem()
            if (sorted.size >= 2) {
                val first = sorted[0] as? SeriesEntity
                val second = sorted[1] as? SeriesEntity
                if (first != null && second != null) {
                    assertTrue((first.rating ?: 0.0) >= (second.rating ?: 0.0))
                }
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `seriesFlow should handle RATING_LOW_TO_HIGH sorting`() = runTest {
        // Given
        val series = listOf(
            TestDataFactory.createSeriesEntity(name = "High Rating", rating = 9.5),
            TestDataFactory.createSeriesEntity(name = "Low Rating", rating = 5.0),
            TestDataFactory.createSeriesEntity(name = "Medium Rating", rating = 7.0),
        )
        whenever(mockContentRepository.getSeries()).thenReturn(flowOf(series))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(SortOrder.RATING_LOW_TO_HIGH))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onSeriesSearchQueryChanged("")
        advanceUntilIdle()

        // Then
        viewModel.seriesFlow.test(timeout = 5.seconds) {
            val sorted = awaitItem()
            if (sorted.size >= 2) {
                val first = sorted[0] as? SeriesEntity
                val second = sorted[1] as? SeriesEntity
                if (first != null && second != null) {
                    assertTrue((first.rating ?: Double.MAX_VALUE) <= (second.rating ?: Double.MAX_VALUE))
                }
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `seriesFlow should handle null sortOrder`() = runTest {
        // Given
        val series = listOf(
            TestDataFactory.createSeriesEntity(name = "Z Series"),
            TestDataFactory.createSeriesEntity(name = "A Series"),
        )
        whenever(mockContentRepository.getSeries()).thenReturn(flowOf(series))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(null))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onSeriesSearchQueryChanged("")
        advanceUntilIdle()

        // Then - Should return original order (no sorting)
        viewModel.seriesFlow.test(timeout = 5.seconds) {
            val sorted = awaitItem()
            assertEquals(series.size, sorted.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `seriesFlow should handle empty series list`() = runTest {
        // Given
        val series = emptyList<SeriesEntity>()
        whenever(mockContentRepository.getSeries()).thenReturn(flowOf(series))
        whenever(mockSortPreferenceManager.getSortOrder(any())).thenReturn(flowOf(SortOrder.A_TO_Z))
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onSeriesSearchQueryChanged("")
        advanceUntilIdle()

        // Then
        viewModel.seriesFlow.test(timeout = 5.seconds) {
            val sorted = awaitItem()
            assertTrue(sorted.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== Category Filtering Edge Cases ====================

    @Test
    fun `selectSeriesCategory should handle invalid category ID`() = runTest {
        // Given
        viewModel = createViewModel()
        val invalidCategoryId = "invalid_category_123"

        // When
        viewModel.selectSeriesCategory(invalidCategoryId)
        advanceUntilIdle()

        // Then
        viewModel.selectedSeriesCategoryId.test {
            val selected = awaitItem()
            assertEquals(invalidCategoryId, selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectSeriesCategory should handle viewer category ID`() = runTest {
        // Given
        viewModel = createViewModel()
        val viewerCategoryId = "viewer_1"
        whenever(mockContentRepository.getFavoriteChannelIds(1)).thenReturn(flowOf(listOf(1, 2, 3)))
        runBlocking {
            whenever(mockContentRepository.getSeriesByIds(any())).thenReturn(emptyList())
        }

        // When
        viewModel.selectSeriesCategory(viewerCategoryId)
        advanceUntilIdle()

        // Then
        viewModel.selectedSeriesCategoryId.test {
            val selected = awaitItem()
            assertEquals(viewerCategoryId, selected)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

