package com.pnr.tv.db.dao

import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import app.cash.turbine.test
import com.pnr.tv.db.AppDatabase
import com.pnr.tv.db.entity.LiveStreamCategoryEntity
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
 * LiveStreamCategoryDao için unit testler.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LiveStreamCategoryDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var categoryDao: LiveStreamCategoryDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        categoryDao = database.liveStreamCategoryDao()
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
            LiveStreamCategoryEntity(categoryIdInt = 1, categoryName = "Sports", sortOrder = 1),
            LiveStreamCategoryEntity(categoryIdInt = 2, categoryName = "News", sortOrder = 2),
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
    fun `getById should return category for existing ID`() = runTest {
        // Given
        val category = LiveStreamCategoryEntity(categoryIdInt = 1, categoryName = "Sports", sortOrder = 1)
        categoryDao.insertAll(listOf(category))

        // When
        val result = categoryDao.getById(1)

        // Then
        assertNotNull(result)
        assertEquals("Sports", result?.categoryName)
    }

    @Test
    fun `getById should return null for non-existent ID`() = runTest {
        // When
        val result = categoryDao.getById(999)

        // Then
        assertNull(result)
    }

    @Test
    fun `getAll should return categories ordered by sortOrder ASC`() = runTest {
        // Given
        val categories = listOf(
            LiveStreamCategoryEntity(categoryIdInt = 3, categoryName = "Movies", sortOrder = 3),
            LiveStreamCategoryEntity(categoryIdInt = 1, categoryName = "Sports", sortOrder = 1),
            LiveStreamCategoryEntity(categoryIdInt = 2, categoryName = "News", sortOrder = 2),
        )
        categoryDao.insertAll(categories)

        // When & Then
        categoryDao.getAll().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertEquals(1, result[0].categoryIdInt)
            assertEquals(2, result[1].categoryIdInt)
            assertEquals(3, result[2].categoryIdInt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearAll should remove all categories`() = runTest {
        // Given
        val categories = listOf(
            LiveStreamCategoryEntity(categoryIdInt = 1, categoryName = "Sports", sortOrder = 1),
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
            LiveStreamCategoryEntity(categoryIdInt = 1, categoryName = "Sports", sortOrder = 1),
        )
        categoryDao.insertAll(categories1)

        val categories2 = listOf(
            LiveStreamCategoryEntity(categoryIdInt = 2, categoryName = "News", sortOrder = 1),
        )

        // When
        categoryDao.replaceAll(categories2)

        // Then
        categoryDao.getAll().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(2, result[0].categoryIdInt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replaceAll should clear all when empty list provided`() = runTest {
        // Given
        val categories = listOf(
            LiveStreamCategoryEntity(categoryIdInt = 1, categoryName = "Sports", sortOrder = 1),
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
    fun `insertAll should replace existing categories with same categoryId`() = runTest {
        // Given
        val category1 = LiveStreamCategoryEntity(categoryIdInt = 1, categoryName = "Original", sortOrder = 1)
        val category2 = LiveStreamCategoryEntity(categoryIdInt = 1, categoryName = "Updated", sortOrder = 1)

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
    fun `getAll should maintain sortOrder when categories have same sortOrder`() = runTest {
        // Given
        val categories = listOf(
            LiveStreamCategoryEntity(categoryIdInt = 1, categoryName = "Sports", sortOrder = 1),
            LiveStreamCategoryEntity(categoryIdInt = 2, categoryName = "News", sortOrder = 1),
            LiveStreamCategoryEntity(categoryIdInt = 3, categoryName = "Movies", sortOrder = 2),
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
            LiveStreamCategoryEntity(categoryIdInt = 1, categoryName = null, sortOrder = 1),
            LiveStreamCategoryEntity(categoryIdInt = 2, categoryName = "News", sortOrder = 2),
        )

        // When
        categoryDao.insertAll(categories)

        // Then
        categoryDao.getAll().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.any { it.categoryIdInt == 1 && it.categoryName == null })
            assertTrue(result.any { it.categoryIdInt == 2 && it.categoryName == "News" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getById should return category with correct categoryId conversion`() = runTest {
        // Given
        val category = LiveStreamCategoryEntity(categoryIdInt = 123, categoryName = "Sports", sortOrder = 1)
        categoryDao.insertAll(listOf(category))

        // When
        val result = categoryDao.getById(123)

        // Then
        assertNotNull(result)
        assertEquals(123, result?.categoryIdInt)
        assertEquals("123", result?.categoryId) // String conversion
        assertEquals("Sports", result?.categoryName)
    }
}

