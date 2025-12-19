package com.pnr.tv.db.dao

import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import com.pnr.tv.db.AppDatabase
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
 * PlaybackPositionDao için unit testler.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackPositionDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var playbackPositionDao: PlaybackPositionDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        playbackPositionDao = database.playbackPositionDao()
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
    fun `upsert should insert new playback position`() = runTest {
        // Given
        setupTestData()
        val position = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_1",
            userId = 1,
            positionMs = 30000L,
            durationMs = 120000L,
        )

        // When
        playbackPositionDao.upsert(position)

        // Then
        val result = playbackPositionDao.getPosition("movie_1", 1)
        assertNotNull(result)
        assertEquals(30000L, result?.positionMs)
        assertEquals(120000L, result?.durationMs)
    }

    @Test
    fun `upsert should update existing playback position`() = runTest {
        // Given
        setupTestData()
        val position1 = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_1",
            userId = 1,
            positionMs = 30000L,
            durationMs = 120000L,
        )
        val position2 = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_1",
            userId = 1,
            positionMs = 60000L,
            durationMs = 120000L,
        )
        playbackPositionDao.upsert(position1)

        // When
        playbackPositionDao.upsert(position2)

        // Then
        val result = playbackPositionDao.getPosition("movie_1", 1)
        assertNotNull(result)
        assertEquals(60000L, result?.positionMs)
    }

    @Test
    fun `getPosition should return null for non-existent position`() = runTest {
        // Given
        setupTestData()
        // When
        val result = playbackPositionDao.getPosition("movie_999", 1)

        // Then
        assertNull(result)
    }

    @Test
    fun `deletePosition should remove playback position`() = runTest {
        // Given
        setupTestData()
        val position = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_1",
            userId = 1,
        )
        playbackPositionDao.upsert(position)

        // When
        playbackPositionDao.deletePosition("movie_1", 1)

        // Then
        val result = playbackPositionDao.getPosition("movie_1", 1)
        assertNull(result)
    }

    @Test
    fun `deleteByUserId should remove all positions for user`() = runTest {
        // Given
        setupTestData()
        val position1 = TestDataFactory.createPlaybackPositionEntity(contentId = "movie_1", userId = 1)
        val position2 = TestDataFactory.createPlaybackPositionEntity(contentId = "movie_2", userId = 2)
        playbackPositionDao.upsert(position1)
        playbackPositionDao.upsert(position2)

        // When
        playbackPositionDao.deleteByUserId(1)

        // Then
        assertNull(playbackPositionDao.getPosition("movie_1", 1))
        assertNotNull(playbackPositionDao.getPosition("movie_2", 2))
    }

    @Test
    fun `deleteAll should remove all positions`() = runTest {
        // Given
        setupTestData()
        val position1 = TestDataFactory.createPlaybackPositionEntity(contentId = "movie_1", userId = 1)
        val position2 = TestDataFactory.createPlaybackPositionEntity(contentId = "movie_2", userId = 2)
        playbackPositionDao.upsert(position1)
        playbackPositionDao.upsert(position2)

        // When
        playbackPositionDao.deleteAll()

        // Then
        assertNull(playbackPositionDao.getPosition("movie_1", 1))
        assertNull(playbackPositionDao.getPosition("movie_2", 2))
    }

    @Test
    fun `deleteOlderThan should remove positions older than timestamp`() = runTest {
        // Given
        setupTestData()
        val now = System.currentTimeMillis()
        val position1 = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_1",
            userId = 1,
            lastUpdated = now - 100000L, // 100 seconds ago
        )
        val position2 = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_2",
            userId = 1,
            lastUpdated = now - 50000L, // 50 seconds ago
        )
        playbackPositionDao.upsert(position1)
        playbackPositionDao.upsert(position2)

        // When
        playbackPositionDao.deleteOlderThan(now - 75000L) // Delete older than 75 seconds

        // Then
        assertNull(playbackPositionDao.getPosition("movie_1", 1))
        assertNotNull(playbackPositionDao.getPosition("movie_2", 1))
    }

    @Test
    fun `getPosition should return null for different user`() = runTest {
        // Given
        setupTestData()
        val position = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_1",
            userId = 1,
            positionMs = 30000L,
        )
        playbackPositionDao.upsert(position)

        // When
        val result = playbackPositionDao.getPosition("movie_1", 2)

        // Then
        assertNull(result) // Different userId
    }

    @Test
    fun `getPosition should return null for different contentId`() = runTest {
        // Given
        setupTestData()
        val position = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_1",
            userId = 1,
            positionMs = 30000L,
        )
        playbackPositionDao.upsert(position)

        // When
        val result = playbackPositionDao.getPosition("movie_2", 1)

        // Then
        assertNull(result) // Different contentId
    }

    @Test
    fun `upsert should update lastUpdated timestamp`() = runTest {
        // Given
        setupTestData()
        val now = System.currentTimeMillis()
        val position1 = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_1",
            userId = 1,
            positionMs = 30000L,
            lastUpdated = now - 1000L,
        )
        playbackPositionDao.upsert(position1)

        // When - Update with newer timestamp
        val position2 = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_1",
            userId = 1,
            positionMs = 60000L,
            lastUpdated = now,
        )
        playbackPositionDao.upsert(position2)

        // Then
        val result = playbackPositionDao.getPosition("movie_1", 1)
        assertNotNull(result)
        assertEquals(now, result?.lastUpdated)
        assertEquals(60000L, result?.positionMs)
    }

    @Test
    fun `deletePosition should handle non-existent position gracefully`() = runTest {
        // Given
        setupTestData()
        // When - Should not throw exception
        playbackPositionDao.deletePosition("movie_999", 1)

        // Then
        val result = playbackPositionDao.getPosition("movie_999", 1)
        assertNull(result)
    }

    @Test
    fun `deleteByUserId should handle non-existent user gracefully`() = runTest {
        // Given
        setupTestData()
        val position = TestDataFactory.createPlaybackPositionEntity(contentId = "movie_1", userId = 1)
        playbackPositionDao.upsert(position)

        // When
        playbackPositionDao.deleteByUserId(999)

        // Then - Should not affect existing positions
        val result = playbackPositionDao.getPosition("movie_1", 1)
        assertNotNull(result)
    }

    @Test
    fun `deleteOlderThan should not remove positions newer than timestamp`() = runTest {
        // Given
        setupTestData()
        val now = System.currentTimeMillis()
        val position1 = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_1",
            userId = 1,
            lastUpdated = now - 50000L, // 50 seconds ago
        )
        val position2 = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_2",
            userId = 1,
            lastUpdated = now - 30000L, // 30 seconds ago
        )
        playbackPositionDao.upsert(position1)
        playbackPositionDao.upsert(position2)

        // When - Delete older than 100 seconds (both should remain)
        playbackPositionDao.deleteOlderThan(now - 100000L)

        // Then
        assertNotNull(playbackPositionDao.getPosition("movie_1", 1))
        assertNotNull(playbackPositionDao.getPosition("movie_2", 1))
    }

    @Test
    fun `deleteOlderThan should remove all positions when timestamp is current`() = runTest {
        // Given
        setupTestData()
        val now = System.currentTimeMillis()
        val position1 = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_1",
            userId = 1,
            lastUpdated = now - 100000L,
        )
        val position2 = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_2",
            userId = 1,
            lastUpdated = now - 50000L,
        )
        playbackPositionDao.upsert(position1)
        playbackPositionDao.upsert(position2)

        // When - Delete older than now (all should be deleted)
        playbackPositionDao.deleteOlderThan(now)

        // Then
        assertNull(playbackPositionDao.getPosition("movie_1", 1))
        assertNull(playbackPositionDao.getPosition("movie_2", 1))
    }

    @Test
    fun `upsert should handle different content types`() = runTest {
        // Given
        setupTestData()
        val moviePosition = TestDataFactory.createPlaybackPositionEntity(
            contentId = "movie_1",
            userId = 1,
            positionMs = 30000L,
        )
        val episodePosition = TestDataFactory.createPlaybackPositionEntity(
            contentId = "series_1_s1_e1",
            userId = 1,
            positionMs = 60000L,
        )

        // When
        playbackPositionDao.upsert(moviePosition)
        playbackPositionDao.upsert(episodePosition)

        // Then
        val movieResult = playbackPositionDao.getPosition("movie_1", 1)
        val episodeResult = playbackPositionDao.getPosition("series_1_s1_e1", 1)
        assertNotNull(movieResult)
        assertNotNull(episodeResult)
        assertEquals(30000L, movieResult?.positionMs)
        assertEquals(60000L, episodeResult?.positionMs)
    }
}

