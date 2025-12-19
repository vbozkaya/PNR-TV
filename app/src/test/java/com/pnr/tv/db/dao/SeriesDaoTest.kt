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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * SeriesDao için unit testler.
 * In-memory database kullanarak CRUD operasyonlarını test eder.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var seriesDao: SeriesDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        seriesDao = database.seriesDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun `insertAll should insert series successfully`() = runTest {
        // Given
        val series = TestDataFactory.createSeriesEntities(3)

        // When
        seriesDao.insertAll(series)

        // Then
        seriesDao.getAll().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertAll should replace existing series with same streamId`() = runTest {
        // Given
        val series1 = TestDataFactory.createSeriesEntity(streamId = 1, name = "Original Series")
        val series2 = TestDataFactory.createSeriesEntity(streamId = 1, name = "Updated Series")

        // When
        seriesDao.insertAll(listOf(series1))
        seriesDao.insertAll(listOf(series2))

        // Then
        seriesDao.getAll().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Updated Series", result[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAll should return all series ordered by added DESC`() = runTest {
        // Given
        val series1 = TestDataFactory.createSeriesEntity(streamId = 1, added = "2024-01-01")
        val series2 = TestDataFactory.createSeriesEntity(streamId = 2, added = "2024-01-02")
        val series3 = TestDataFactory.createSeriesEntity(streamId = 3, added = "2024-01-03")
        seriesDao.insertAll(listOf(series1, series2, series3))

        // When & Then
        seriesDao.getAll().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            // Should be ordered by added DESC (newest first)
            assertEquals(3, result[0].streamId)
            assertEquals(2, result[1].streamId)
            assertEquals(1, result[2].streamId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getByCategoryId should return series for specific category`() = runTest {
        // Given
        val series1 = TestDataFactory.createSeriesEntity(streamId = 1, categoryId = "1")
        val series2 = TestDataFactory.createSeriesEntity(streamId = 2, categoryId = "1")
        val series3 = TestDataFactory.createSeriesEntity(streamId = 3, categoryId = "2")
        seriesDao.insertAll(listOf(series1, series2, series3))

        // When & Then
        seriesDao.getByCategoryId("1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.all { it.categoryId == "1" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getByCategoryId should return empty list for non-existent category`() = runTest {
        // Given
        val series = TestDataFactory.createSeriesEntity(streamId = 1, categoryId = "1")
        seriesDao.insertAll(listOf(series))

        // When & Then
        seriesDao.getByCategoryId("999").test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRecentlyAdded should return limited number of series`() = runTest {
        // Given
        val series = TestDataFactory.createSeriesEntities(10)
        seriesDao.insertAll(series)

        // When
        val result = seriesDao.getRecentlyAdded(5)

        // Then
        assertEquals(5, result.size)
    }

    @Test
    fun `getByIds should return series with specified IDs`() = runTest {
        // Given
        val series = TestDataFactory.createSeriesEntities(5)
        seriesDao.insertAll(series)

        // When
        val result = seriesDao.getByIds(listOf(1, 3, 5))

        // Then
        assertEquals(3, result.size)
        assertTrue(result.map { it.streamId }.containsAll(listOf(1, 3, 5)))
    }

    @Test
    fun `getSeriesIdsWithTmdb should return only series with tmdbId`() = runTest {
        // Given
        val series1 = TestDataFactory.createSeriesEntity(streamId = 1, tmdbId = 12345)
        val series2 = TestDataFactory.createSeriesEntity(streamId = 2, tmdbId = null)
        val series3 = TestDataFactory.createSeriesEntity(streamId = 3, tmdbId = 67890)
        seriesDao.insertAll(listOf(series1, series2, series3))

        // When
        val result = seriesDao.getSeriesIdsWithTmdb()

        // Then
        assertEquals(2, result.size)
        assertTrue(result.containsAll(listOf(1, 3)))
        assertTrue(!result.contains(2))
    }

    @Test
    fun `clearAll should remove all series`() = runTest {
        // Given
        val series = TestDataFactory.createSeriesEntities(5)
        seriesDao.insertAll(series)

        // When
        seriesDao.clearAll()

        // Then
        seriesDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replaceAll should clear and insert new series`() = runTest {
        // Given
        val series1 = TestDataFactory.createSeriesEntities(3)
        seriesDao.insertAll(series1)

        val series2 = TestDataFactory.createSeriesEntities(2).mapIndexed { index, series ->
            series.copy(streamId = index + 10)
        }

        // When
        seriesDao.replaceAll(series2)

        // Then
        seriesDao.getAll().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.all { it.streamId >= 10 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRecentlyAdded should return all series if limit is greater than count`() = runTest {
        // Given
        val series = TestDataFactory.createSeriesEntities(3)
        seriesDao.insertAll(series)

        // When
        val result = seriesDao.getRecentlyAdded(10)

        // Then
        assertEquals(3, result.size)
    }

    @Test
    fun `getRecentlyAdded should return empty list when limit is 0`() = runTest {
        // Given
        val series = TestDataFactory.createSeriesEntities(5)
        seriesDao.insertAll(series)

        // When
        val result = seriesDao.getRecentlyAdded(0)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getRecentlyAdded should return series ordered by added DESC`() = runTest {
        // Given
        val series1 = TestDataFactory.createSeriesEntity(streamId = 1, added = "2024-01-01")
        val series2 = TestDataFactory.createSeriesEntity(streamId = 2, added = "2024-01-02")
        val series3 = TestDataFactory.createSeriesEntity(streamId = 3, added = "2024-01-03")
        seriesDao.insertAll(listOf(series1, series2, series3))

        // When
        val result = seriesDao.getRecentlyAdded(2)

        // Then
        assertEquals(2, result.size)
        // Should be ordered by added DESC (newest first)
        assertEquals(3, result[0].streamId)
        assertEquals(2, result[1].streamId)
    }

    @Test
    fun `getByIds should return empty list for non-existent IDs`() = runTest {
        // Given
        val series = TestDataFactory.createSeriesEntities(3)
        seriesDao.insertAll(series)

        // When
        val result = seriesDao.getByIds(listOf(999, 1000))

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getByIds should return empty list when empty list provided`() = runTest {
        // Given
        val series = TestDataFactory.createSeriesEntities(3)
        seriesDao.insertAll(series)

        // When
        val result = seriesDao.getByIds(emptyList())

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getByIds should return only existing series when partial IDs provided`() = runTest {
        // Given
        val series = TestDataFactory.createSeriesEntities(5)
        seriesDao.insertAll(series)

        // When
        val result = seriesDao.getByIds(listOf(1, 999, 3, 1000))

        // Then
        assertEquals(2, result.size)
        assertTrue(result.map { it.streamId }.containsAll(listOf(1, 3)))
        assertTrue(!result.map { it.streamId }.contains(999))
        assertTrue(!result.map { it.streamId }.contains(1000))
    }

    @Test
    fun `getSeriesIdsWithTmdb should return empty list when no series have tmdbId`() = runTest {
        // Given
        val series1 = TestDataFactory.createSeriesEntity(streamId = 1, tmdbId = null)
        val series2 = TestDataFactory.createSeriesEntity(streamId = 2, tmdbId = null)
        seriesDao.insertAll(listOf(series1, series2))

        // When
        val result = seriesDao.getSeriesIdsWithTmdb()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `replaceAll should clear all when empty list provided`() = runTest {
        // Given
        val series = TestDataFactory.createSeriesEntities(3)
        seriesDao.insertAll(series)

        // When
        seriesDao.replaceAll(emptyList())

        // Then
        seriesDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAll should emit empty list initially`() = runTest {
        // When & Then
        seriesDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertAll should handle empty list`() = runTest {
        // When
        seriesDao.insertAll(emptyList())

        // Then
        seriesDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getByCategoryId should return empty list when categoryId is null in database`() = runTest {
        // Given
        val series1 = TestDataFactory.createSeriesEntity(streamId = 1, categoryId = null)
        val series2 = TestDataFactory.createSeriesEntity(streamId = 2, categoryId = "1")
        seriesDao.insertAll(listOf(series1, series2))

        // When & Then - Querying a specific category should not return series with null categoryId
        seriesDao.getByCategoryId("1").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(2, result[0].streamId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

