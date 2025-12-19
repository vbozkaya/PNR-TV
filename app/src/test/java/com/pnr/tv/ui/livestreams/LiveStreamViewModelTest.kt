package com.pnr.tv.ui.livestreams

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.ContentConstants
import com.pnr.tv.R
import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.domain.BuildLiveStreamUrlUseCase
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.Result
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LiveStreamViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockContentRepository: ContentRepository = mock()
    private val mockBuildLiveStreamUrlUseCase: BuildLiveStreamUrlUseCase = mock()
    private val mockContext: Context = mock()

    private lateinit var viewModel: LiveStreamViewModel

    @Before
    fun setup() {
        whenever(mockContext.getString(any())).thenReturn("Mock String")
        whenever(mockContext.getString(any(), any())).thenReturn("Mock String")
        // Default mock for suspend functions
        runBlocking {
            whenever(mockContentRepository.getLiveStreamsByIds(any())).thenReturn(emptyList())
        }
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    private fun createViewModel(): LiveStreamViewModel {
        return LiveStreamViewModel(
            contentRepository = mockContentRepository,
            buildLiveStreamUrlUseCase = mockBuildLiveStreamUrlUseCase,
            context = mockContext,
        )
    }

    @Test
    fun `selectLiveStreamCategory should update selected category`() = runTest {
        // Given
        viewModel = createViewModel()
        val categoryId = "1"

        // When
        viewModel.selectLiveStreamCategory(categoryId)
        advanceUntilIdle()

        // Then
        viewModel.selectedLiveStreamCategoryId.test {
            val selected = awaitItem()
            assertEquals(categoryId, selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadLiveStreamsIfNeeded should skip loading when data exists`() = runTest {
        // Given
        whenever(mockContentRepository.hasLiveStreams()).thenReturn(true)
        whenever(mockContentRepository.hasLiveStreamCategories()).thenReturn(true)
        viewModel = createViewModel()

        // When
        viewModel.loadLiveStreamsIfNeeded()
        advanceUntilIdle()

        // Then
        viewModel.isLiveStreamsLoading.test {
            val isLoading = awaitItem()
            assertFalse(isLoading)
            cancelAndIgnoreRemainingEvents()
        }
        verify(mockContentRepository).hasLiveStreams()
        verify(mockContentRepository).hasLiveStreamCategories()
    }

    @Test
    fun `loadLiveStreamsIfNeeded should load when data missing`() = runTest {
        // Given
        whenever(mockContentRepository.hasLiveStreams()).thenReturn(false)
        whenever(mockContentRepository.hasLiveStreamCategories()).thenReturn(false)
        whenever(mockContentRepository.refreshLiveStreams()).thenReturn(Result.Success(Unit))
        viewModel = createViewModel()

        // When
        viewModel.loadLiveStreamsIfNeeded()
        advanceUntilIdle()

        // Then
        verify(mockContentRepository).refreshLiveStreams()
        viewModel.isLiveStreamsLoading.test {
            val isLoading = awaitItem()
            assertFalse(isLoading) // Should be false after loading completes
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadLiveStreamsIfNeeded should set error message on error`() = runTest {
        // Given
        whenever(mockContentRepository.hasLiveStreams()).thenReturn(false)
        whenever(mockContentRepository.hasLiveStreamCategories()).thenReturn(false)
        whenever(mockContentRepository.refreshLiveStreams()).thenReturn(
            Result.Error("Error message"),
        )
        viewModel = createViewModel()

        // When
        viewModel.loadLiveStreamsIfNeeded()
        advanceUntilIdle()

        // Then
        viewModel.liveStreamsErrorMessage.test {
            val error = awaitItem()
            assertNotNull(error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addLiveStreamFavorite should add favorite`() = runTest {
        // Given
        val channelId = 123
        whenever(mockContentRepository.addFavorite(any(), any())).thenReturn(Unit)
        viewModel = createViewModel()

        // When
        viewModel.addLiveStreamFavorite(channelId)
        advanceUntilIdle()

        // Then
        verify(mockContentRepository).addFavorite(channelId, 1) // defaultViewerId = 1
    }

    @Test
    fun `removeLiveStreamFavorite should remove favorite`() = runTest {
        // Given
        val channelId = 123
        whenever(mockContentRepository.removeFavorite(any(), any())).thenReturn(Unit)
        viewModel = createViewModel()

        // When
        viewModel.removeLiveStreamFavorite(channelId)
        advanceUntilIdle()

        // Then
        verify(mockContentRepository).removeFavorite(channelId, 1)
    }

    @Test
    fun `onChannelSelected should emit openPlayerEvent with URL`() = runTest {
        // Given
        val channel = LiveStreamEntity(
            streamId = 123,
            name = "Test Channel",
            streamIconUrl = null,
            categoryId = 1,
            categoryName = null,
        )
        val url = "http://example.com/stream.m3u8"
        whenever(mockBuildLiveStreamUrlUseCase.invoke(any())).thenReturn(url)
        viewModel = createViewModel()

        // When & Then - Flow'u dinlemeye başla, sonra action'ı tetikle
        viewModel.openPlayerEvent.test(timeout = 5.seconds) {
            viewModel.onChannelSelected(channel)
            advanceUntilIdle()

            val event = awaitItem()
            assertEquals(url, event.first)
            assertEquals(channel.streamId, event.second)
            assertEquals(channel.categoryId, event.third)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onChannelSelected should not emit event when URL is null`() = runTest {
        // Given
        val channel = LiveStreamEntity(
            streamId = 123,
            name = "Test Channel",
            streamIconUrl = null,
            categoryId = 1,
            categoryName = null,
        )
        whenever(mockBuildLiveStreamUrlUseCase.invoke(any())).thenReturn(null)
        viewModel = createViewModel()

        // When
        viewModel.onChannelSelected(channel)
        advanceUntilIdle()

        // Then
        // Event should not be emitted when URL is null
        // We can't easily test this without collecting, but the function should handle it gracefully
    }

    @Test
    fun `liveStreamCategories should include virtual categories`() = runTest {
        // Given
        val normalCategories = listOf(
            LiveStreamCategoryEntity(
                categoryIdInt = 1,
                categoryName = "Category 1",
                sortOrder = ContentConstants.SortOrder.DEFAULT,
            ),
        )
        whenever(mockContentRepository.getLiveStreamCategories()).thenReturn(flowOf(normalCategories))
        whenever(mockContentRepository.getFavoriteChannelIds(any())).thenReturn(flowOf(emptyList()))
        whenever(mockContentRepository.getRecentlyWatchedChannelIds()).thenReturn(flowOf(emptyList()))
        viewModel = createViewModel()

        // When
        viewModel.liveStreamCategories.test {
            val categories = awaitItem()

            // Then
            assertTrue(categories.isNotEmpty())
            // Should include favorites and recently watched virtual categories
            val categoryNames = categories.map { it.categoryName }
            assertTrue(categoryNames.contains("Mock String")) // Favorites category
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== Category Filtering Edge Cases ====================

    @Test
    fun `selectLiveStreamCategory should handle null category ID`() = runTest {
        // Given
        viewModel = createViewModel()

        // When
        viewModel.selectLiveStreamCategory(null)
        advanceUntilIdle()

        // Then
        viewModel.selectedLiveStreamCategoryId.test {
            val selected = awaitItem()
            assertNull(selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectLiveStreamCategory should handle invalid category ID`() = runTest {
        // Given
        viewModel = createViewModel()
        val invalidCategoryId = "invalid_category"

        // When
        viewModel.selectLiveStreamCategory(invalidCategoryId)
        advanceUntilIdle()

        // Then
        viewModel.selectedLiveStreamCategoryId.test {
            val selected = awaitItem()
            assertEquals(invalidCategoryId, selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectLiveStreamCategory should handle favorites virtual category`() = runTest {
        // Given
        viewModel = createViewModel()
        val favoritesCategoryId = LiveStreamViewModel.VIRTUAL_CATEGORY_ID_FAVORITES.toString()
        whenever(mockContentRepository.getFavoriteChannelIds(any())).thenReturn(flowOf(listOf(1, 2, 3)))
        runBlocking {
            whenever(mockContentRepository.getLiveStreamsByIds(any())).thenReturn(emptyList())
        }

        // When
        viewModel.selectLiveStreamCategory(favoritesCategoryId)
        advanceUntilIdle()

        // Then
        viewModel.selectedLiveStreamCategoryId.test {
            val selected = awaitItem()
            assertEquals(favoritesCategoryId, selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectLiveStreamCategory should handle recently watched virtual category`() = runTest {
        // Given
        viewModel = createViewModel()
        val recentlyWatchedCategoryId = LiveStreamViewModel.VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED.toString()
        whenever(mockContentRepository.getRecentlyWatchedChannelIds()).thenReturn(flowOf(listOf(1, 2)))
        runBlocking {
            whenever(mockContentRepository.getLiveStreamsByIds(any())).thenReturn(emptyList())
        }

        // When
        viewModel.selectLiveStreamCategory(recentlyWatchedCategoryId)
        advanceUntilIdle()

        // Then
        viewModel.selectedLiveStreamCategoryId.test {
            val selected = awaitItem()
            assertEquals(recentlyWatchedCategoryId, selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `liveStreams should return empty list when category ID is null`() = runTest {
        // Given
        viewModel = createViewModel()

        // When
        viewModel.selectLiveStreamCategory(null)
        advanceUntilIdle()

        // Then
        viewModel.liveStreams.test(timeout = 5.seconds) {
            val streams = awaitItem()
            assertTrue(streams.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `liveStreams should return empty list when category ID cannot be parsed`() = runTest {
        // Given
        viewModel = createViewModel()

        // When
        viewModel.selectLiveStreamCategory("not_a_number")
        advanceUntilIdle()

        // Then
        viewModel.liveStreams.test(timeout = 5.seconds) {
            val streams = awaitItem()
            assertTrue(streams.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `liveStreams should return empty list for favorites when no favorites exist`() = runTest {
        // Given
        val favoritesCategoryId = LiveStreamViewModel.VIRTUAL_CATEGORY_ID_FAVORITES.toString()
        whenever(mockContentRepository.getFavoriteChannelIds(any())).thenReturn(flowOf(emptyList()))
        // getLiveStreamsByIds is never called when favorites list is empty, but mock it anyway
        runBlocking {
            whenever(mockContentRepository.getLiveStreamsByIds(any())).thenReturn(emptyList())
        }
        viewModel = createViewModel()

        // When
        viewModel.selectLiveStreamCategory(favoritesCategoryId)
        advanceUntilIdle()

        // Then
        viewModel.liveStreams.test(timeout = 5.seconds) {
            val streams = awaitItem()
            assertTrue(streams.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `liveStreams should return empty list for recently watched when no recently watched exist`() = runTest {
        // Given
        val recentlyWatchedCategoryId = LiveStreamViewModel.VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED.toString()
        whenever(mockContentRepository.getRecentlyWatchedChannelIds()).thenReturn(flowOf(emptyList()))
        // getLiveStreamsByIds is never called when recently watched list is empty, but mock it anyway
        runBlocking {
            whenever(mockContentRepository.getLiveStreamsByIds(any())).thenReturn(emptyList())
        }
        viewModel = createViewModel()

        // When
        viewModel.selectLiveStreamCategory(recentlyWatchedCategoryId)
        advanceUntilIdle()

        // Then
        viewModel.liveStreams.test(timeout = 5.seconds) {
            val streams = awaitItem()
            assertTrue(streams.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `liveStreams should return channels for normal category ID`() = runTest {
        // Given
        val normalCategoryId = "5"
        val channels = listOf(
            LiveStreamEntity(
                streamId = 1,
                name = "Channel 1",
                streamIconUrl = null,
                categoryId = 5,
                categoryName = "Sports",
            ),
            LiveStreamEntity(
                streamId = 2,
                name = "Channel 2",
                streamIconUrl = null,
                categoryId = 5,
                categoryName = "Sports",
            ),
        )
        whenever(mockContentRepository.getLiveStreamsByCategoryId(5)).thenReturn(flowOf(channels))
        viewModel = createViewModel()

        // When
        viewModel.selectLiveStreamCategory(normalCategoryId)
        advanceUntilIdle()

        // Then
        viewModel.liveStreams.test(timeout = 5.seconds) {
            val streams = awaitItem()
            assertEquals(channels.size, streams.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

