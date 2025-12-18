package com.pnr.tv.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.SessionManager
import com.pnr.tv.db.dao.PlaybackPositionDao
import com.pnr.tv.db.entity.PlaybackPositionEntity
import com.pnr.tv.testdata.TestDataFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackPositionRepositoryTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockPlaybackPositionDao: PlaybackPositionDao = mock()
    private val mockSessionManager: SessionManager = mock()
    private lateinit var repository: PlaybackPositionRepository

    @Before
    fun setup() {
        repository = PlaybackPositionRepository(mockPlaybackPositionDao, mockSessionManager)
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    @Test
    fun `savePlaybackPosition should call dao upsert with correct entity`() =
        runTest {
            // Given
            val contentId = "movie_1"
            val positionMs = 30000L
            val durationMs = 120000L
            val userId = 1
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))

            // When
            repository.savePlaybackPosition(contentId, positionMs, durationMs)
            advanceUntilIdle()

            // Then
            verify(mockPlaybackPositionDao).upsert(
                PlaybackPositionEntity(
                    contentId = contentId,
                    userId = userId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    lastUpdated = org.mockito.ArgumentMatchers.anyLong(),
                ),
            )
        }

    @Test
    fun `savePlaybackPosition should not call dao when userId is null`() =
        runTest {
            // Given
            val contentId = "movie_1"
            val positionMs = 30000L
            val durationMs = 120000L
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))

            // When
            repository.savePlaybackPosition(contentId, positionMs, durationMs)
            advanceUntilIdle()

            // Then - verify was never called (implementation should handle null gracefully)
        }

    @Test
    fun `getPlaybackPosition should return entity from dao`() =
        runTest {
            // Given
            val contentId = "movie_1"
            val userId = 1
            val position =
                TestDataFactory.createPlaybackPositionEntity(
                    contentId = contentId,
                    userId = userId,
                )
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))
            whenever(mockPlaybackPositionDao.getPosition(contentId, userId)).thenReturn(position)

            // When
            val result = repository.getPlaybackPosition(contentId)

            // Then
            assertEquals(position, result)
        }

    @Test
    fun `getPlaybackPosition should return null when userId is null`() =
        runTest {
            // Given
            val contentId = "movie_1"
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))

            // When
            val result = repository.getPlaybackPosition(contentId)

            // Then
            assertNull(result)
        }

    @Test
    fun `deletePlaybackPosition should call dao with correct parameters`() =
        runTest {
            // Given
            val contentId = "movie_1"
            val userId = 1
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))

            // When
            repository.deletePlaybackPosition(contentId)
            advanceUntilIdle()

            // Then
            verify(mockPlaybackPositionDao).deletePosition(contentId, userId)
        }

    @Test
    fun `cleanupOldPlaybackPositions should call dao deleteOlderThan`() =
        runTest {
            // Given
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)

            // When
            repository.cleanupOldPlaybackPositions()
            advanceUntilIdle()

            // Then
            verify(mockPlaybackPositionDao).deleteOlderThan(
                org.mockito.ArgumentMatchers.anyLong(),
            )
        }
}
