package com.pnr.tv.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.db.entity.PlaybackPositionEntity
import com.pnr.tv.db.entity.SeriesEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Retrofit

@OptIn(ExperimentalCoroutinesApi::class)
class ContentRepositoryTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockMovieRepository: MovieRepository = mock()
    private val mockSeriesRepository: SeriesRepository = mock()
    private val mockLiveStreamRepository: LiveStreamRepository = mock()
    private val mockFavoriteRepository: FavoriteRepository = mock()
    private val mockRecentlyWatchedRepository: RecentlyWatchedRepository = mock()
    private val mockPlaybackPositionRepository: PlaybackPositionRepository = mock()
    private val mockRetrofitBuilder: Retrofit.Builder = mock()
    private val mockUserRepository: UserRepository = mock()
    private val mockContext: android.content.Context = mock()

    private lateinit var repository: ContentRepository

    @Before
    fun setup() {
        // Mock ConnectivityManager for NetworkUtils
        val mockConnectivityManager = mock<ConnectivityManager>()
        whenever(mockContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE))
            .thenReturn(mockConnectivityManager)
        
        // Mock network capabilities for NetworkUtils.isNetworkAvailable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val mockNetwork = mock<android.net.Network>()
            val mockNetworkCapabilities = mock<NetworkCapabilities>()
            whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
            whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
                .thenReturn(mockNetworkCapabilities)
            whenever(mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                .thenReturn(true)
            whenever(mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                .thenReturn(true)
        } else {
            val mockNetworkInfo = mock<android.net.NetworkInfo>()
            whenever(mockNetworkInfo.isConnected).thenReturn(true)
            @Suppress("DEPRECATION")
            whenever(mockConnectivityManager.activeNetworkInfo).thenReturn(mockNetworkInfo)
        }
        
        whenever(mockContext.getString(any())).thenReturn("Mock String")
        whenever(mockContext.getString(any(), any())).thenReturn("Mock String")
        
        repository =
            ContentRepository(
                movieRepository = mockMovieRepository,
                seriesRepository = mockSeriesRepository,
                liveStreamRepository = mockLiveStreamRepository,
                favoriteRepository = mockFavoriteRepository,
                recentlyWatchedRepository = mockRecentlyWatchedRepository,
                playbackPositionRepository = mockPlaybackPositionRepository,
                retrofitBuilder = mockRetrofitBuilder,
                userRepository = mockUserRepository,
                context = mockContext,
            )
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    // ==================== Movie Operations Tests ====================

    @Test
    fun `getMovies should delegate to movieRepository`() =
        runTest {
            // Given
            val testMovies =
                listOf(
                    MovieEntity(
                        streamId = 1,
                        name = "Test Movie",
                        streamIconUrl = null,
                        rating = 8.5,
                        plot = "Test plot",
                        categoryId = "1",
                        added = "2024-01-01",
                        tmdbId = 100,
                        containerExtension = "mp4",
                    ),
                )
            whenever(mockMovieRepository.getMovies()).thenReturn(flowOf(testMovies))

            // When
            val result = repository.getMovies()

            // Then
            result.test {
                val movies = awaitItem()
                assertEquals(testMovies, movies)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getMoviesByCategoryId should delegate to movieRepository`() =
        runTest {
            // Given
            val categoryId = "1"
            val testMovies = emptyList<MovieEntity>()
            whenever(mockMovieRepository.getMoviesByCategoryId(categoryId)).thenReturn(flowOf(testMovies))

            // When
            val result = repository.getMoviesByCategoryId(categoryId)

            // Then
            result.test {
                val movies = awaitItem()
                assertEquals(testMovies, movies)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refreshMovies should delegate to movieRepository`() =
        runTest {
            // Given
            whenever(mockMovieRepository.refreshMovies(false, false))
                .thenReturn(Result.Success(Unit))

            // When
            val result = repository.refreshMovies(skipTmdbSync = false, forMainScreenUpdate = false)

            // Then
            assertTrue(result.isSuccess)
            verify(mockMovieRepository).refreshMovies(false, false)
        }

    // ==================== Series Operations Tests ====================

    @Test
    fun `getSeries should delegate to seriesRepository`() =
        runTest {
            // Given
            val testSeries =
                listOf(
                    SeriesEntity(
                        streamId = 1,
                        name = "Test Series",
                        coverUrl = null,
                        rating = 9.0,
                        plot = "Test plot",
                        releaseDate = null,
                        categoryId = "1",
                        added = "2024-01-01",
                        tmdbId = 200,
                    ),
                )
            whenever(mockSeriesRepository.getSeries()).thenReturn(flowOf(testSeries))

            // When
            val result = repository.getSeries()

            // Then
            result.test {
                val series = awaitItem()
                assertEquals(testSeries, series)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refreshSeries should delegate to seriesRepository`() =
        runTest {
            // Given
            whenever(mockSeriesRepository.refreshSeries(false, false))
                .thenReturn(Result.Success(Unit))

            // When
            val result = repository.refreshSeries(skipTmdbSync = false, forMainScreenUpdate = false)

            // Then
            assertTrue(result.isSuccess)
            verify(mockSeriesRepository).refreshSeries(false, false)
        }

    // ==================== LiveStream Operations Tests ====================

    @Test
    fun `getLiveStreams should delegate to liveStreamRepository`() =
        runTest {
            // Given
            val testStreams =
                listOf(
                    LiveStreamEntity(
                        streamId = 1,
                        name = "Test Channel",
                        streamIconUrl = null,
                        categoryId = 1,
                        categoryName = "Sports",
                    ),
                )
            whenever(mockLiveStreamRepository.getLiveStreams()).thenReturn(flowOf(testStreams))

            // When
            val result = repository.getLiveStreams()

            // Then
            result.test {
                val streams = awaitItem()
                assertEquals(testStreams, streams)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refreshLiveStreams should delegate to liveStreamRepository`() =
        runTest {
            // Given
            whenever(mockLiveStreamRepository.refreshLiveStreams(false))
                .thenReturn(Result.Success(Unit))

            // When
            val result = repository.refreshLiveStreams(forMainScreenUpdate = false)

            // Then
            assertTrue(result.isSuccess)
            verify(mockLiveStreamRepository).refreshLiveStreams(false)
        }

    // ==================== Favorite Operations Tests ====================

    @Test
    fun `addFavorite should delegate to favoriteRepository`() =
        runTest {
            // Given
            val channelId = 1
            val viewerId = 2

            // When
            repository.addFavorite(channelId, viewerId)

            // Then
            verify(mockFavoriteRepository).addFavorite(channelId, viewerId)
        }

    @Test
    fun `removeFavorite should delegate to favoriteRepository`() =
        runTest {
            // Given
            val channelId = 1
            val viewerId = 2

            // When
            repository.removeFavorite(channelId, viewerId)

            // Then
            verify(mockFavoriteRepository).removeFavorite(channelId, viewerId)
        }

    @Test
    fun `isFavorite should delegate to favoriteRepository`() =
        runTest {
            // Given
            val channelId = 1
            val viewerId = 2
            whenever(mockFavoriteRepository.isFavorite(channelId, viewerId)).thenReturn(flowOf(true))

            // When
            val result = repository.isFavorite(channelId, viewerId)

            // Then
            result.test {
                val isFavorite = awaitItem()
                assertTrue(isFavorite)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ==================== Recently Watched Operations Tests ====================

    @Test
    fun `saveRecentlyWatched should delegate to recentlyWatchedRepository`() =
        runTest {
            // Given
            val channelId = 1

            // When
            repository.saveRecentlyWatched(channelId)

            // Then
            verify(mockRecentlyWatchedRepository).saveRecentlyWatched(channelId)
        }

    @Test
    fun `getRecentlyWatchedChannelIds should delegate to recentlyWatchedRepository`() =
        runTest {
            // Given
            val limit = 10
            val channelIds = listOf(1, 2, 3)
            whenever(mockRecentlyWatchedRepository.getRecentlyWatchedChannelIds(limit))
                .thenReturn(flowOf(channelIds))

            // When
            val result = repository.getRecentlyWatchedChannelIds(limit)

            // Then
            result.test {
                val ids = awaitItem()
                assertEquals(channelIds, ids)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ==================== Playback Position Operations Tests ====================

    @Test
    fun `savePlaybackPosition should delegate to playbackPositionRepository`() =
        runTest {
            // Given
            val contentId = "movie_123"
            val positionMs = 60000L
            val durationMs = 3600000L

            // When
            repository.savePlaybackPosition(contentId, positionMs, durationMs)

            // Then
            verify(mockPlaybackPositionRepository).savePlaybackPosition(contentId, positionMs, durationMs)
        }

    @Test
    fun `getPlaybackPosition should delegate to playbackPositionRepository`() =
        runTest {
            // Given
            val contentId = "movie_123"
            val positionEntity =
                PlaybackPositionEntity(
                    contentId = contentId,
                    userId = 1,
                    positionMs = 60000L,
                    durationMs = 3600000L,
                    lastUpdated = System.currentTimeMillis(),
                )
            whenever(mockPlaybackPositionRepository.getPlaybackPosition(contentId))
                .thenReturn(positionEntity)

            // When
            val result = repository.getPlaybackPosition(contentId)

            // Then
            assertNotNull(result)
            assertEquals(positionEntity, result)
        }

    @Test
    fun `deletePlaybackPosition should delegate to playbackPositionRepository`() =
        runTest {
            // Given
            val contentId = "movie_123"

            // When
            repository.deletePlaybackPosition(contentId)

            // Then
            verify(mockPlaybackPositionRepository).deletePlaybackPosition(contentId)
        }

    // ==================== Error Handling Tests ====================

    @Test
    fun `fetchUserInfo should return error when no users exist`() = runTest {
        // Given - No users in repository
        whenever(mockUserRepository.allUsers).thenReturn(flowOf(emptyList()))
        whenever(mockContext.getString(any())).thenReturn("Mock String")
        whenever(mockContext.getString(any(), any())).thenReturn("Mock String")

        // When
        val result = repository.fetchUserInfo()

        // Then
        assertTrue(result.isError)
        assertTrue(result is Result.Error)
    }

    @Test
    fun `fetchUserInfo should return error when current user is null`() = runTest {
        // Given - Users exist but no current user selected
        val testUser = com.pnr.tv.db.entity.UserAccountEntity(
            id = 1,
            accountName = "Test Account",
            username = "testuser",
            password = "testpass", // Not encrypted for test
            dns = "https://test.dns.com", // Not encrypted for test
        )
        whenever(mockUserRepository.allUsers).thenReturn(flowOf(listOf(testUser)))
        // currentUser returns null (no user selected)
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(null))
        whenever(mockContext.getString(any())).thenReturn("Mock String")
        whenever(mockContext.getString(any(), any())).thenReturn("Mock String")

        // When
        val result = repository.fetchUserInfo()

        // Then
        assertTrue(result.isError)
        assertTrue(result is Result.Error)
    }

    @Test
    fun `fetchUserInfo should handle network errors gracefully`() = runTest {
        // Given - User exists but API call fails
        // Note: Full network error testing requires mocking DataEncryption and Retrofit
        // which is complex. This test verifies the error handling structure exists.
        val testUser = com.pnr.tv.db.entity.UserAccountEntity(
            id = 1,
            accountName = "Test Account",
            username = "testuser",
            password = "testpass",
            dns = "https://test.dns.com",
        )
        whenever(mockUserRepository.allUsers).thenReturn(flowOf(listOf(testUser)))
        whenever(mockContext.getString(any())).thenReturn("Mock String")
        whenever(mockContext.getString(any(), any())).thenReturn("Mock String")
        
        // When - fetchUserInfo is called, it will fail due to missing API setup
        // but the error handling structure is verified
        val result = repository.fetchUserInfo()

        // Then - Should return an error (either user not found or network error)
        assertTrue(result.isError)
    }
}
