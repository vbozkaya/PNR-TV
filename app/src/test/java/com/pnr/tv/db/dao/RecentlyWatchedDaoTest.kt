package com.pnr.tv.db.dao

import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import app.cash.turbine.test
import com.pnr.tv.db.AppDatabase
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
 * RecentlyWatchedDao için unit testler.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class RecentlyWatchedDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var recentlyWatchedDao: RecentlyWatchedDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        recentlyWatchedDao = database.recentlyWatchedDao()
    }
    
    private suspend fun setupTestData() {
        // Insert users for foreign key constraints
        val userDao = database.userDao()
        userDao.insertUser(TestDataFactory.createUserAccountEntity(id = 1, accountName = "User 1"))
        userDao.insertUser(TestDataFactory.createUserAccountEntity(id = 2, accountName = "User 2"))
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun `upsert should insert new recently watched channel`() = runTest {
        // Given
        setupTestData()
        val recent = TestDataFactory.createRecentlyWatchedEntity(channelId = 1, userId = 1)

        // When
        recentlyWatchedDao.upsert(recent)

        // Then
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 10).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(1, result[0])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert should update existing recently watched channel`() = runTest {
        // Given
        setupTestData()
        val recent1 = TestDataFactory.createRecentlyWatchedEntity(channelId = 1, userId = 1, watchedAt = 1000L)
        val recent2 = TestDataFactory.createRecentlyWatchedEntity(channelId = 1, userId = 1, watchedAt = 2000L)
        recentlyWatchedDao.upsert(recent1)

        // When
        recentlyWatchedDao.upsert(recent2)

        // Then
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 10).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(1, result[0])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRecentlyWatchedChannelIds should return channels ordered by watchedAt DESC`() = runTest {
        // Given
        setupTestData()
        val recent1 = TestDataFactory.createRecentlyWatchedEntity(channelId = 1, userId = 1, watchedAt = 1000L)
        val recent2 = TestDataFactory.createRecentlyWatchedEntity(channelId = 2, userId = 1, watchedAt = 2000L)
        val recent3 = TestDataFactory.createRecentlyWatchedEntity(channelId = 3, userId = 1, watchedAt = 3000L)
        recentlyWatchedDao.upsert(recent1)
        recentlyWatchedDao.upsert(recent2)
        recentlyWatchedDao.upsert(recent3)

        // When & Then
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 10).test {
            val result = awaitItem()
            assertEquals(3, result.size)
            // Should be ordered by watchedAt DESC (newest first)
            assertEquals(3, result[0])
            assertEquals(2, result[1])
            assertEquals(1, result[2])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRecentlyWatchedChannelIds should respect limit`() = runTest {
        // Given
        setupTestData()
        for (i in 1..10) {
            val recent = TestDataFactory.createRecentlyWatchedEntity(channelId = i, userId = 1, watchedAt = i * 1000L)
            recentlyWatchedDao.upsert(recent)
        }

        // When & Then
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 5).test {
            val result = awaitItem()
            assertEquals(5, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `trim should keep only limited number of channels`() = runTest {
        // Given
        setupTestData()
        for (i in 1..10) {
            val recent = TestDataFactory.createRecentlyWatchedEntity(channelId = i, userId = 1, watchedAt = i * 1000L)
            recentlyWatchedDao.upsert(recent)
        }

        // When
        recentlyWatchedDao.trim(1, 5)

        // Then
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 10).test {
            val result = awaitItem()
            assertEquals(5, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteByUserId should remove all recently watched for user`() = runTest {
        // Given
        setupTestData()
        val recent1 = TestDataFactory.createRecentlyWatchedEntity(channelId = 1, userId = 1)
        val recent2 = TestDataFactory.createRecentlyWatchedEntity(channelId = 2, userId = 2)
        recentlyWatchedDao.upsert(recent1)
        recentlyWatchedDao.upsert(recent2)

        // When
        recentlyWatchedDao.deleteByUserId(1)

        // Then
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 10).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        recentlyWatchedDao.getRecentlyWatchedChannelIds(2, 10).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAll should remove all recently watched`() = runTest {
        // Given
        setupTestData()
        val recent1 = TestDataFactory.createRecentlyWatchedEntity(channelId = 1, userId = 1)
        val recent2 = TestDataFactory.createRecentlyWatchedEntity(channelId = 2, userId = 2)
        recentlyWatchedDao.upsert(recent1)
        recentlyWatchedDao.upsert(recent2)

        // When
        recentlyWatchedDao.deleteAll()

        // Then
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 10).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRecentlyWatchedChannelIds should return empty list when no recently watched exist`() = runTest {
        // Given
        setupTestData()
        // When & Then
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 10).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRecentlyWatchedChannelIds should return all channels when limit is greater than count`() = runTest {
        // Given
        setupTestData()
        for (i in 1..5) {
            val recent = TestDataFactory.createRecentlyWatchedEntity(channelId = i, userId = 1, watchedAt = i * 1000L)
            recentlyWatchedDao.upsert(recent)
        }

        // When & Then
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 10).test {
            val result = awaitItem()
            assertEquals(5, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRecentlyWatchedChannelIds should return empty list when limit is 0`() = runTest {
        // Given
        setupTestData()
        val recent = TestDataFactory.createRecentlyWatchedEntity(channelId = 1, userId = 1)
        recentlyWatchedDao.upsert(recent)

        // When & Then
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 0).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `trim should handle when count is less than limit`() = runTest {
        // Given
        setupTestData()
        for (i in 1..3) {
            val recent = TestDataFactory.createRecentlyWatchedEntity(channelId = i, userId = 1, watchedAt = i * 1000L)
            recentlyWatchedDao.upsert(recent)
        }

        // When
        recentlyWatchedDao.trim(1, 5)

        // Then
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 10).test {
            val result = awaitItem()
            assertEquals(3, result.size) // Should keep all since count < limit
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `trim should keep newest channels when count exceeds limit`() = runTest {
        // Given
        setupTestData()
        for (i in 1..10) {
            val recent = TestDataFactory.createRecentlyWatchedEntity(channelId = i, userId = 1, watchedAt = i * 1000L)
            recentlyWatchedDao.upsert(recent)
        }

        // When
        recentlyWatchedDao.trim(1, 5)

        // Then
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 10).test {
            val result = awaitItem()
            assertEquals(5, result.size)
            // Should keep newest (highest watchedAt)
            assertTrue(result.containsAll(listOf(6, 7, 8, 9, 10)))
            assertFalse(result.contains(1))
            assertFalse(result.contains(2))
            assertFalse(result.contains(3))
            assertFalse(result.contains(4))
            assertFalse(result.contains(5))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteByUserId should handle non-existent user gracefully`() = runTest {
        // Given
        setupTestData()
        val recent = TestDataFactory.createRecentlyWatchedEntity(channelId = 1, userId = 1)
        recentlyWatchedDao.upsert(recent)

        // When
        recentlyWatchedDao.deleteByUserId(999)

        // Then - Should not affect existing records
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 10).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRecentlyWatchedChannelIds should return only channels for specific user`() = runTest {
        // Given
        setupTestData()
        val recent1 = TestDataFactory.createRecentlyWatchedEntity(channelId = 1, userId = 1, watchedAt = 1000L)
        val recent2 = TestDataFactory.createRecentlyWatchedEntity(channelId = 2, userId = 1, watchedAt = 2000L)
        val recent3 = TestDataFactory.createRecentlyWatchedEntity(channelId = 3, userId = 2, watchedAt = 3000L)
        recentlyWatchedDao.upsert(recent1)
        recentlyWatchedDao.upsert(recent2)
        recentlyWatchedDao.upsert(recent3)

        // When & Then
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 10).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.containsAll(listOf(1, 2)))
            assertFalse(result.contains(3))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert should update watchedAt timestamp when same channel is watched again`() = runTest {
        // Given
        setupTestData()
        val recent1 = TestDataFactory.createRecentlyWatchedEntity(channelId = 1, userId = 1, watchedAt = 1000L)
        recentlyWatchedDao.upsert(recent1)

        // When - Same channel watched again with newer timestamp
        val recent2 = TestDataFactory.createRecentlyWatchedEntity(channelId = 1, userId = 1, watchedAt = 2000L)
        recentlyWatchedDao.upsert(recent2)

        // Then - Should be at the top (newest)
        recentlyWatchedDao.getRecentlyWatchedChannelIds(1, 10).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(1, result[0])
            cancelAndIgnoreRemainingEvents()
        }
    }
}

