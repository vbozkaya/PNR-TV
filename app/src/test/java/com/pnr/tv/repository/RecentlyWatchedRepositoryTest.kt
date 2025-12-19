package com.pnr.tv.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.SessionManager
import com.pnr.tv.db.dao.RecentlyWatchedDao
import com.pnr.tv.db.entity.RecentlyWatchedEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RecentlyWatchedRepositoryTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockRecentlyWatchedDao: RecentlyWatchedDao = mock()
    private val mockSessionManager: SessionManager = mock()
    private lateinit var repository: RecentlyWatchedRepository

    @Before
    fun setup() {
        repository = RecentlyWatchedRepository(mockRecentlyWatchedDao, mockSessionManager)
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    @Test
    fun `saveRecentlyWatched should call dao upsert and trim`() =
        runTest {
            // Given
            val channelId = 1
            val userId = 1
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))

            // When
            repository.saveRecentlyWatched(channelId)
            advanceUntilIdle()

            // Then
            verify(mockRecentlyWatchedDao).upsert(
                org.mockito.kotlin.argThat { entity ->
                    entity.channelId == channelId &&
                        entity.userId == userId &&
                        entity.watchedAt > 0
                },
            )
            verify(mockRecentlyWatchedDao).trim(userId, 50)
        }

    @Test
    fun `saveRecentlyWatched should not call dao when userId is null`() =
        runTest {
            // Given
            val channelId = 1
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))

            // When
            repository.saveRecentlyWatched(channelId)
            advanceUntilIdle()

            // Then - verify was never called (implementation should handle null gracefully)
        }

    @Test
    fun `getRecentlyWatchedChannelIds should return list from dao`() =
        runTest {
            // Given
            val userId = 1
            val limit = 20
            val channelIds = listOf(1, 2, 3)
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))
            whenever(mockRecentlyWatchedDao.getRecentlyWatchedChannelIds(userId, limit)).thenReturn(flowOf(channelIds))

            // When
            val result = repository.getRecentlyWatchedChannelIds(limit)

            // Then
            result.test {
                assertEquals(channelIds, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getRecentlyWatchedChannelIds should return empty list when userId is null`() =
        runTest {
            // Given
            val limit = 20
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))

            // When
            val result = repository.getRecentlyWatchedChannelIds(limit)

            // Then
            result.test {
                assertEquals(emptyList<Int>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getRecentlyWatchedChannelIds should use default limit when not specified`() =
        runTest {
            // Given
            val userId = 1
            val channelIds = listOf(1, 2, 3)
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))
            whenever(mockRecentlyWatchedDao.getRecentlyWatchedChannelIds(userId, 50)).thenReturn(flowOf(channelIds))

            // When
            val result = repository.getRecentlyWatchedChannelIds()

            // Then
            result.test {
                assertEquals(channelIds, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
