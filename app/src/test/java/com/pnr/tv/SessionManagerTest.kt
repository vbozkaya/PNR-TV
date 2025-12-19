package com.pnr.tv

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * SessionManager için unit testler.
 * 
 * Not: SessionManager private DataStore extension kullandığı için,
 * tam test coverage için DataStore'u inject edilebilir hale getirmek gerekir.
 * Şimdilik, bu testler DataStore bağımlılığı nedeniyle @Ignore yapılmıştır.
 * Gerçek test için Robolectric veya test DataStore kullanılmalıdır.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var mockContext: android.content.Context
    private lateinit var sessionManager: SessionManager

    @Before
    fun setup() {
        mockContext = mock()
        // SessionManager constructor'ı Context alıyor ve private DataStore oluşturuyor
        // Test için gerçek bir test DataStore kullanmak daha iyi olur
        // Şimdilik mock context ile test ediyoruz
        sessionManager = SessionManager(mockContext)
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    @Test
    @Ignore("DataStore requires real Context - needs Robolectric or test DataStore")
    fun `saveCurrentUser should save userId successfully`() = runTest {
        // Given
        val userId = 123

        // When
        sessionManager.saveCurrentUser(userId)
        advanceUntilIdle()

        // Then
        // DataStore'a kaydedildiğini doğrulamak için getCurrentUserId'i kontrol ediyoruz
        val savedUserId = sessionManager.getCurrentUserId().first()
        assertEquals(userId, savedUserId)
    }

    @Test
    @Ignore("DataStore requires real Context - needs Robolectric or test DataStore")
    fun `getCurrentUserId should return null when no user is saved`() = runTest {
        // When
        val userId = sessionManager.getCurrentUserId().first()

        // Then
        assertNull(userId)
    }

    @Test
    @Ignore("DataStore requires real Context - needs Robolectric or test DataStore")
    fun `getCurrentUserId should return Flow of userId when user is saved`() = runTest {
        // Given
        val userId = 456
        sessionManager.saveCurrentUser(userId)
        advanceUntilIdle()

        // When
        sessionManager.getCurrentUserId().test {
            val result = awaitItem()
            
            // Then
            assertEquals(userId, result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Ignore("DataStore requires real Context - needs Robolectric or test DataStore")
    fun `clearCurrentUser should remove userId from DataStore`() = runTest {
        // Given
        val userId = 789
        sessionManager.saveCurrentUser(userId)
        advanceUntilIdle()
        
        // Verify user is saved
        assertEquals(userId, sessionManager.getCurrentUserId().first())

        // When
        sessionManager.clearCurrentUser()
        advanceUntilIdle()

        // Then
        val clearedUserId = sessionManager.getCurrentUserId().first()
        assertNull(clearedUserId)
    }

    @Test
    @Ignore("DataStore requires real Context - needs Robolectric or test DataStore")
    fun `saveCurrentUser should update existing userId`() = runTest {
        // Given
        val firstUserId = 111
        val secondUserId = 222
        
        sessionManager.saveCurrentUser(firstUserId)
        advanceUntilIdle()
        assertEquals(firstUserId, sessionManager.getCurrentUserId().first())

        // When
        sessionManager.saveCurrentUser(secondUserId)
        advanceUntilIdle()

        // Then
        assertEquals(secondUserId, sessionManager.getCurrentUserId().first())
    }

    @Test
    @Ignore("DataStore requires real Context - needs Robolectric or test DataStore")
    fun `getCurrentUserId should emit updated value when user changes`() = runTest {
        // Given
        val firstUserId = 333
        val secondUserId = 444

        // When & Then
        sessionManager.getCurrentUserId().test {
            // Initially null
            assertNull(awaitItem())
            
            // Save first user
            sessionManager.saveCurrentUser(firstUserId)
            advanceUntilIdle()
            assertEquals(firstUserId, awaitItem())
            
            // Update to second user
            sessionManager.saveCurrentUser(secondUserId)
            advanceUntilIdle()
            assertEquals(secondUserId, awaitItem())
            
            // Clear user
            sessionManager.clearCurrentUser()
            advanceUntilIdle()
            assertNull(awaitItem())
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}

