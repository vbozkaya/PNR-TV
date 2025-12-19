package com.pnr.tv.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * NetworkUtils için unit testler.
 * Network connectivity kontrolünü test eder.
 * 
 * Not: Mockito ile ConnectivityManager mock'lanıyor çünkü Robolectric 4.13'te
 * ShadowConnectivityManager API'si karmaşık. Mock yaklaşımı daha basit ve güvenilir.
 */
@RunWith(RobolectricTestRunner::class)
class NetworkUtilsTest {
    private lateinit var mockContext: Context
    private lateinit var mockConnectivityManager: ConnectivityManager

    @Before
    fun setup() {
        mockContext = mock()
        mockConnectivityManager = mock()
        whenever(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .thenReturn(mockConnectivityManager)
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    // ==================== isNetworkAvailable Tests (Android M+) ====================

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isNetworkAvailable should return true when WiFi is connected`() {
        // Given
        val mockNetwork = mock<Network>()
        val mockCapabilities = mock<NetworkCapabilities>()
        
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(mockCapabilities)
        whenever(mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(true)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            .thenReturn(true)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            .thenReturn(false)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            .thenReturn(false)

        // When
        val result = NetworkUtils.isNetworkAvailable(mockContext)

        // Then
        assertTrue(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isNetworkAvailable should return true when Ethernet is connected`() {
        // Given
        val mockNetwork = mock<Network>()
        val mockCapabilities = mock<NetworkCapabilities>()
        
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(mockCapabilities)
        whenever(mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(true)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            .thenReturn(false)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            .thenReturn(true)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            .thenReturn(false)

        // When
        val result = NetworkUtils.isNetworkAvailable(mockContext)

        // Then
        assertTrue(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isNetworkAvailable should return true when Cellular is connected`() {
        // Given
        val mockNetwork = mock<Network>()
        val mockCapabilities = mock<NetworkCapabilities>()
        
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(mockCapabilities)
        whenever(mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(true)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            .thenReturn(false)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            .thenReturn(false)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            .thenReturn(true)

        // When
        val result = NetworkUtils.isNetworkAvailable(mockContext)

        // Then
        assertTrue(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isNetworkAvailable should return false when no network`() {
        // Given
        whenever(mockConnectivityManager.activeNetwork).thenReturn(null)

        // When
        val result = NetworkUtils.isNetworkAvailable(mockContext)

        // Then
        assertFalse(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isNetworkAvailable should return false when network capabilities is null`() {
        // Given
        val mockNetwork = mock<Network>()
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(null)

        // When
        val result = NetworkUtils.isNetworkAvailable(mockContext)

        // Then
        assertFalse(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isNetworkAvailable should return false when no internet capability`() {
        // Given
        val mockNetwork = mock<Network>()
        val mockCapabilities = mock<NetworkCapabilities>()
        
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(mockCapabilities)
        whenever(mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(false)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            .thenReturn(true)

        // When
        val result = NetworkUtils.isNetworkAvailable(mockContext)

        // Then
        assertFalse(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isNetworkAvailable should return false when no transport available`() {
        // Given
        val mockNetwork = mock<Network>()
        val mockCapabilities = mock<NetworkCapabilities>()
        
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(mockCapabilities)
        whenever(mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(true)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            .thenReturn(false)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            .thenReturn(false)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            .thenReturn(false)

        // When
        val result = NetworkUtils.isNetworkAvailable(mockContext)

        // Then
        assertFalse(result)
    }

    // ==================== isNetworkAvailable Tests (Android M öncesi) ====================

    @Test
    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    fun `isNetworkAvailable should return true when connected on pre-M Android`() {
        // Given
        val mockNetworkInfo = mock<android.net.NetworkInfo>()
        @Suppress("DEPRECATION")
        whenever(mockConnectivityManager.activeNetworkInfo).thenReturn(mockNetworkInfo)
        whenever(mockNetworkInfo.isConnected).thenReturn(true)

        // When
        val result = NetworkUtils.isNetworkAvailable(mockContext)

        // Then
        assertTrue(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    fun `isNetworkAvailable should return false when not connected on pre-M Android`() {
        // Given
        val mockNetworkInfo = mock<android.net.NetworkInfo>()
        @Suppress("DEPRECATION")
        whenever(mockConnectivityManager.activeNetworkInfo).thenReturn(mockNetworkInfo)
        whenever(mockNetworkInfo.isConnected).thenReturn(false)

        // When
        val result = NetworkUtils.isNetworkAvailable(mockContext)

        // Then
        assertFalse(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    fun `isNetworkAvailable should return false when networkInfo is null on pre-M Android`() {
        // Given
        @Suppress("DEPRECATION")
        whenever(mockConnectivityManager.activeNetworkInfo).thenReturn(null)

        // When
        val result = NetworkUtils.isNetworkAvailable(mockContext)

        // Then
        assertFalse(result)
    }

    // ==================== logNetworkStatus Tests ====================

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `logNetworkStatus should log available status when network is available`() {
        // Given
        val mockNetwork = mock<Network>()
        val mockCapabilities = mock<NetworkCapabilities>()
        
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(mockCapabilities)
        whenever(mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(true)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            .thenReturn(true)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            .thenReturn(false)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            .thenReturn(false)

        // When
        NetworkUtils.logNetworkStatus(mockContext)

        // Then - Method should not throw exception
        // Logging is tested indirectly through isNetworkAvailable
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `logNetworkStatus should log unavailable status when network is not available`() {
        // Given
        whenever(mockConnectivityManager.activeNetwork).thenReturn(null)

        // When
        NetworkUtils.logNetworkStatus(mockContext)

        // Then - Method should not throw exception
        // Logging is tested indirectly through isNetworkAvailable
    }

    // ==================== isOnline Tests ====================

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isOnline should return true when network is available`() {
        // Given
        val mockNetwork = mock<Network>()
        val mockCapabilities = mock<NetworkCapabilities>()
        
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(mockCapabilities)
        whenever(mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(true)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            .thenReturn(true)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            .thenReturn(false)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            .thenReturn(false)

        // When
        val result = NetworkUtils.isOnline(mockContext)

        // Then
        assertTrue(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isOnline should return false when network is not available`() {
        // Given
        whenever(mockConnectivityManager.activeNetwork).thenReturn(null)

        // When
        val result = NetworkUtils.isOnline(mockContext)

        // Then
        assertFalse(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isOnline should be alias for isNetworkAvailable`() {
        // Given
        val mockNetwork = mock<Network>()
        val mockCapabilities = mock<NetworkCapabilities>()
        
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(mockCapabilities)
        whenever(mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(true)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            .thenReturn(true)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            .thenReturn(false)
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            .thenReturn(false)

        // When
        val isAvailableResult = NetworkUtils.isNetworkAvailable(mockContext)
        val isOnlineResult = NetworkUtils.isOnline(mockContext)

        // Then
        assertEquals(isAvailableResult, isOnlineResult)
        assertTrue(isAvailableResult)
        assertTrue(isOnlineResult)
    }
}
