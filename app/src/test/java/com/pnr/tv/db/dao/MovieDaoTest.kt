package com.pnr.tv.db.dao

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
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException

/**
 * MovieDao için unit testler.
 * In-memory database kullanarak CRUD operasyonlarını test eder.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MovieDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var movieDao: MovieDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        movieDao = database.movieDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun `insertAll should insert movies successfully`() = runTest {
        // Given
        val movies = TestDataFactory.createMovieEntities(3)

        // When
        movieDao.insertAll(movies)

        // Then
        movieDao.getAll().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertAll should replace existing movies with same streamId`() = runTest {
        // Given
        val movie1 = TestDataFactory.createMovieEntity(streamId = 1, name = "Original Movie")
        val movie2 = TestDataFactory.createMovieEntity(streamId = 1, name = "Updated Movie")

        // When
        movieDao.insertAll(listOf(movie1))
        movieDao.insertAll(listOf(movie2))

        // Then
        movieDao.getAll().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Updated Movie", result[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAll should return all movies ordered by added DESC`() = runTest {
        // Given
        val movie1 = TestDataFactory.createMovieEntity(streamId = 1, added = "2024-01-01")
        val movie2 = TestDataFactory.createMovieEntity(streamId = 2, added = "2024-01-02")
        val movie3 = TestDataFactory.createMovieEntity(streamId = 3, added = "2024-01-03")
        movieDao.insertAll(listOf(movie1, movie2, movie3))

        // When & Then
        movieDao.getAll().test {
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
    fun `getByCategoryId should return movies for specific category`() = runTest {
        // Given
        val movie1 = TestDataFactory.createMovieEntity(streamId = 1, categoryId = "1")
        val movie2 = TestDataFactory.createMovieEntity(streamId = 2, categoryId = "1")
        val movie3 = TestDataFactory.createMovieEntity(streamId = 3, categoryId = "2")
        movieDao.insertAll(listOf(movie1, movie2, movie3))

        // When & Then
        movieDao.getByCategoryId("1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.all { it.categoryId == "1" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getByCategoryId should return empty list for non-existent category`() = runTest {
        // Given
        val movie = TestDataFactory.createMovieEntity(streamId = 1, categoryId = "1")
        movieDao.insertAll(listOf(movie))

        // When & Then
        movieDao.getByCategoryId("999").test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRecentlyAdded should return limited number of movies`() = runTest {
        // Given
        val movies = TestDataFactory.createMovieEntities(10)
        movieDao.insertAll(movies)

        // When
        val result = movieDao.getRecentlyAdded(5)

        // Then
        assertEquals(5, result.size)
    }

    @Test
    fun `getRecentlyAdded should return all movies if limit is greater than count`() = runTest {
        // Given
        val movies = TestDataFactory.createMovieEntities(3)
        movieDao.insertAll(movies)

        // When
        val result = movieDao.getRecentlyAdded(10)

        // Then
        assertEquals(3, result.size)
    }

    @Test
    fun `getByIds should return movies with specified IDs`() = runTest {
        // Given
        val movies = TestDataFactory.createMovieEntities(5)
        movieDao.insertAll(movies)

        // When
        val result = movieDao.getByIds(listOf(1, 3, 5))

        // Then
        assertEquals(3, result.size)
        assertTrue(result.map { it.streamId }.containsAll(listOf(1, 3, 5)))
    }

    @Test
    fun `getByIds should return empty list for non-existent IDs`() = runTest {
        // Given
        val movies = TestDataFactory.createMovieEntities(3)
        movieDao.insertAll(movies)

        // When
        val result = movieDao.getByIds(listOf(999, 1000))

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMovieIdsWithTmdb should return only movies with tmdbId`() = runTest {
        // Given
        val movie1 = TestDataFactory.createMovieEntity(streamId = 1, tmdbId = 12345)
        val movie2 = TestDataFactory.createMovieEntity(streamId = 2, tmdbId = null)
        val movie3 = TestDataFactory.createMovieEntity(streamId = 3, tmdbId = 67890)
        movieDao.insertAll(listOf(movie1, movie2, movie3))

        // When
        val result = movieDao.getMovieIdsWithTmdb()

        // Then
        assertEquals(2, result.size)
        assertTrue(result.containsAll(listOf(1, 3)))
        assertTrue(!result.contains(2))
    }

    @Test
    fun `getMovieIdsWithTmdb should return empty list when no movies have tmdbId`() = runTest {
        // Given
        val movie1 = TestDataFactory.createMovieEntity(streamId = 1, tmdbId = null)
        val movie2 = TestDataFactory.createMovieEntity(streamId = 2, tmdbId = null)
        movieDao.insertAll(listOf(movie1, movie2))

        // When
        val result = movieDao.getMovieIdsWithTmdb()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `clearAll should remove all movies`() = runTest {
        // Given
        val movies = TestDataFactory.createMovieEntities(5)
        movieDao.insertAll(movies)

        // When
        movieDao.clearAll()

        // Then
        movieDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replaceAll should clear and insert new movies`() = runTest {
        // Given
        val movies1 = TestDataFactory.createMovieEntities(3)
        movieDao.insertAll(movies1)

        val movies2 = TestDataFactory.createMovieEntities(2).mapIndexed { index, movie ->
            movie.copy(streamId = index + 10)
        }

        // When
        movieDao.replaceAll(movies2)

        // Then
        movieDao.getAll().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.all { it.streamId >= 10 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replaceAll should clear all when empty list provided`() = runTest {
        // Given
        val movies = TestDataFactory.createMovieEntities(3)
        movieDao.insertAll(movies)

        // When
        movieDao.replaceAll(emptyList())

        // Then
        movieDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAll should emit empty list initially`() = runTest {
        // When & Then
        movieDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getByCategoryId should return empty list when categoryId is null in database`() = runTest {
        // Given
        val movie1 = TestDataFactory.createMovieEntity(streamId = 1, categoryId = null)
        val movie2 = TestDataFactory.createMovieEntity(streamId = 2, categoryId = "1")
        movieDao.insertAll(listOf(movie1, movie2))

        // When & Then - Querying a specific category should not return movies with null categoryId
        movieDao.getByCategoryId("1").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(2, result[0].streamId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRecentlyAdded should return empty list when limit is 0`() = runTest {
        // Given
        val movies = TestDataFactory.createMovieEntities(5)
        movieDao.insertAll(movies)

        // When
        val result = movieDao.getRecentlyAdded(0)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getByIds should return empty list when empty list provided`() = runTest {
        // Given
        val movies = TestDataFactory.createMovieEntities(3)
        movieDao.insertAll(movies)

        // When
        val result = movieDao.getByIds(emptyList())

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getByIds should return only existing movies when partial IDs provided`() = runTest {
        // Given
        val movies = TestDataFactory.createMovieEntities(5)
        movieDao.insertAll(movies)

        // When
        val result = movieDao.getByIds(listOf(1, 999, 3, 1000))

        // Then
        assertEquals(2, result.size)
        assertTrue(result.map { it.streamId }.containsAll(listOf(1, 3)))
        assertTrue(!result.map { it.streamId }.contains(999))
        assertTrue(!result.map { it.streamId }.contains(1000))
    }

    @Test
    fun `insertAll should handle empty list`() = runTest {
        // When
        movieDao.insertAll(emptyList())

        // Then
        movieDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRecentlyAdded should return movies ordered by added DESC`() = runTest {
        // Given
        val movie1 = TestDataFactory.createMovieEntity(streamId = 1, added = "2024-01-01")
        val movie2 = TestDataFactory.createMovieEntity(streamId = 2, added = "2024-01-02")
        val movie3 = TestDataFactory.createMovieEntity(streamId = 3, added = "2024-01-03")
        movieDao.insertAll(listOf(movie1, movie2, movie3))

        // When
        val result = movieDao.getRecentlyAdded(2)

        // Then
        assertEquals(2, result.size)
        // Should be ordered by added DESC (newest first)
        assertEquals(3, result[0].streamId)
        assertEquals(2, result[1].streamId)
    }
}

