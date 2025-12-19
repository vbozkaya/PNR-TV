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
 * TmdbCacheDao için unit testler.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class TmdbCacheDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var tmdbCacheDao: TmdbCacheDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        tmdbCacheDao = database.tmdbCacheDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun `insertCache should insert cache successfully`() = runTest {
        // Given
        val cache = TestDataFactory.createTmdbCacheEntity(tmdbId = 12345, title = "Test Movie")

        // When
        tmdbCacheDao.insertCache(cache)

        // Then
        val result = tmdbCacheDao.getCacheByTmdbId(12345)
        assertNotNull(result)
        assertEquals("Test Movie", result?.title)
    }

    @Test
    fun `insertCache should replace existing cache with same tmdbId`() = runTest {
        // Given
        val cache1 = TestDataFactory.createTmdbCacheEntity(tmdbId = 12345, title = "Original")
        val cache2 = TestDataFactory.createTmdbCacheEntity(tmdbId = 12345, title = "Updated")
        tmdbCacheDao.insertCache(cache1)

        // When
        tmdbCacheDao.insertCache(cache2)

        // Then
        val result = tmdbCacheDao.getCacheByTmdbId(12345)
        assertEquals("Updated", result?.title)
    }

    @Test
    fun `getCacheByTmdbId should return null for non-existent ID`() = runTest {
        // When
        val result = tmdbCacheDao.getCacheByTmdbId(99999)

        // Then
        assertNull(result)
    }

    @Test
    fun `getCacheByTmdbIds should return caches for specified IDs`() = runTest {
        // Given
        val cache1 = TestDataFactory.createTmdbCacheEntity(tmdbId = 12345, title = "Movie 1")
        val cache2 = TestDataFactory.createTmdbCacheEntity(tmdbId = 67890, title = "Movie 2")
        val cache3 = TestDataFactory.createTmdbCacheEntity(tmdbId = 11111, title = "Movie 3")
        tmdbCacheDao.insertCache(cache1)
        tmdbCacheDao.insertCache(cache2)
        tmdbCacheDao.insertCache(cache3)

        // When
        val result = tmdbCacheDao.getCacheByTmdbIds(listOf(12345, 67890))

        // Then
        assertEquals(2, result.size)
        assertTrue(result.map { it.tmdbId }.containsAll(listOf(12345, 67890)))
    }

    @Test
    fun `getCacheByTmdbIds should return empty list for non-existent IDs`() = runTest {
        // When
        val result = tmdbCacheDao.getCacheByTmdbIds(listOf(99999, 88888))

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deleteOldCache should remove caches older than timestamp`() = runTest {
        // Given
        val now = System.currentTimeMillis()
        val cache1 = TestDataFactory.createTmdbCacheEntity(tmdbId = 12345, cacheTime = now - 100000L)
        val cache2 = TestDataFactory.createTmdbCacheEntity(tmdbId = 67890, cacheTime = now - 50000L)
        tmdbCacheDao.insertCache(cache1)
        tmdbCacheDao.insertCache(cache2)

        // When
        val deletedCount = tmdbCacheDao.deleteOldCache(now - 75000L)

        // Then
        assertEquals(1, deletedCount)
        assertNull(tmdbCacheDao.getCacheByTmdbId(12345))
        assertNotNull(tmdbCacheDao.getCacheByTmdbId(67890))
    }

    @Test
    fun `clearAllCache should remove all caches`() = runTest {
        // Given
        val cache1 = TestDataFactory.createTmdbCacheEntity(tmdbId = 12345)
        val cache2 = TestDataFactory.createTmdbCacheEntity(tmdbId = 67890)
        tmdbCacheDao.insertCache(cache1)
        tmdbCacheDao.insertCache(cache2)

        // When
        val deletedCount = tmdbCacheDao.clearAllCache()

        // Then
        assertEquals(2, deletedCount)
        assertNull(tmdbCacheDao.getCacheByTmdbId(12345))
        assertNull(tmdbCacheDao.getCacheByTmdbId(67890))
    }

    @Test
    fun `getCacheCount should return total number of caches`() = runTest {
        // Given
        val cache1 = TestDataFactory.createTmdbCacheEntity(tmdbId = 12345)
        val cache2 = TestDataFactory.createTmdbCacheEntity(tmdbId = 67890)
        tmdbCacheDao.insertCache(cache1)
        tmdbCacheDao.insertCache(cache2)

        // When
        val count = tmdbCacheDao.getCacheCount()

        // Then
        assertEquals(2, count)
    }

    @Test
    fun `deleteCacheById should remove specific cache`() = runTest {
        // Given
        val cache1 = TestDataFactory.createTmdbCacheEntity(tmdbId = 12345)
        val cache2 = TestDataFactory.createTmdbCacheEntity(tmdbId = 67890)
        tmdbCacheDao.insertCache(cache1)
        tmdbCacheDao.insertCache(cache2)

        // When
        val deletedCount = tmdbCacheDao.deleteCacheById(12345)

        // Then
        assertEquals(1, deletedCount)
        assertNull(tmdbCacheDao.getCacheByTmdbId(12345))
        assertNotNull(tmdbCacheDao.getCacheByTmdbId(67890))
    }

    @Test
    fun `getCacheByTmdbIds should return empty list when empty list provided`() = runTest {
        // When
        val result = tmdbCacheDao.getCacheByTmdbIds(emptyList())

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getCacheByTmdbIds should return only existing caches when partial IDs provided`() = runTest {
        // Given
        val cache1 = TestDataFactory.createTmdbCacheEntity(tmdbId = 12345, title = "Movie 1")
        val cache2 = TestDataFactory.createTmdbCacheEntity(tmdbId = 67890, title = "Movie 2")
        tmdbCacheDao.insertCache(cache1)
        tmdbCacheDao.insertCache(cache2)

        // When
        val result = tmdbCacheDao.getCacheByTmdbIds(listOf(12345, 99999, 67890))

        // Then
        assertEquals(2, result.size)
        assertTrue(result.map { it.tmdbId }.containsAll(listOf(12345, 67890)))
        assertTrue(!result.map { it.tmdbId }.contains(99999))
    }

    @Test
    fun `deleteOldCache should return 0 when no caches are older than timestamp`() = runTest {
        // Given
        val now = System.currentTimeMillis()
        val cache1 = TestDataFactory.createTmdbCacheEntity(tmdbId = 12345, cacheTime = now - 50000L)
        val cache2 = TestDataFactory.createTmdbCacheEntity(tmdbId = 67890, cacheTime = now - 30000L)
        tmdbCacheDao.insertCache(cache1)
        tmdbCacheDao.insertCache(cache2)

        // When - Delete older than 100000 seconds (both should remain)
        val deletedCount = tmdbCacheDao.deleteOldCache(now - 100000L)

        // Then
        assertEquals(0, deletedCount)
        assertNotNull(tmdbCacheDao.getCacheByTmdbId(12345))
        assertNotNull(tmdbCacheDao.getCacheByTmdbId(67890))
    }

    @Test
    fun `deleteOldCache should remove all caches when timestamp is current`() = runTest {
        // Given
        val now = System.currentTimeMillis()
        val cache1 = TestDataFactory.createTmdbCacheEntity(tmdbId = 12345, cacheTime = now - 100000L)
        val cache2 = TestDataFactory.createTmdbCacheEntity(tmdbId = 67890, cacheTime = now - 50000L)
        tmdbCacheDao.insertCache(cache1)
        tmdbCacheDao.insertCache(cache2)

        // When - Delete older than now (all should be deleted)
        val deletedCount = tmdbCacheDao.deleteOldCache(now)

        // Then
        assertEquals(2, deletedCount)
        assertNull(tmdbCacheDao.getCacheByTmdbId(12345))
        assertNull(tmdbCacheDao.getCacheByTmdbId(67890))
    }

    @Test
    fun `clearAllCache should return 0 when cache is empty`() = runTest {
        // When
        val deletedCount = tmdbCacheDao.clearAllCache()

        // Then
        assertEquals(0, deletedCount)
    }

    @Test
    fun `getCacheCount should return 0 when cache is empty`() = runTest {
        // When
        val count = tmdbCacheDao.getCacheCount()

        // Then
        assertEquals(0, count)
    }

    @Test
    fun `deleteCacheById should return 0 for non-existent ID`() = runTest {
        // When
        val deletedCount = tmdbCacheDao.deleteCacheById(99999)

        // Then
        assertEquals(0, deletedCount)
    }

    @Test
    fun `insertCache should handle cache with all fields populated`() = runTest {
        // Given
        val cache = TestDataFactory.createTmdbCacheEntity(
            tmdbId = 12345,
            title = "Test Movie",
            director = "Test Director",
            cast = "Actor1, Actor2",
            overview = "Test Overview",
            cacheTime = System.currentTimeMillis(),
        )

        // When
        tmdbCacheDao.insertCache(cache)

        // Then
        val result = tmdbCacheDao.getCacheByTmdbId(12345)
        assertNotNull(result)
        assertEquals("Test Movie", result?.title)
        assertEquals("Test Director", result?.director)
        assertEquals("Actor1, Actor2", result?.cast)
        assertEquals("Test Overview", result?.overview)
    }

    @Test
    fun `insertCache should handle cache with null fields`() = runTest {
        // Given
        val cache = com.pnr.tv.db.entity.TmdbCacheEntity(
            tmdbId = 12345,
            title = null,
            director = null,
            cast = null,
            overview = null,
            cacheTime = System.currentTimeMillis(),
            rawJson = null,
        )

        // When
        tmdbCacheDao.insertCache(cache)

        // Then
        val result = tmdbCacheDao.getCacheByTmdbId(12345)
        assertNotNull(result)
        assertEquals(12345, result?.tmdbId)
        assertNull(result?.title)
        assertNull(result?.director)
        assertNull(result?.cast)
        assertNull(result?.overview)
    }
}

