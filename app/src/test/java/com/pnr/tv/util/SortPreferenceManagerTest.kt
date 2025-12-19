package com.pnr.tv.util

import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.model.ContentType
import com.pnr.tv.model.SortOrder
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * SortPreferenceManager için unit testler.
 * Sıralama tercihlerinin doğru kaydedildiğini ve okunduğunu doğrular.
 * 
 * Not: DataStore bağımlılığı nedeniyle bu testler @Ignore yapılmıştır.
 * Gerçek test için Robolectric veya test DataStore kullanılmalıdır.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SortPreferenceManagerTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var mockContext: android.content.Context
    private lateinit var sortPreferenceManager: SortPreferenceManager

    @Before
    fun setup() {
        mockContext = mock()
        sortPreferenceManager = SortPreferenceManager(mockContext)
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    @Test
    @Ignore("DataStore requires real Context - needs Robolectric or test DataStore")
    fun `saveSortOrder should save sort order for MOVIES content type`() = runTest {
        // Given
        val sortOrder = SortOrder.A_TO_Z

        // When
        sortPreferenceManager.saveSortOrder(ContentType.MOVIES, sortOrder)
        advanceUntilIdle()

        // Then
        val savedSortOrder = sortPreferenceManager.getSortOrder(ContentType.MOVIES).first()
        assertEquals(sortOrder, savedSortOrder)
    }

    @Test
    @Ignore("DataStore requires real Context - needs Robolectric or test DataStore")
    fun `saveSortOrder should save sort order for SERIES content type`() = runTest {
        // Given
        val sortOrder = SortOrder.RATING_HIGH_TO_LOW

        // When
        sortPreferenceManager.saveSortOrder(ContentType.SERIES, sortOrder)
        advanceUntilIdle()

        // Then
        val savedSortOrder = sortPreferenceManager.getSortOrder(ContentType.SERIES).first()
        assertEquals(sortOrder, savedSortOrder)
    }

    @Test
    @Ignore("DataStore requires real Context - needs Robolectric or test DataStore")
    fun `saveSortOrder should save sort order for LIVE_TV content type`() = runTest {
        // Given
        val sortOrder = SortOrder.Z_TO_A

        // When
        sortPreferenceManager.saveSortOrder(ContentType.LIVE_TV, sortOrder)
        advanceUntilIdle()

        // Then
        val savedSortOrder = sortPreferenceManager.getSortOrder(ContentType.LIVE_TV).first()
        assertEquals(sortOrder, savedSortOrder)
    }

    @Test
    @Ignore("DataStore requires real Context - needs Robolectric or test DataStore")
    fun `getSortOrder should return null when no sort order is saved`() = runTest {
        // When
        val sortOrder = sortPreferenceManager.getSortOrder(ContentType.MOVIES).first()

        // Then
        assertNull(sortOrder)
    }

    @Test
    @Ignore("DataStore requires real Context - needs Robolectric or test DataStore")
    fun `saveSortOrder should update existing sort order`() = runTest {
        // Given
        val firstSortOrder = SortOrder.A_TO_Z
        val secondSortOrder = SortOrder.Z_TO_A

        sortPreferenceManager.saveSortOrder(ContentType.MOVIES, firstSortOrder)
        advanceUntilIdle()
        assertEquals(firstSortOrder, sortPreferenceManager.getSortOrder(ContentType.MOVIES).first())

        // When
        sortPreferenceManager.saveSortOrder(ContentType.MOVIES, secondSortOrder)
        advanceUntilIdle()

        // Then
        assertEquals(secondSortOrder, sortPreferenceManager.getSortOrder(ContentType.MOVIES).first())
    }

    @Test
    @Ignore("DataStore requires real Context - needs Robolectric or test DataStore")
    fun `saveSortOrder should save different sort orders for different content types`() = runTest {
        // Given
        val moviesSortOrder = SortOrder.A_TO_Z
        val seriesSortOrder = SortOrder.RATING_HIGH_TO_LOW
        val liveTvSortOrder = SortOrder.DATE_NEW_TO_OLD

        // When
        sortPreferenceManager.saveSortOrder(ContentType.MOVIES, moviesSortOrder)
        sortPreferenceManager.saveSortOrder(ContentType.SERIES, seriesSortOrder)
        sortPreferenceManager.saveSortOrder(ContentType.LIVE_TV, liveTvSortOrder)
        advanceUntilIdle()

        // Then
        assertEquals(moviesSortOrder, sortPreferenceManager.getSortOrder(ContentType.MOVIES).first())
        assertEquals(seriesSortOrder, sortPreferenceManager.getSortOrder(ContentType.SERIES).first())
        assertEquals(liveTvSortOrder, sortPreferenceManager.getSortOrder(ContentType.LIVE_TV).first())
    }

    @Test
    @Ignore("DataStore requires real Context - needs Robolectric or test DataStore")
    fun `getSortOrder should return all SortOrder enum values correctly`() = runTest {
        // Given
        val allSortOrders = listOf(
            SortOrder.A_TO_Z,
            SortOrder.Z_TO_A,
            SortOrder.RATING_HIGH_TO_LOW,
            SortOrder.RATING_LOW_TO_HIGH,
            SortOrder.DATE_NEW_TO_OLD,
            SortOrder.DATE_OLD_TO_NEW,
        )

        // When & Then
        allSortOrders.forEachIndexed { index, sortOrder ->
            sortPreferenceManager.saveSortOrder(ContentType.MOVIES, sortOrder)
            advanceUntilIdle()
            
            val savedSortOrder = sortPreferenceManager.getSortOrder(ContentType.MOVIES).first()
            assertEquals("SortOrder at index $index should be saved correctly", sortOrder, savedSortOrder)
        }
    }

    @Test
    @Ignore("DataStore requires real Context - needs Robolectric or test DataStore")
    fun `getSortOrder should emit updated value when sort order changes`() = runTest {
        // Given
        val firstSortOrder = SortOrder.A_TO_Z
        val secondSortOrder = SortOrder.RATING_HIGH_TO_LOW

        // When & Then
        sortPreferenceManager.getSortOrder(ContentType.MOVIES).test {
            // Initially null
            assertNull(awaitItem())
            
            // Save first sort order
            sortPreferenceManager.saveSortOrder(ContentType.MOVIES, firstSortOrder)
            advanceUntilIdle()
            assertEquals(firstSortOrder, awaitItem())
            
            // Update to second sort order
            sortPreferenceManager.saveSortOrder(ContentType.MOVIES, secondSortOrder)
            advanceUntilIdle()
            assertEquals(secondSortOrder, awaitItem())
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}

