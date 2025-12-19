package com.pnr.tv.db.dao

import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import app.cash.turbine.test
import com.pnr.tv.db.AppDatabase
import com.pnr.tv.db.entity.FavoriteChannelEntity
import com.pnr.tv.testdata.TestDataFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * FavoriteDao için unit testler.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var favoriteDao: FavoriteDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        favoriteDao = database.favoriteDao()
    }
    
    private suspend fun setupTestData() {
        // Insert users and viewers for foreign key constraints
        val userDao = database.userDao()
        val viewerDao = database.viewerDao()
        
        // Create users
        userDao.insertUser(TestDataFactory.createUserAccountEntity(id = 1, accountName = "User 1"))
        userDao.insertUser(TestDataFactory.createUserAccountEntity(id = 2, accountName = "User 2"))
        
        // Create viewers
        viewerDao.insert(TestDataFactory.createViewerEntity(id = 1, name = "Viewer 1", userId = 1))
        viewerDao.insert(TestDataFactory.createViewerEntity(id = 2, name = "Viewer 2", userId = 1))
        viewerDao.insert(TestDataFactory.createViewerEntity(id = 3, name = "Viewer 3", userId = 2))
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun `addFavorite should insert favorite successfully`() = runTest {
        // Given
        setupTestData()
        val favorite = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)

        // When
        favoriteDao.addFavorite(favorite)

        // Then
        favoriteDao.isFavorite(1, 1, 1).test {
            val result = awaitItem()
            assertTrue(result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isFavorite should return false for non-existent favorite`() = runTest {
        // Given
        setupTestData()
        // When & Then
        favoriteDao.isFavorite(1, 1, 1).test {
            val result = awaitItem()
            assertFalse(result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeFavorite should delete favorite`() = runTest {
        // Given
        setupTestData()
        val favorite = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        favoriteDao.addFavorite(favorite)

        // When
        favoriteDao.removeFavorite(1, 1, 1)

        // Then
        favoriteDao.isFavorite(1, 1, 1).test {
            val result = awaitItem()
            assertFalse(result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getFavoriteChannelIds should return channel IDs for viewer and user`() = runTest {
        // Given
        setupTestData()
        val favorite1 = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        val favorite2 = TestDataFactory.createFavoriteChannelEntity(channelId = 2, viewerId = 1, userId = 1)
        val favorite3 = TestDataFactory.createFavoriteChannelEntity(channelId = 3, viewerId = 2, userId = 1)
        favoriteDao.addFavorite(favorite1)
        favoriteDao.addFavorite(favorite2)
        favoriteDao.addFavorite(favorite3)

        // When & Then
        favoriteDao.getFavoriteChannelIds(1, 1).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.containsAll(listOf(1, 2)))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllFavoriteChannelIds should return all channel IDs for user`() = runTest {
        // Given
        setupTestData()
        val favorite1 = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        val favorite2 = TestDataFactory.createFavoriteChannelEntity(channelId = 2, viewerId = 1, userId = 1)
        val favorite3 = TestDataFactory.createFavoriteChannelEntity(channelId = 3, viewerId = 2, userId = 1)
        favoriteDao.addFavorite(favorite1)
        favoriteDao.addFavorite(favorite2)
        favoriteDao.addFavorite(favorite3)

        // When & Then
        favoriteDao.getAllFavoriteChannelIds(1).test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertTrue(result.containsAll(listOf(1, 2, 3)))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getViewerIdsWithFavorites should return viewer IDs with favorites`() = runTest {
        // Given
        setupTestData()
        val favorite1 = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        val favorite2 = TestDataFactory.createFavoriteChannelEntity(channelId = 2, viewerId = 2, userId = 1)
        favoriteDao.addFavorite(favorite1)
        favoriteDao.addFavorite(favorite2)

        // When & Then
        favoriteDao.getViewerIdsWithFavorites(1).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.containsAll(listOf(1, 2)))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteByUserId should remove all favorites for user`() = runTest {
        // Given
        setupTestData()
        val favorite1 = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        val favorite2 = TestDataFactory.createFavoriteChannelEntity(channelId = 2, viewerId = 1, userId = 2)
        favoriteDao.addFavorite(favorite1)
        favoriteDao.addFavorite(favorite2)

        // When
        favoriteDao.deleteByUserId(1)

        // Then
        favoriteDao.getAllFavoriteChannelIds(1).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        favoriteDao.getAllFavoriteChannelIds(2).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAll should remove all favorites`() = runTest {
        // Given
        setupTestData()
        val favorite1 = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        val favorite2 = TestDataFactory.createFavoriteChannelEntity(channelId = 2, viewerId = 1, userId = 2)
        favoriteDao.addFavorite(favorite1)
        favoriteDao.addFavorite(favorite2)

        // When
        favoriteDao.deleteAll()

        // Then
        favoriteDao.getAllFavoriteChannelIds(1).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addFavorite should replace existing favorite with same primary key`() = runTest {
        // Given
        setupTestData()
        val favorite1 = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        val favorite2 = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        favoriteDao.addFavorite(favorite1)

        // When
        favoriteDao.addFavorite(favorite2)

        // Then - Should still be favorite (REPLACE strategy)
        favoriteDao.isFavorite(1, 1, 1).test {
            val result = awaitItem()
            assertTrue(result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getFavoriteChannelIds should return empty list when no favorites exist`() = runTest {
        // Given
        setupTestData()
        // When & Then
        favoriteDao.getFavoriteChannelIds(1, 1).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllFavoriteChannelIds should return empty list when no favorites exist`() = runTest {
        // Given
        setupTestData()
        // When & Then
        favoriteDao.getAllFavoriteChannelIds(1).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getViewerIdsWithFavorites should return empty list when no favorites exist`() = runTest {
        // Given
        setupTestData()
        // When & Then
        favoriteDao.getViewerIdsWithFavorites(1).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeFavorite should handle non-existent favorite gracefully`() = runTest {
        // When - Should not throw exception
        favoriteDao.removeFavorite(999, 999, 999)

        // Then
        favoriteDao.getAllFavoriteChannelIds(999).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteByUserId should handle non-existent user gracefully`() = runTest {
        // Given
        setupTestData()
        val favorite = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        favoriteDao.addFavorite(favorite)

        // When
        favoriteDao.deleteByUserId(999)

        // Then - Should not affect existing favorites
        favoriteDao.getAllFavoriteChannelIds(1).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getFavoriteChannelIds should return only favorites for specific viewer`() = runTest {
        // Given
        setupTestData()
        val favorite1 = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        val favorite2 = TestDataFactory.createFavoriteChannelEntity(channelId = 2, viewerId = 1, userId = 1)
        val favorite3 = TestDataFactory.createFavoriteChannelEntity(channelId = 3, viewerId = 2, userId = 1)
        favoriteDao.addFavorite(favorite1)
        favoriteDao.addFavorite(favorite2)
        favoriteDao.addFavorite(favorite3)

        // When & Then
        favoriteDao.getFavoriteChannelIds(2, 1).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertTrue(result.contains(3))
            assertFalse(result.contains(1))
            assertFalse(result.contains(2))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllFavoriteChannelIds should return favorites for all viewers of user`() = runTest {
        // Given
        setupTestData()
        val favorite1 = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        val favorite2 = TestDataFactory.createFavoriteChannelEntity(channelId = 2, viewerId = 2, userId = 1)
        val favorite3 = TestDataFactory.createFavoriteChannelEntity(channelId = 3, viewerId = 1, userId = 2)
        favoriteDao.addFavorite(favorite1)
        favoriteDao.addFavorite(favorite2)
        favoriteDao.addFavorite(favorite3)

        // When & Then
        favoriteDao.getAllFavoriteChannelIds(1).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.containsAll(listOf(1, 2)))
            assertFalse(result.contains(3))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getViewerIdsWithFavorites should return distinct viewer IDs`() = runTest {
        // Given
        setupTestData()
        val favorite1 = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        val favorite2 = TestDataFactory.createFavoriteChannelEntity(channelId = 2, viewerId = 1, userId = 1)
        val favorite3 = TestDataFactory.createFavoriteChannelEntity(channelId = 3, viewerId = 2, userId = 1)
        favoriteDao.addFavorite(favorite1)
        favoriteDao.addFavorite(favorite2)
        favoriteDao.addFavorite(favorite3)

        // When & Then
        favoriteDao.getViewerIdsWithFavorites(1).test {
            val result = awaitItem()
            assertEquals(2, result.size) // Should be distinct
            assertTrue(result.containsAll(listOf(1, 2)))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isFavorite should return false initially`() = runTest {
        // Given
        setupTestData()
        // When & Then
        favoriteDao.isFavorite(1, 1, 1).test {
            val result = awaitItem()
            assertFalse(result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isFavorite should return true after adding favorite`() = runTest {
        // Given
        setupTestData()
        val favorite = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        favoriteDao.addFavorite(favorite)

        // When & Then
        favoriteDao.isFavorite(1, 1, 1).test {
            val result = awaitItem()
            assertTrue(result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isFavorite should return false for different viewer`() = runTest {
        // Given
        setupTestData()
        val favorite = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        favoriteDao.addFavorite(favorite)

        // When & Then
        favoriteDao.isFavorite(1, 2, 1).test {
            val result = awaitItem()
            assertFalse(result) // Different viewerId
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isFavorite should return false for different user`() = runTest {
        // Given
        setupTestData()
        val favorite = TestDataFactory.createFavoriteChannelEntity(channelId = 1, viewerId = 1, userId = 1)
        favoriteDao.addFavorite(favorite)

        // When & Then
        favoriteDao.isFavorite(1, 1, 2).test {
            val result = awaitItem()
            assertFalse(result) // Different userId
            cancelAndIgnoreRemainingEvents()
        }
    }
}

