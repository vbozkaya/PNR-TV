package com.pnr.tv.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.db.dao.LiveStreamCategoryDao
import com.pnr.tv.db.dao.LiveStreamDao
import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import com.pnr.tv.db.entity.LiveStreamEntity
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
class LiveStreamRepositoryTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockRetrofitBuilder: Retrofit.Builder = mock()
    private val mockUserRepository: UserRepository = mock()
    private val mockLiveStreamDao: LiveStreamDao = mock()
    private val mockLiveStreamCategoryDao: LiveStreamCategoryDao = mock()
    private val mockContext: android.content.Context = mock()

    private lateinit var repository: LiveStreamRepository

    @Before
    fun setup() {
        repository =
            LiveStreamRepository(
                retrofitBuilder = mockRetrofitBuilder,
                userRepository = mockUserRepository,
                liveStreamDao = mockLiveStreamDao,
                liveStreamCategoryDao = mockLiveStreamCategoryDao,
                context = mockContext,
            )
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    @Test
    fun `getLiveStreams should return flow from liveStreamDao`() =
        runTest {
            // Given
            val testStreams =
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
            whenever(mockLiveStreamDao.getAll()).thenReturn(flowOf(testStreams))

            // When
            val result = repository.getLiveStreams()

            // Then
            result.test {
                val streams = awaitItem()
                assertEquals(testStreams, streams)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getLiveStreamsByCategoryId should return flow from liveStreamDao`() =
        runTest {
            // Given
            val categoryId = 1
            val testStreams =
                listOf(
                    LiveStreamEntity(
                        streamId = 1,
                        name = "Channel 1",
                        streamIconUrl = null,
                        categoryId = categoryId,
                        categoryName = "Sports",
                    ),
                )
            whenever(mockLiveStreamDao.getByCategoryId(categoryId)).thenReturn(flowOf(testStreams))

            // When
            val result = repository.getLiveStreamsByCategoryId(categoryId)

            // Then
            result.test {
                val streams = awaitItem()
                assertEquals(testStreams, streams)
                assertEquals(categoryId, streams.first().categoryId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getLiveStreamCategories should return flow from liveStreamCategoryDao`() =
        runTest {
            // Given
            val testCategories =
                listOf(
                    LiveStreamCategoryEntity(
                        categoryIdInt = 1,
                        categoryName = "Sports",
                        sortOrder = 0,
                    ),
                    LiveStreamCategoryEntity(
                        categoryIdInt = 2,
                        categoryName = "Movies",
                        sortOrder = 1,
                    ),
                )
            whenever(mockLiveStreamCategoryDao.getAll()).thenReturn(flowOf(testCategories))

            // When
            val result = repository.getLiveStreamCategories()

            // Then
            result.test {
                val categories = awaitItem()
                assertEquals(testCategories, categories)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getLiveStreamsByIds should return empty list when empty ids provided`() =
        runTest {
            // Given
            val emptyIds = emptyList<Int>()

            // When
            val result = repository.getLiveStreamsByIds(emptyIds)

            // Then
            assertTrue(result.isEmpty())
            // DAO should not be called for empty list
        }

    @Test
    fun `getLiveStreamsByIds should delegate to liveStreamDao when ids provided`() =
        runTest {
            // Given
            val channelIds = listOf(1, 2, 3)
            val testStreams =
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
            whenever(mockLiveStreamDao.getByIds(channelIds)).thenReturn(testStreams)

            // When
            val result = repository.getLiveStreamsByIds(channelIds)

            // Then
            assertEquals(testStreams, result)
            verify(mockLiveStreamDao).getByIds(channelIds)
        }

    // Note: refreshLiveStreams and refreshLiveStreamCategories use safeApiCall
    // which requires complex Retrofit.Builder mocking. These are better tested
    // with integration tests using MockWebServer. Unit tests focus on DAO operations.
}
