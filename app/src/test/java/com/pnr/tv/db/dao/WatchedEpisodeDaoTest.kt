package com.pnr.tv.db.dao

import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import app.cash.turbine.test
import com.pnr.tv.db.AppDatabase
import com.pnr.tv.db.entity.WatchedEpisodeEntity
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
 * WatchedEpisodeDao için unit testler.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class WatchedEpisodeDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var watchedEpisodeDao: WatchedEpisodeDao

    @Before
    fun setup() = runTest {
        val context = RuntimeEnvironment.getApplication()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        watchedEpisodeDao = database.watchedEpisodeDao()
        
        // Insert a user for foreign key constraint
        val userDao = database.userDao()
        val user = com.pnr.tv.testdata.TestDataFactory.createUserAccountEntity(id = 1)
        userDao.insertUser(user)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun `markAsWatched should insert watched episode successfully`() = runTest {
        // Given
        val episode = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e1",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedTimestamp = System.currentTimeMillis(),
        )

        // When
        watchedEpisodeDao.markAsWatched(episode)

        // Then
        val result = watchedEpisodeDao.getWatchedEpisode("series_1_s1_e1", 1)
        assertNotNull(result)
        assertEquals(1, result?.seasonNumber)
        assertEquals(1, result?.episodeNumber)
    }

    @Test
    fun `markAsWatched should update existing watched episode`() = runTest {
        // Given
        val episode1 = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e1",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedTimestamp = 1000L,
            watchProgress = 50,
        )
        val episode2 = episode1.copy(watchedTimestamp = 2000L, watchProgress = 100)
        watchedEpisodeDao.markAsWatched(episode1)

        // When
        watchedEpisodeDao.markAsWatched(episode2)

        // Then
        val result = watchedEpisodeDao.getWatchedEpisode("series_1_s1_e1", 1)
        assertEquals(2000L, result?.watchedTimestamp)
        assertEquals(100, result?.watchProgress)
    }

    @Test
    fun `getWatchedEpisodesForSeries should return episodes for series ordered by season and episode`() = runTest {
        // Given
        val episode1 = WatchedEpisodeEntity(
            episodeId = "series_1_s2_e1",
            userId = 1,
            seriesId = 1,
            seasonNumber = 2,
            episodeNumber = 1,
            watchedTimestamp = System.currentTimeMillis(),
        )
        val episode2 = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e2",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 2,
            watchedTimestamp = System.currentTimeMillis(),
        )
        val episode3 = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e1",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedTimestamp = System.currentTimeMillis(),
        )
        watchedEpisodeDao.markAsWatched(episode1)
        watchedEpisodeDao.markAsWatched(episode2)
        watchedEpisodeDao.markAsWatched(episode3)

        // When & Then
        watchedEpisodeDao.getWatchedEpisodesForSeries(1, 1).test {
            val result = awaitItem()
            assertEquals(3, result.size)
            // Should be ordered by seasonNumber, episodeNumber
            assertEquals(1, result[0].seasonNumber)
            assertEquals(1, result[0].episodeNumber)
            assertEquals(1, result[1].seasonNumber)
            assertEquals(2, result[1].episodeNumber)
            assertEquals(2, result[2].seasonNumber)
            assertEquals(1, result[2].episodeNumber)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getWatchedEpisodesForSeries should return only episodes for specific series and user`() = runTest {
        // Given
        val episode1 = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e1",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedTimestamp = System.currentTimeMillis(),
        )
        val episode2 = WatchedEpisodeEntity(
            episodeId = "series_2_s1_e1",
            userId = 1,
            seriesId = 2,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedTimestamp = System.currentTimeMillis(),
        )
        watchedEpisodeDao.markAsWatched(episode1)
        watchedEpisodeDao.markAsWatched(episode2)

        // When & Then
        watchedEpisodeDao.getWatchedEpisodesForSeries(1, 1).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(1, result[0].seriesId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getWatchedEpisode should return null for non-existent episode`() = runTest {
        // When
        val result = watchedEpisodeDao.getWatchedEpisode("non_existent", 1)

        // Then
        assertNull(result)
    }

    @Test
    fun `removeWatchedEpisode should delete watched episode`() = runTest {
        // Given
        val episode = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e1",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedTimestamp = System.currentTimeMillis(),
        )
        watchedEpisodeDao.markAsWatched(episode)

        // When
        watchedEpisodeDao.removeWatchedEpisode("series_1_s1_e1", 1)

        // Then
        val result = watchedEpisodeDao.getWatchedEpisode("series_1_s1_e1", 1)
        assertNull(result)
    }

    @Test
    fun `getLastWatchedEpisode should return most recently watched episode`() = runTest {
        // Given
        val episode1 = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e1",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedTimestamp = 1000L,
        )
        val episode2 = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e2",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 2,
            watchedTimestamp = 2000L,
        )
        watchedEpisodeDao.markAsWatched(episode1)
        watchedEpisodeDao.markAsWatched(episode2)

        // When
        val result = watchedEpisodeDao.getLastWatchedEpisode(1, 1)

        // Then
        assertNotNull(result)
        assertEquals(2, result?.episodeNumber)
        assertEquals(2000L, result?.watchedTimestamp)
    }

    @Test
    fun `deleteByUserId should remove all watched episodes for user`() = runTest {
        // Given
        val episode1 = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e1",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedTimestamp = System.currentTimeMillis(),
        )
        watchedEpisodeDao.markAsWatched(episode1)

        // When
        watchedEpisodeDao.deleteByUserId(1)

        // Then
        val result = watchedEpisodeDao.getWatchedEpisode("series_1_s1_e1", 1)
        assertNull(result)
    }

    @Test
    fun `clearAll should remove all watched episodes`() = runTest {
        // Given
        val episode1 = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e1",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedTimestamp = System.currentTimeMillis(),
        )
        watchedEpisodeDao.markAsWatched(episode1)

        // When
        watchedEpisodeDao.clearAll()

        // Then
        val result = watchedEpisodeDao.getWatchedEpisode("series_1_s1_e1", 1)
        assertNull(result)
    }

    @Test
    fun `getWatchedEpisodesForSeries should return empty list when no episodes watched`() = runTest {
        // When & Then
        watchedEpisodeDao.getWatchedEpisodesForSeries(1, 1).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getWatchedEpisodesForSeries should return empty list for different user`() = runTest {
        // Given
        val episode = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e1",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedTimestamp = System.currentTimeMillis(),
        )
        watchedEpisodeDao.markAsWatched(episode)

        // When & Then
        watchedEpisodeDao.getWatchedEpisodesForSeries(1, 2).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getWatchedEpisode should return null for different user`() = runTest {
        // Given
        val episode = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e1",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedTimestamp = System.currentTimeMillis(),
        )
        watchedEpisodeDao.markAsWatched(episode)

        // When
        val result = watchedEpisodeDao.getWatchedEpisode("series_1_s1_e1", 2)

        // Then
        assertNull(result) // Different userId
    }

    @Test
    fun `removeWatchedEpisode should handle non-existent episode gracefully`() = runTest {
        // When - Should not throw exception
        watchedEpisodeDao.removeWatchedEpisode("non_existent", 1)

        // Then
        val result = watchedEpisodeDao.getWatchedEpisode("non_existent", 1)
        assertNull(result)
    }

    @Test
    fun `getLastWatchedEpisode should return null when no episodes watched`() = runTest {
        // When
        val result = watchedEpisodeDao.getLastWatchedEpisode(1, 1)

        // Then
        assertNull(result)
    }

    @Test
    fun `getLastWatchedEpisode should return null for different user`() = runTest {
        // Given
        val episode = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e1",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedTimestamp = System.currentTimeMillis(),
        )
        watchedEpisodeDao.markAsWatched(episode)

        // When
        val result = watchedEpisodeDao.getLastWatchedEpisode(1, 2)

        // Then
        assertNull(result) // Different userId
    }

    @Test
    fun `deleteByUserId should handle non-existent user gracefully`() = runTest {
        // Given
        val episode = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e1",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedTimestamp = System.currentTimeMillis(),
        )
        watchedEpisodeDao.markAsWatched(episode)

        // When
        watchedEpisodeDao.deleteByUserId(999)

        // Then - Should not affect existing episodes
        val result = watchedEpisodeDao.getWatchedEpisode("series_1_s1_e1", 1)
        assertNotNull(result)
    }

    @Test
    fun `markAsWatched should handle watchProgress correctly`() = runTest {
        // Given
        val episode = WatchedEpisodeEntity(
            episodeId = "series_1_s1_e1",
            userId = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedTimestamp = System.currentTimeMillis(),
            watchProgress = 75,
        )

        // When
        watchedEpisodeDao.markAsWatched(episode)

        // Then
        val result = watchedEpisodeDao.getWatchedEpisode("series_1_s1_e1", 1)
        assertNotNull(result)
        assertEquals(75, result?.watchProgress)
    }
}

