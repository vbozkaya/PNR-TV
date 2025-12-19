package com.pnr.tv.ui.player

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.domain.BuildLiveStreamUrlUseCase
import com.pnr.tv.repository.ContentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * PlayerViewModel için unit testler.
 * 
 * Not: PlayerViewModel init bloğunda ExoPlayer oluşturduğu için
 * unit test ortamında çalışmaz. ExoPlayer gerçek Android context ve
 * native kütüphaneler gerektirir. Bu testler için Robolectric veya
 * integration testler kullanılmalıdır.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockContentRepository: ContentRepository = mock()
    private val mockContext: android.content.Context = mock()
    private val savedStateHandle = SavedStateHandle()
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setup() {
        viewModel = PlayerViewModel(mockContentRepository, mockContext, savedStateHandle)
    }

    @After
    fun tearDown() {
        // MainCoroutineRule handles Dispatcher cleanup
    }

    @Test
    @Ignore("ExoPlayer requires real Android context - needs Robolectric or integration tests")
    fun `createPlaylistFromChannels should create correct MediaItem list`() =
        runTest {
            // Given
            val channels =
                listOf(
                    LiveStreamEntity(
                        streamId = 1,
                        name = "Channel 1",
                        streamIconUrl = null,
                        categoryId = 1,
                        categoryName = "Sports",
                    ),
                    LiveStreamEntity(
                        streamId = 2,
                        name = "Channel 2",
                        streamIconUrl = "http://example.com/icon.png",
                        categoryId = 1,
                        categoryName = "Sports",
                    ),
                )
            val mockBuildUrlUseCase: BuildLiveStreamUrlUseCase = mock()
            whenever(mockBuildUrlUseCase(channels[0])).thenReturn("http://example.com/stream1.ts")
            whenever(mockBuildUrlUseCase(channels[1])).thenReturn("http://example.com/stream2.ts")

            // When
            val result = viewModel.createPlaylistFromChannels(channels, mockBuildUrlUseCase)

            // Then
            assertEquals(2, result.size)
            assertEquals("http://example.com/stream1.ts", result[0].localConfiguration?.uri.toString())
            assertEquals("Channel 1", result[0].mediaMetadata.title?.toString())
            assertEquals(1, result[0].localConfiguration?.tag)

            assertEquals("http://example.com/stream2.ts", result[1].localConfiguration?.uri.toString())
            assertEquals("Channel 2", result[1].mediaMetadata.title?.toString())
            assertEquals(2, result[1].localConfiguration?.tag)
        }

    @Test
    @Ignore("ExoPlayer requires real Android context - needs Robolectric or integration tests")
    fun `createPlaylistFromChannels should skip channels with null URL`() =
        runTest {
            // Given
            val channels =
                listOf(
                    LiveStreamEntity(
                        streamId = 1,
                        name = "Channel 1",
                        streamIconUrl = null,
                        categoryId = 1,
                        categoryName = "Sports",
                    ),
                    LiveStreamEntity(
                        streamId = 2,
                        name = "Channel 2",
                        streamIconUrl = null,
                        categoryId = 1,
                        categoryName = "Sports",
                    ),
                )
            val mockBuildUrlUseCase: BuildLiveStreamUrlUseCase = mock()
            whenever(mockBuildUrlUseCase(channels[0])).thenReturn("http://example.com/stream1.ts")
            whenever(mockBuildUrlUseCase(channels[1])).thenReturn(null) // Null URL

            // When
            val result = viewModel.createPlaylistFromChannels(channels, mockBuildUrlUseCase)

            // Then
            assertEquals(1, result.size) // Only one channel with valid URL
            assertEquals("http://example.com/stream1.ts", result[0].localConfiguration?.uri.toString())
        }

    @Test
    @Ignore("ExoPlayer requires real Android context - needs Robolectric or integration tests")
    fun `createPlaylistFromChannels should handle empty channel list`() =
        runTest {
            // Given
            val channels = emptyList<LiveStreamEntity>()
            val mockBuildUrlUseCase: BuildLiveStreamUrlUseCase = mock()

            // When
            val result = viewModel.createPlaylistFromChannels(channels, mockBuildUrlUseCase)

            // Then
            assertTrue(result.isEmpty())
        }

    @Test
    @Ignore("ExoPlayer requires real Android context - needs Robolectric or integration tests")
    fun `createPlaylistFromChannels should handle channels with null name`() =
        runTest {
            // Given
            val channels =
                listOf(
                    LiveStreamEntity(
                        streamId = 1,
                        name = null,
                        streamIconUrl = null,
                        categoryId = 1,
                        categoryName = "Sports",
                    ),
                )
            val mockBuildUrlUseCase: BuildLiveStreamUrlUseCase = mock()
            whenever(mockBuildUrlUseCase(channels[0])).thenReturn("http://example.com/stream1.ts")

            // When
            val result = viewModel.createPlaylistFromChannels(channels, mockBuildUrlUseCase)

            // Then
            assertEquals(1, result.size)
            assertEquals("", result[0].mediaMetadata.title?.toString()) // Empty string for null name
        }

    @Test
    @Ignore("ExoPlayer requires real Android context - needs Robolectric or integration tests")
    fun `startWatching should save channel ID and start time to SavedStateHandle`() =
        runTest {
            // Given
            val channelId = 123

            // When
            viewModel.startWatching(channelId)
            advanceUntilIdle()

            // Then
            val savedChannelId = savedStateHandle.get<Int>("watching_channel_id")
            val savedStartTime = savedStateHandle.get<Long>("watching_start_time")

            assertEquals(channelId, savedChannelId)
            assertNotNull(savedStartTime)
            assertTrue(savedStartTime!! > 0)
        }

    @Test
    @Ignore("ExoPlayer requires real Android context - needs Robolectric or integration tests")
    fun `stopWatching should clear saved channel ID and start time`() =
        runTest {
            // Given
            val channelId = 123
            viewModel.startWatching(channelId)
            advanceUntilIdle()

            // When
            viewModel.stopWatching()
            advanceUntilIdle()

            // Then
            val savedChannelId = savedStateHandle.get<Int>("watching_channel_id")
            val savedStartTime = savedStateHandle.get<Long>("watching_start_time")

            assertNull(savedChannelId)
            assertNull(savedStartTime)
        }

    @Test
    @Ignore("ExoPlayer requires real Android context - needs Robolectric or integration tests")
    fun `stopWatching should do nothing when no watching session exists`() =
        runTest {
            // Given - No watching session started

            // When
            viewModel.stopWatching()
            advanceUntilIdle()

            // Then - Should not crash, and nothing should be saved
            val savedChannelId = savedStateHandle.get<Int>("watching_channel_id")
            val savedStartTime = savedStateHandle.get<Long>("watching_start_time")

            assertNull(savedChannelId)
            assertNull(savedStartTime)
        }

    @Test
    @Ignore("ExoPlayer requires real Android context - needs Robolectric or integration tests")
    fun `isPlaying StateFlow should be initialized to false`() =
        runTest {
            // When
            val result = viewModel.isPlaying

            // Then
            result.test {
                val isPlaying = awaitItem()
                assertFalse(isPlaying)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    @Ignore("ExoPlayer requires real Android context - needs Robolectric or integration tests")
    fun `isBuffering StateFlow should be initialized to false`() =
        runTest {
            // When
            val result = viewModel.isBuffering

            // Then
            result.test {
                val isBuffering = awaitItem()
                assertFalse(isBuffering)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    @Ignore("ExoPlayer requires real Android context - needs Robolectric or integration tests")
    fun `duration StateFlow should be initialized to null`() =
        runTest {
            // When
            val result = viewModel.duration

            // Then
            result.test {
                val duration = awaitItem()
                assertNull(duration)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    @Ignore("ExoPlayer requires real Android context - needs Robolectric or integration tests")
    fun `currentPosition StateFlow should be initialized to 0`() =
        runTest {
            // When
            val result = viewModel.currentPosition

            // Then
            result.test {
                val position = awaitItem()
                assertEquals(0L, position)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    @Ignore("ExoPlayer requires real Android context - needs Robolectric or integration tests")
    fun `errorMessage StateFlow should be initialized to null`() =
        runTest {
            // When
            val result = viewModel.errorMessage

            // Then
            result.test {
                val error = awaitItem()
                assertNull(error)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    @Ignore("ExoPlayer requires real Android context - needs Robolectric or integration tests")
    fun `currentMediaItem StateFlow should be initialized to null`() =
        runTest {
            // When
            val result = viewModel.currentMediaItem

            // Then
            result.test {
                val mediaItem = awaitItem()
                assertNull(mediaItem)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Note: Testing ExoPlayer methods (play, pause, seekTo, etc.) requires complex mocking
    // of ExoPlayer and its dependencies. These are better tested with integration tests
    // or using Robolectric. Unit tests focus on non-player-dependent logic.
}
