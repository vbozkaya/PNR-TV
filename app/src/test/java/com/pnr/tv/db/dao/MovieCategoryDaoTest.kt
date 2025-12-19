package com.pnr.tv.db.dao

import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import app.cash.turbine.test
import com.pnr.tv.db.AppDatabase
import com.pnr.tv.db.entity.MovieCategoryEntity
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
 * MovieCategoryDao için unit testler.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MovieCategoryDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var categoryDao: MovieCategoryDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        categoryDao = database.movieCategoryDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun `getAll should return empty list initially`() = runTest {
        categoryDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertAll should insert categories successfully`() = runTest {
        // Given
        val categories = listOf(
            MovieCategoryEntity(categoryId = "1", categoryName = "Action", parentId = 0, sortOrder = 1),
            MovieCategoryEntity(categoryId = "2", categoryName = "Comedy", parentId = 0, sortOrder = 2),
        )

        // When
        categoryDao.insertAll(categories)

        // Then
        categoryDao.getAll().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAll should return categories ordered by sortOrder ASC`() = runTest {
        // Given
        val categories = listOf(
            MovieCategoryEntity(categoryId = "3", categoryName = "Drama", parentId = 0, sortOrder = 3),
            MovieCategoryEntity(categoryId = "1", categoryName = "Action", parentId = 0, sortOrder = 1),
            MovieCategoryEntity(categoryId = "2", categoryName = "Comedy", parentId = 0, sortOrder = 2),
        )
        categoryDao.insertAll(categories)

        // When & Then
        categoryDao.getAll().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertEquals("1", result[0].categoryId)
            assertEquals("2", result[1].categoryId)
            assertEquals("3", result[2].categoryId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertAll should replace existing categories with same categoryId`() = runTest {
        // Given
        val category1 = MovieCategoryEntity(categoryId = "1", categoryName = "Original", parentId = 0, sortOrder = 1)
        val category2 = MovieCategoryEntity(categoryId = "1", categoryName = "Updated", parentId = 0, sortOrder = 1)

        // When
        categoryDao.insertAll(listOf(category1))
        categoryDao.insertAll(listOf(category2))

        // Then
        categoryDao.getAll().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Updated", result[0].categoryName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearAll should remove all categories`() = runTest {
        // Given
        val categories = listOf(
            MovieCategoryEntity(categoryId = "1", categoryName = "Action", parentId = 0, sortOrder = 1),
            MovieCategoryEntity(categoryId = "2", categoryName = "Comedy", parentId = 0, sortOrder = 2),
        )
        categoryDao.insertAll(categories)

        // When
        categoryDao.clearAll()

        // Then
        categoryDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replaceAll should clear and insert new categories`() = runTest {
        // Given
        val categories1 = listOf(
            MovieCategoryEntity(categoryId = "1", categoryName = "Action", parentId = 0, sortOrder = 1),
            MovieCategoryEntity(categoryId = "2", categoryName = "Comedy", parentId = 0, sortOrder = 2),
        )
        categoryDao.insertAll(categories1)

        val categories2 = listOf(
            MovieCategoryEntity(categoryId = "3", categoryName = "Drama", parentId = 0, sortOrder = 1),
        )

        // When
        categoryDao.replaceAll(categories2)

        // Then
        categoryDao.getAll().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("3", result[0].categoryId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertAll should handle empty list`() = runTest {
        // When
        categoryDao.insertAll(emptyList())

        // Then
        categoryDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replaceAll should clear all when empty list provided`() = runTest {
        // Given
        val categories = listOf(
            MovieCategoryEntity(categoryId = "1", categoryName = "Action", parentId = 0, sortOrder = 1),
            MovieCategoryEntity(categoryId = "2", categoryName = "Comedy", parentId = 0, sortOrder = 2),
        )
        categoryDao.insertAll(categories)

        // When
        categoryDao.replaceAll(emptyList())

        // Then
        categoryDao.getAll().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAll should maintain sortOrder when categories have same sortOrder`() = runTest {
        // Given
        val categories = listOf(
            MovieCategoryEntity(categoryId = "1", categoryName = "Action", parentId = 0, sortOrder = 1),
            MovieCategoryEntity(categoryId = "2", categoryName = "Comedy", parentId = 0, sortOrder = 1),
            MovieCategoryEntity(categoryId = "3", categoryName = "Drama", parentId = 0, sortOrder = 2),
        )
        categoryDao.insertAll(categories)

        // When & Then
        categoryDao.getAll().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            // Should be ordered by sortOrder ASC
            assertTrue(result[0].sortOrder <= result[1].sortOrder)
            assertTrue(result[1].sortOrder <= result[2].sortOrder)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertAll should handle categories with null categoryName`() = runTest {
        // Given
        val categories = listOf(
            MovieCategoryEntity(categoryId = "1", categoryName = null, parentId = 0, sortOrder = 1),
            MovieCategoryEntity(categoryId = "2", categoryName = "Comedy", parentId = 0, sortOrder = 2),
        )

        // When
        categoryDao.insertAll(categories)

        // Then
        categoryDao.getAll().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.any { it.categoryId == "1" && it.categoryName == null })
            assertTrue(result.any { it.categoryId == "2" && it.categoryName == "Comedy" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertAll should handle categories with different parentId values`() = runTest {
        // Given
        val categories = listOf(
            MovieCategoryEntity(categoryId = "1", categoryName = "Action", parentId = 0, sortOrder = 1),
            MovieCategoryEntity(categoryId = "2", categoryName = "Sub-Action", parentId = 1, sortOrder = 2),
            MovieCategoryEntity(categoryId = "3", categoryName = "Comedy", parentId = 0, sortOrder = 3),
        )

        // When
        categoryDao.insertAll(categories)

        // Then
        categoryDao.getAll().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertTrue(result.any { it.parentId == 0 })
            assertTrue(result.any { it.parentId == 1 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replaceAll should handle large number of categories`() = runTest {
        // Given
        val categories1 = (1..5).map {
            MovieCategoryEntity(categoryId = it.toString(), categoryName = "Category $it", parentId = 0, sortOrder = it)
        }
        categoryDao.insertAll(categories1)

        val categories2 = (6..10).map {
            MovieCategoryEntity(categoryId = it.toString(), categoryName = "Category $it", parentId = 0, sortOrder = it - 5)
        }

        // When
        categoryDao.replaceAll(categories2)

        // Then
        categoryDao.getAll().test {
            val result = awaitItem()
            assertEquals(5, result.size)
            assertTrue(result.all { it.categoryId.toInt() in 6..10 })
            cancelAndIgnoreRemainingEvents()
        }
    }
}

