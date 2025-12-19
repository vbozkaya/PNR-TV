package com.pnr.tv.db.dao

import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import app.cash.turbine.test
import com.pnr.tv.db.AppDatabase
import com.pnr.tv.db.dao.UserDao
import com.pnr.tv.testdata.TestDataFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * ViewerDao için unit testler.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ViewerDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var viewerDao: ViewerDao
    private lateinit var userDao: UserDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        viewerDao = database.viewerDao()
        userDao = database.userDao()
    }
    
    /**
     * Helper metod: Test için gerekli user'ı insert eder.
     * ViewerEntity foreign key constraint nedeniyle user'ın önce insert edilmesi gerekir.
     */
    private suspend fun insertTestUser(userId: Int = 1) {
        val user = TestDataFactory.createUserAccountEntity(
            id = userId,
            accountName = "Test User $userId",
            username = "testuser$userId",
        )
        userDao.insertUser(user)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun `insert should insert viewer successfully`() = runTest {
        // Given
        insertTestUser(userId = 1)
        val viewer = TestDataFactory.createViewerEntity(id = 1, name = "Test Viewer", userId = 1)

        // When
        val rowId = viewerDao.insert(viewer)

        // Then
        assertTrue(rowId > 0)
        val result = viewerDao.getViewerById(1, 1)
        assertNotNull(result)
        assertEquals("Test Viewer", result?.name)
    }

    @Test
    fun `getAllViewers should return viewers for user ordered by isDeletable ASC then name ASC`() = runTest {
        // Given
        insertTestUser(userId = 1)
        val viewer1 = TestDataFactory.createViewerEntity(id = 1, name = "Charlie", userId = 1, isDeletable = true)
        val viewer2 = TestDataFactory.createViewerEntity(id = 2, name = "Alice", userId = 1, isDeletable = false)
        val viewer3 = TestDataFactory.createViewerEntity(id = 3, name = "Bob", userId = 1, isDeletable = true)
        viewerDao.insert(viewer1)
        viewerDao.insert(viewer2)
        viewerDao.insert(viewer3)

        // When & Then
        viewerDao.getAllViewers(1).test {
            val result = awaitItem()
            assertEquals(3, result.size)
            // Should be ordered by isDeletable ASC (false first), then name ASC
            assertEquals("Alice", result[0].name) // isDeletable = false
            assertEquals("Bob", result[1].name) // isDeletable = true, name ASC
            assertEquals("Charlie", result[2].name) // isDeletable = true, name ASC
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllViewers should return only viewers for specific user`() = runTest {
        // Given
        insertTestUser(userId = 1)
        insertTestUser(userId = 2)
        val viewer1 = TestDataFactory.createViewerEntity(id = 1, name = "Viewer 1", userId = 1)
        val viewer2 = TestDataFactory.createViewerEntity(id = 2, name = "Viewer 2", userId = 2)
        viewerDao.insert(viewer1)
        viewerDao.insert(viewer2)

        // When & Then
        viewerDao.getAllViewers(1).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Viewer 1", result[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getViewerById should return viewer for existing ID and user`() = runTest {
        // Given
        insertTestUser(userId = 1)
        val viewer = TestDataFactory.createViewerEntity(id = 1, name = "Test Viewer", userId = 1)
        viewerDao.insert(viewer)

        // When
        val result = viewerDao.getViewerById(1, 1)

        // Then
        assertNotNull(result)
        assertEquals("Test Viewer", result?.name)
    }

    @Test
    fun `getViewerById should return null for non-existent ID`() = runTest {
        // When
        val result = viewerDao.getViewerById(999, 1)

        // Then
        assertNull(result)
    }

    @Test
    fun `getViewerById should return null for wrong user`() = runTest {
        // Given
        insertTestUser(userId = 1)
        insertTestUser(userId = 2)
        val viewer = TestDataFactory.createViewerEntity(id = 1, name = "Test Viewer", userId = 1)
        viewerDao.insert(viewer)

        // When
        val result = viewerDao.getViewerById(1, 2)

        // Then
        assertNull(result)
    }

    @Test
    fun `delete should remove viewer`() = runTest {
        // Given
        insertTestUser(userId = 1)
        val viewer = TestDataFactory.createViewerEntity(id = 1, name = "Test Viewer", userId = 1)
        viewerDao.insert(viewer)

        // When
        viewerDao.delete(1)

        // Then
        val result = viewerDao.getViewerById(1, 1)
        assertNull(result)
    }

    @Test
    fun `getViewerIdsWithFavorites should return viewer IDs with favorites`() = runTest {
        // Given
        insertTestUser(userId = 1)
        val viewer = TestDataFactory.createViewerEntity(id = 1, name = "Test Viewer", userId = 1)
        viewerDao.insert(viewer)
        val favoriteDao = database.favoriteDao()
        val favorite = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        favoriteDao.addFavorite(favorite)

        // When & Then
        viewerDao.getViewerIdsWithFavorites(1).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertTrue(result.contains(1))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteByUserId should remove all viewers for user`() = runTest {
        // Given
        insertTestUser(userId = 1)
        insertTestUser(userId = 2)
        val viewer1 = TestDataFactory.createViewerEntity(id = 1, name = "Viewer 1", userId = 1)
        val viewer2 = TestDataFactory.createViewerEntity(id = 2, name = "Viewer 2", userId = 2)
        viewerDao.insert(viewer1)
        viewerDao.insert(viewer2)

        // When
        viewerDao.deleteByUserId(1)

        // Then
        viewerDao.getAllViewers(1).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        viewerDao.getAllViewers(2).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAll should remove all viewers`() = runTest {
        // Given
        insertTestUser(userId = 1)
        insertTestUser(userId = 2)
        val viewer1 = TestDataFactory.createViewerEntity(id = 1, name = "Viewer 1", userId = 1)
        val viewer2 = TestDataFactory.createViewerEntity(id = 2, name = "Viewer 2", userId = 2)
        viewerDao.insert(viewer1)
        viewerDao.insert(viewer2)

        // When
        viewerDao.deleteAll()

        // Then
        viewerDao.getAllViewers(1).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

