package com.pnr.tv.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.SessionManager
import com.pnr.tv.db.dao.ViewerDao
import com.pnr.tv.db.entity.ViewerEntity
import com.pnr.tv.testdata.TestDataFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * ViewerRepository için unit testler.
 * Viewer yönetimi ve SessionManager entegrasyonunu test eder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewerRepositoryTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockViewerDao: ViewerDao = mock()
    private val mockSessionManager: SessionManager = mock()
    private lateinit var repository: ViewerRepository

    @Before
    fun setup() {
        repository = ViewerRepository(mockViewerDao, mockSessionManager)
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    // ==================== addViewer Tests ====================

    @Test
    fun `addViewer should insert viewer with current user id`() = runTest {
        // Given
        val userId = 1
        val viewer = TestDataFactory.createViewerEntity(id = 0, name = "Test Viewer", userId = 0)
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))
        whenever(mockViewerDao.insert(any())).thenReturn(1L)

        // When
        val result = repository.addViewer(viewer)

        // Then
        assertEquals(1L, result)
        verify(mockViewerDao).insert(viewer.copy(userId = userId))
    }

    @Test
    fun `addViewer should return 0 when user id is null`() = runTest {
        // Given
        val viewer = TestDataFactory.createViewerEntity(id = 0, name = "Test Viewer", userId = 0)
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))

        // When
        val result = repository.addViewer(viewer)

        // Then
        assertEquals(0L, result)
        verify(mockViewerDao, never()).insert(any())
    }

    @Test
    fun `addViewer should update viewer userId from session manager`() = runTest {
        // Given
        val userId = 5
        val viewer = TestDataFactory.createViewerEntity(id = 0, name = "Test Viewer", userId = 0)
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))
        whenever(mockViewerDao.insert(any())).thenReturn(2L)

        // When
        val result = repository.addViewer(viewer)

        // Then
        assertEquals(2L, result)
        verify(mockViewerDao).insert(viewer.copy(userId = userId))
    }

    // ==================== deleteViewer Tests ====================

    @Test
    fun `deleteViewer should call dao delete with viewer id`() = runTest {
        // Given
        val viewer = TestDataFactory.createViewerEntity(id = 1, name = "Test Viewer", userId = 1)

        // When
        repository.deleteViewer(viewer)

        // Then
        verify(mockViewerDao).delete(1)
    }

    @Test
    fun `deleteViewer should handle different viewer ids`() = runTest {
        // Given
        val viewer = TestDataFactory.createViewerEntity(id = 999, name = "Test Viewer", userId = 1)

        // When
        repository.deleteViewer(viewer)

        // Then
        verify(mockViewerDao).delete(999)
    }

    // ==================== getAllViewers Tests ====================

    @Test
    fun `getAllViewers should return flow from dao when userId is not null`() = runTest {
        // Given
        val userId = 1
        val viewers = listOf(
            TestDataFactory.createViewerEntity(id = 1, name = "Viewer 1", userId = userId),
            TestDataFactory.createViewerEntity(id = 2, name = "Viewer 2", userId = userId),
        )
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))
        whenever(mockViewerDao.getAllViewers(userId)).thenReturn(flowOf(viewers))

        // When & Then
        repository.getAllViewers().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("Viewer 1", result[0].name)
            assertEquals("Viewer 2", result[1].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllViewers should return empty list when userId is null`() = runTest {
        // Given
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))

        // When & Then
        repository.getAllViewers().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        verify(mockViewerDao, never()).getAllViewers(any())
    }

    @Test
    fun `getAllViewers should return empty list when dao returns empty`() = runTest {
        // Given
        val userId = 1
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))
        whenever(mockViewerDao.getAllViewers(userId)).thenReturn(flowOf(emptyList()))

        // When & Then
        repository.getAllViewers().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllViewers should update when userId changes`() = runTest {
        // Given
        val userId1 = 1
        val userId2 = 2
        val viewers1 = listOf(
            TestDataFactory.createViewerEntity(id = 1, name = "Viewer 1", userId = userId1),
        )
        val viewers2 = listOf(
            TestDataFactory.createViewerEntity(id = 2, name = "Viewer 2", userId = userId2),
        )
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(
            flowOf(userId1, userId2),
        )
        whenever(mockViewerDao.getAllViewers(userId1)).thenReturn(flowOf(viewers1))
        whenever(mockViewerDao.getAllViewers(userId2)).thenReturn(flowOf(viewers2))

        // When & Then
        repository.getAllViewers().test {
            val result1 = awaitItem()
            assertEquals(1, result1.size)
            assertEquals("Viewer 1", result1[0].name)

            val result2 = awaitItem()
            assertEquals(1, result2.size)
            assertEquals("Viewer 2", result2[0].name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== getViewerById Tests ====================

    @Test
    fun `getViewerById should return viewer when found`() = runTest {
        // Given
        val userId = 1
        val viewerId = 5
        val viewer = TestDataFactory.createViewerEntity(id = viewerId, name = "Test Viewer", userId = userId)
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))
        whenever(mockViewerDao.getViewerById(viewerId, userId)).thenReturn(viewer)

        // When
        val result = repository.getViewerById(viewerId)

        // Then
        assertNotNull(result)
        assertEquals("Test Viewer", result?.name)
        assertEquals(viewerId, result?.id)
        verify(mockViewerDao).getViewerById(viewerId, userId)
    }

    @Test
    fun `getViewerById should return null when viewer not found`() = runTest {
        // Given
        val userId = 1
        val viewerId = 999
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))
        whenever(mockViewerDao.getViewerById(viewerId, userId)).thenReturn(null)

        // When
        val result = repository.getViewerById(viewerId)

        // Then
        assertNull(result)
        verify(mockViewerDao).getViewerById(viewerId, userId)
    }

    @Test
    fun `getViewerById should return null when userId is null`() = runTest {
        // Given
        val viewerId = 1
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))

        // When
        val result = repository.getViewerById(viewerId)

        // Then
        assertNull(result)
        verify(mockViewerDao, never()).getViewerById(any(), any())
    }

    @Test
    fun `getViewerById should return null for wrong user`() = runTest {
        // Given
        val userId = 1
        val viewerId = 5
        // Viewer belongs to different user, so dao returns null
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))
        whenever(mockViewerDao.getViewerById(viewerId, userId)).thenReturn(null)

        // When
        val result = repository.getViewerById(viewerId)

        // Then
        assertNull(result)
    }

    // ==================== getViewerIdsWithFavorites Tests ====================

    @Test
    fun `getViewerIdsWithFavorites should return viewer IDs when userId is not null`() = runTest {
        // Given
        val userId = 1
        val viewerIds = listOf(1, 2, 3)
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))
        whenever(mockViewerDao.getViewerIdsWithFavorites(userId)).thenReturn(flowOf(viewerIds))

        // When & Then
        repository.getViewerIdsWithFavorites().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertTrue(result.contains(1))
            assertTrue(result.contains(2))
            assertTrue(result.contains(3))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getViewerIdsWithFavorites should return empty list when userId is null`() = runTest {
        // Given
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))

        // When & Then
        repository.getViewerIdsWithFavorites().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        verify(mockViewerDao, never()).getViewerIdsWithFavorites(any())
    }

    @Test
    fun `getViewerIdsWithFavorites should return empty list when no favorites exist`() = runTest {
        // Given
        val userId = 1
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))
        whenever(mockViewerDao.getViewerIdsWithFavorites(userId)).thenReturn(flowOf(emptyList()))

        // When & Then
        repository.getViewerIdsWithFavorites().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getViewerIdsWithFavorites should update when userId changes`() = runTest {
        // Given
        val userId1 = 1
        val userId2 = 2
        val viewerIds1 = listOf(1, 2)
        val viewerIds2 = listOf(3, 4)
        whenever(mockSessionManager.getCurrentUserId()).thenReturn(
            flowOf(userId1, userId2),
        )
        whenever(mockViewerDao.getViewerIdsWithFavorites(userId1)).thenReturn(flowOf(viewerIds1))
        whenever(mockViewerDao.getViewerIdsWithFavorites(userId2)).thenReturn(flowOf(viewerIds2))

        // When & Then
        repository.getViewerIdsWithFavorites().test {
            val result1 = awaitItem()
            assertEquals(2, result1.size)
            assertTrue(result1.contains(1))
            assertTrue(result1.contains(2))

            val result2 = awaitItem()
            assertEquals(2, result2.size)
            assertTrue(result2.contains(3))
            assertTrue(result2.contains(4))

            cancelAndIgnoreRemainingEvents()
        }
    }
}

