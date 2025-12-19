package com.pnr.tv.db.dao

import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import app.cash.turbine.test
import com.pnr.tv.db.AppDatabase
import com.pnr.tv.testdata.TestDataFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * LiveStreamDao için unit testler.
 * In-memory database kullanarak CRUD operasyonlarını test eder.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LiveStreamDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var liveStreamDao: LiveStreamDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        liveStreamDao = database.liveStreamDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun `insertAll should insert live streams successfully`() = runTest {
        // Given
        val streams = TestDataFactory.createLiveStreamEntities(3)

        // When
        liveStreamDao.insertAll(streams)

        // Then
        liveStreamDao.getAll().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getByCategoryId should return streams for specific category`() = runTest {
        // Given
        val stream1 = TestDataFactory.createLiveStreamEntity(streamId = 1, categoryId = 1)
        val stream2 = TestDataFactory.createLiveStreamEntity(streamId = 2, categoryId = 1)
        val stream3 = TestDataFactory.createLiveStreamEntity(streamId = 3, categoryId = 2)
        liveStreamDao.insertAll(listOf(stream1, stream2, stream3))

        // When & Then
        liveStreamDao.getByCategoryId(1).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.all { it.categoryId == 1 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getByCategoryId should return empty list for non-existent category`() = runTest {
        // Given
        val stream = TestDataFactory.createLiveStreamEntity(streamId = 1, categoryId = 1)
        liveStreamDao.insertAll(listOf(stream))

        // When & Then
        liveStreamDao.getByCategoryId(999).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getByIds should return streams with specified IDs`() = runTest {
        // Given
        val streams = TestDataFactory.createLiveStreamEntities(5)
        liveStreamDao.insertAll(streams)

        // When
        val result = liveStreamDao.getByIds(listOf(1, 3, 5))

        // Then
        assertEquals(3, result.size)
        assertTrue(result.map { it.streamId }.containsAll(listOf(1, 3, 5)))
    }

    @Test
    fun `clearAll should remove all streams`() = runTest {
        // Given
        val streams = TestDataFactory.createLiveStreamEntities(5)
        liveStreamDao.insertAll(streams)

        // When
        liveStreamDao.clearAll()

        // Then
        liveStreamDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteStreams should remove specified streams`() = runTest {
        // Given
        val streams = TestDataFactory.createLiveStreamEntities(5)
        liveStreamDao.insertAll(streams)

        // When
        liveStreamDao.deleteStreams(listOf(streams[0], streams[1]))

        // Then
        liveStreamDao.getAll().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertTrue(!result.map { it.streamId }.contains(1))
            assertTrue(!result.map { it.streamId }.contains(2))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replaceAll should clear and insert new streams`() = runTest {
        // Given
        val streams1 = TestDataFactory.createLiveStreamEntities(3)
        liveStreamDao.insertAll(streams1)

        val streams2 = TestDataFactory.createLiveStreamEntities(2).mapIndexed { index, stream ->
            stream.copy(streamId = index + 10)
        }

        // When
        liveStreamDao.replaceAll(streams2)

        // Then
        liveStreamDao.getAll().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.all { it.streamId >= 10 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert should add new streams and update existing ones`() = runTest {
        // Given
        val existingStreams = TestDataFactory.createLiveStreamEntities(3)
        liveStreamDao.insertAll(existingStreams)

        val newStreams = listOf(
            TestDataFactory.createLiveStreamEntity(streamId = 1, name = "Updated Channel 1"),
            TestDataFactory.createLiveStreamEntity(streamId = 4, name = "New Channel 4"),
            TestDataFactory.createLiveStreamEntity(streamId = 5, name = "New Channel 5"),
        )

        // When
        liveStreamDao.upsert(newStreams)

        // Then
        val result = liveStreamDao.getAll().first()
        assertEquals(3, result.size) // Should have 3 streams (1 updated, 2 new, 2 deleted)
        assertTrue(result.any { it.streamId == 1 && it.name == "Updated Channel 1" })
        assertTrue(result.any { it.streamId == 4 })
        assertTrue(result.any { it.streamId == 5 })
    }

    @Test
    fun `upsert should delete streams not in new list`() = runTest {
        // Given
        val existingStreams = TestDataFactory.createLiveStreamEntities(5)
        liveStreamDao.insertAll(existingStreams)

        val newStreams = listOf(
            TestDataFactory.createLiveStreamEntity(streamId = 1, name = "Kept Channel"),
        )

        // When
        liveStreamDao.upsert(newStreams)

        // Then
        val result = liveStreamDao.getAll().first()
        assertEquals(1, result.size)
        assertEquals(1, result[0].streamId)
    }

    @Test
    fun `insertAll should replace existing streams with same streamId`() = runTest {
        // Given
        val stream1 = TestDataFactory.createLiveStreamEntity(streamId = 1, name = "Original Channel")
        val stream2 = TestDataFactory.createLiveStreamEntity(streamId = 1, name = "Updated Channel")

        // When
        liveStreamDao.insertAll(listOf(stream1))
        liveStreamDao.insertAll(listOf(stream2))

        // Then
        liveStreamDao.getAll().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Updated Channel", result[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getByCategoryId should return streams ordered by name ASC`() = runTest {
        // Given
        val stream1 = TestDataFactory.createLiveStreamEntity(streamId = 1, name = "Zebra Channel", categoryId = 1)
        val stream2 = TestDataFactory.createLiveStreamEntity(streamId = 2, name = "Alpha Channel", categoryId = 1)
        val stream3 = TestDataFactory.createLiveStreamEntity(streamId = 3, name = "Beta Channel", categoryId = 1)
        liveStreamDao.insertAll(listOf(stream1, stream2, stream3))

        // When & Then
        liveStreamDao.getByCategoryId(1).test {
            val result = awaitItem()
            assertEquals(3, result.size)
            // Should be ordered by name ASC
            assertEquals("Alpha Channel", result[0].name)
            assertEquals("Beta Channel", result[1].name)
            assertEquals("Zebra Channel", result[2].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getByCategoryId should return empty list when categoryId is null in database`() = runTest {
        // Given
        val stream1 = TestDataFactory.createLiveStreamEntity(streamId = 1, categoryId = null)
        val stream2 = TestDataFactory.createLiveStreamEntity(streamId = 2, categoryId = 1)
        liveStreamDao.insertAll(listOf(stream1, stream2))

        // When & Then - Querying a specific category should not return streams with null categoryId
        liveStreamDao.getByCategoryId(1).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(2, result[0].streamId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getByIds should return empty list for non-existent IDs`() = runTest {
        // Given
        val streams = TestDataFactory.createLiveStreamEntities(3)
        liveStreamDao.insertAll(streams)

        // When
        val result = liveStreamDao.getByIds(listOf(999, 1000))

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getByIds should return empty list when empty list provided`() = runTest {
        // Given
        val streams = TestDataFactory.createLiveStreamEntities(3)
        liveStreamDao.insertAll(streams)

        // When
        val result = liveStreamDao.getByIds(emptyList())

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getByIds should return only existing streams when partial IDs provided`() = runTest {
        // Given
        val streams = TestDataFactory.createLiveStreamEntities(5)
        liveStreamDao.insertAll(streams)

        // When
        val result = liveStreamDao.getByIds(listOf(1, 999, 3, 1000))

        // Then
        assertEquals(2, result.size)
        assertTrue(result.map { it.streamId }.containsAll(listOf(1, 3)))
        assertTrue(!result.map { it.streamId }.contains(999))
        assertTrue(!result.map { it.streamId }.contains(1000))
    }

    @Test
    fun `replaceAll should clear all when empty list provided`() = runTest {
        // Given
        val streams = TestDataFactory.createLiveStreamEntities(3)
        liveStreamDao.insertAll(streams)

        // When
        liveStreamDao.replaceAll(emptyList())

        // Then
        liveStreamDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAll should emit empty list initially`() = runTest {
        // When & Then
        liveStreamDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertAll should handle empty list`() = runTest {
        // When
        liveStreamDao.insertAll(emptyList())

        // Then
        liveStreamDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteStreams should handle empty list`() = runTest {
        // Given
        val streams = TestDataFactory.createLiveStreamEntities(3)
        liveStreamDao.insertAll(streams)

        // When
        liveStreamDao.deleteStreams(emptyList())

        // Then
        liveStreamDao.getAll().test {
            val result = awaitItem()
            assertEquals(3, result.size) // Should remain unchanged
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert should handle empty list`() = runTest {
        // Given
        val existingStreams = TestDataFactory.createLiveStreamEntities(3)
        liveStreamDao.insertAll(existingStreams)

        // When
        liveStreamDao.upsert(emptyList())

        // Then
        val result = liveStreamDao.getAll().first()
        assertTrue(result.isEmpty()) // All should be deleted
    }

    @Test
    fun `upsert should handle when no existing streams`() = runTest {
        // Given - No existing streams
        val newStreams = TestDataFactory.createLiveStreamEntities(3)

        // When
        liveStreamDao.upsert(newStreams)

        // Then
        val result = liveStreamDao.getAll().first()
        assertEquals(3, result.size)
        assertTrue(result.map { it.streamId }.containsAll(listOf(1, 2, 3)))
    }

    @Test
    fun `upsert should not delete anything when all streams are kept`() = runTest {
        // Given
        val existingStreams = TestDataFactory.createLiveStreamEntities(3)
        liveStreamDao.insertAll(existingStreams)

        val newStreams = TestDataFactory.createLiveStreamEntities(3).map { stream ->
            stream.copy(name = "Updated ${stream.name}")
        }

        // When
        liveStreamDao.upsert(newStreams)

        // Then
        val result = liveStreamDao.getAll().first()
        assertEquals(3, result.size)
        assertTrue(result.all { it.name?.startsWith("Updated") == true })
    }
}

