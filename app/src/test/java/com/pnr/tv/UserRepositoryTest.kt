package com.pnr.tv

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.pnr.tv.db.dao.UserDao
import com.pnr.tv.db.entity.UserAccountEntity
import com.pnr.tv.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoryTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockUserDao: UserDao = mock()
    private val mockSessionManager: SessionManager = mock()
    private val mockFavoriteDao: com.pnr.tv.db.dao.FavoriteDao = mock()
    private val mockRecentlyWatchedDao: com.pnr.tv.db.dao.RecentlyWatchedDao = mock()
    private val mockPlaybackPositionDao: com.pnr.tv.db.dao.PlaybackPositionDao = mock()
    private val mockWatchedEpisodeDao: com.pnr.tv.db.dao.WatchedEpisodeDao = mock()
    private val mockViewerDao: com.pnr.tv.db.dao.ViewerDao = mock()
    private val mockMovieDao: com.pnr.tv.db.dao.MovieDao = mock()
    private val mockSeriesDao: com.pnr.tv.db.dao.SeriesDao = mock()
    private val mockLiveStreamDao: com.pnr.tv.db.dao.LiveStreamDao = mock()
    private val mockMovieCategoryDao: com.pnr.tv.db.dao.MovieCategoryDao = mock()
    private val mockSeriesCategoryDao: com.pnr.tv.db.dao.SeriesCategoryDao = mock()
    private val mockLiveStreamCategoryDao: com.pnr.tv.db.dao.LiveStreamCategoryDao = mock()
    private val mockTmdbCacheDao: com.pnr.tv.db.dao.TmdbCacheDao = mock()

    @Before
    fun setup() {
        // Setup boş - her test kendi repository'sini oluşturacak
    }

    @After
    fun tearDown() {
        // MainCoroutineRule handles Dispatcher cleanup
    }

    @Test
    fun `allUsers should return flow from userDao getAllUsers`() =
        runTest {
            // Given
            val testUsers =
                listOf(
                    UserAccountEntity(
                        id = 1,
                        accountName = "Test Account 1",
                        username = "user1",
                        password = "pass1",
                        dns = "https://test1.dns.com",
                    ),
                    UserAccountEntity(
                        id = 2,
                        accountName = "Test Account 2",
                        username = "user2",
                        password = "pass2",
                        dns = "https://test2.dns.com",
                    ),
                )
            // Repository zaten oluşturuldu, yeni bir repository oluştur
            whenever(mockUserDao.getAllUsers()).thenReturn(flowOf(testUsers))
            val testRepository = UserRepository(
                mockUserDao,
                mockSessionManager,
                mockFavoriteDao,
                mockRecentlyWatchedDao,
                mockPlaybackPositionDao,
                mockWatchedEpisodeDao,
                mockViewerDao,
                mockMovieDao,
                mockSeriesDao,
                mockLiveStreamDao,
                mockMovieCategoryDao,
                mockSeriesCategoryDao,
                mockLiveStreamCategoryDao,
                mockTmdbCacheDao
            )

            // When
            advanceUntilIdle()
            val result = testRepository.allUsers.first()

            // Then
            assert(result == testUsers)
            verify(mockUserDao).getAllUsers()
        }

    @Test
    fun `currentUser should return null when sessionManager returns null userId`() =
        runTest {
            // Given
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))
            val testRepository = UserRepository(
                mockUserDao,
                mockSessionManager,
                mockFavoriteDao,
                mockRecentlyWatchedDao,
                mockPlaybackPositionDao,
                mockWatchedEpisodeDao,
                mockViewerDao,
                mockMovieDao,
                mockSeriesDao,
                mockLiveStreamDao,
                mockMovieCategoryDao,
                mockSeriesCategoryDao,
                mockLiveStreamCategoryDao,
                mockTmdbCacheDao
            )

            // When
            advanceUntilIdle()
            val result = testRepository.currentUser.first()

            // Then
            assert(result == null)
            verify(mockSessionManager).getCurrentUserId()
        }

    @Test
    fun `currentUser should return user when sessionManager returns valid userId`() =
        runTest {
            // Given
            val userId = 1
            val testUser =
                UserAccountEntity(
                    id = userId,
                    accountName = "Test Account",
                    username = "testuser",
                    password = "testpass",
                    dns = "https://test.dns.com",
                )
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))
            whenever(mockUserDao.getUserById(userId)).thenReturn(flowOf(testUser))
            val testRepository = UserRepository(
                mockUserDao,
                mockSessionManager,
                mockFavoriteDao,
                mockRecentlyWatchedDao,
                mockPlaybackPositionDao,
                mockWatchedEpisodeDao,
                mockViewerDao,
                mockMovieDao,
                mockSeriesDao,
                mockLiveStreamDao,
                mockMovieCategoryDao,
                mockSeriesCategoryDao,
                mockLiveStreamCategoryDao,
                mockTmdbCacheDao
            )

            // When
            advanceUntilIdle()
            val result = testRepository.currentUser.first()

            // Then
            assert(result == testUser)
            verify(mockSessionManager).getCurrentUserId()
            verify(mockUserDao).getUserById(userId)
        }

    @Test
    fun `addUser should call userDao insertUser with correct user`() =
        runTest {
            // Given
            val testUser =
                UserAccountEntity(
                    id = 0,
                    accountName = "Test Account",
                    username = "testuser",
                    password = "testpass",
                    dns = "https://test.dns.com",
                )
            val expectedId = 1L
            whenever(mockUserDao.insertUser(testUser)).thenReturn(expectedId)
            whenever(mockUserDao.getAllUsers()).thenReturn(flowOf(emptyList()))
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))
            val repository = UserRepository(
                mockUserDao,
                mockSessionManager,
                mockFavoriteDao,
                mockRecentlyWatchedDao,
                mockPlaybackPositionDao,
                mockWatchedEpisodeDao,
                mockViewerDao,
                mockMovieDao,
                mockSeriesDao,
                mockLiveStreamDao,
                mockMovieCategoryDao,
                mockSeriesCategoryDao,
                mockLiveStreamCategoryDao,
                mockTmdbCacheDao
            )

            // When
            val result = repository.addUser(testUser)
            advanceUntilIdle()

            // Then
            assert(result == expectedId)
            verify(mockUserDao).insertUser(testUser)
        }

    @Test
    fun `updateUser should call userDao updateUser with correct user`() =
        runTest {
            // Given
            val testUser =
                UserAccountEntity(
                    id = 1,
                    accountName = "Updated Account",
                    username = "updateduser",
                    password = "updatedpass",
                    dns = "https://updated.dns.com",
                )
            whenever(mockUserDao.getAllUsers()).thenReturn(flowOf(emptyList()))
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))
            val repository = UserRepository(
                mockUserDao,
                mockSessionManager,
                mockFavoriteDao,
                mockRecentlyWatchedDao,
                mockPlaybackPositionDao,
                mockWatchedEpisodeDao,
                mockViewerDao,
                mockMovieDao,
                mockSeriesDao,
                mockLiveStreamDao,
                mockMovieCategoryDao,
                mockSeriesCategoryDao,
                mockLiveStreamCategoryDao,
                mockTmdbCacheDao
            )

            // When
            repository.updateUser(testUser)
            advanceUntilIdle()

            // Then
            verify(mockUserDao).updateUser(testUser)
        }

    @Test
    fun `deleteUser should call userDao deleteUser with correct user`() =
        runTest {
            // Given
            val testUser =
                UserAccountEntity(
                    id = 1,
                    accountName = "Test Account",
                    username = "testuser",
                    password = "testpass",
                    dns = "https://test.dns.com",
                )
            whenever(mockUserDao.getAllUsers()).thenReturn(flowOf(emptyList()))
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))
            val repository = UserRepository(
                mockUserDao,
                mockSessionManager,
                mockFavoriteDao,
                mockRecentlyWatchedDao,
                mockPlaybackPositionDao,
                mockWatchedEpisodeDao,
                mockViewerDao,
                mockMovieDao,
                mockSeriesDao,
                mockLiveStreamDao,
                mockMovieCategoryDao,
                mockSeriesCategoryDao,
                mockLiveStreamCategoryDao,
                mockTmdbCacheDao
            )

            // When
            repository.deleteUser(testUser)
            advanceUntilIdle()

            // Then
            verify(mockUserDao).deleteUser(testUser)
        }

    @Test
    fun `setCurrentUser should call sessionManager saveCurrentUser with correct userId`() =
        runTest {
            // Given
            val testUser =
                UserAccountEntity(
                    id = 1,
                    accountName = "Test Account",
                    username = "testuser",
                    password = "testpass",
                    dns = "https://test.dns.com",
                )
            whenever(mockUserDao.getAllUsers()).thenReturn(flowOf(emptyList()))
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))
            val repository = UserRepository(
                mockUserDao,
                mockSessionManager,
                mockFavoriteDao,
                mockRecentlyWatchedDao,
                mockPlaybackPositionDao,
                mockWatchedEpisodeDao,
                mockViewerDao,
                mockMovieDao,
                mockSeriesDao,
                mockLiveStreamDao,
                mockMovieCategoryDao,
                mockSeriesCategoryDao,
                mockLiveStreamCategoryDao,
                mockTmdbCacheDao
            )

            // When
            repository.setCurrentUser(testUser)
            advanceUntilIdle()

            // Then
            verify(mockSessionManager).saveCurrentUser(testUser.id)
        }

    @Test
    fun `allUsers should return empty list when userDao returns empty list`() =
        runTest {
            // Given
            whenever(mockUserDao.getAllUsers()).thenReturn(flowOf(emptyList()))
            val repository = UserRepository(
                mockUserDao,
                mockSessionManager,
                mockFavoriteDao,
                mockRecentlyWatchedDao,
                mockPlaybackPositionDao,
                mockWatchedEpisodeDao,
                mockViewerDao,
                mockMovieDao,
                mockSeriesDao,
                mockLiveStreamDao,
                mockMovieCategoryDao,
                mockSeriesCategoryDao,
                mockLiveStreamCategoryDao,
                mockTmdbCacheDao
            )

            // When
            advanceUntilIdle()
            val result = repository.allUsers.first()

            // Then
            assert(result.isEmpty())
            verify(mockUserDao).getAllUsers()
        }

    @Test
    fun `allUsers should return single user list`() =
        runTest {
            // Given
            val singleUser =
                UserAccountEntity(
                    id = 1,
                    accountName = "Single User",
                    username = "singleuser",
                    password = "singlepass",
                    dns = "https://single.dns.com",
                )
            whenever(mockUserDao.getAllUsers()).thenReturn(flowOf(listOf(singleUser)))
            val repository = UserRepository(
                mockUserDao,
                mockSessionManager,
                mockFavoriteDao,
                mockRecentlyWatchedDao,
                mockPlaybackPositionDao,
                mockWatchedEpisodeDao,
                mockViewerDao,
                mockMovieDao,
                mockSeriesDao,
                mockLiveStreamDao,
                mockMovieCategoryDao,
                mockSeriesCategoryDao,
                mockLiveStreamCategoryDao,
                mockTmdbCacheDao
            )

            // When
            advanceUntilIdle()
            val result = repository.allUsers.first()

            // Then
            assert(result.size == 1)
            assert(result.first() == singleUser)
            verify(mockUserDao).getAllUsers()
        }

    @Test
    fun `currentUser should return null when userDao returns null for valid userId`() =
        runTest {
            // Given
            val userId = 1
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(userId))
            whenever(mockUserDao.getUserById(userId)).thenReturn(flowOf(null))
            val repository = UserRepository(
                mockUserDao,
                mockSessionManager,
                mockFavoriteDao,
                mockRecentlyWatchedDao,
                mockPlaybackPositionDao,
                mockWatchedEpisodeDao,
                mockViewerDao,
                mockMovieDao,
                mockSeriesDao,
                mockLiveStreamDao,
                mockMovieCategoryDao,
                mockSeriesCategoryDao,
                mockLiveStreamCategoryDao,
                mockTmdbCacheDao
            )

            // When
            advanceUntilIdle()
            val result = repository.currentUser.first()

            // Then
            assert(result == null)
            verify(mockSessionManager).getCurrentUserId()
            verify(mockUserDao).getUserById(userId)
        }

    @Test
    fun `addUser should return correct id when user is added`() =
        runTest {
            // Given
            val testUser =
                UserAccountEntity(
                    id = 0,
                    accountName = "New User",
                    username = "newuser",
                    password = "newpass",
                    dns = "https://new.dns.com",
                )
            val expectedId = 42L
            whenever(mockUserDao.insertUser(testUser)).thenReturn(expectedId)
            whenever(mockUserDao.getAllUsers()).thenReturn(flowOf(emptyList()))
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))
            val repository = UserRepository(
                mockUserDao,
                mockSessionManager,
                mockFavoriteDao,
                mockRecentlyWatchedDao,
                mockPlaybackPositionDao,
                mockWatchedEpisodeDao,
                mockViewerDao,
                mockMovieDao,
                mockSeriesDao,
                mockLiveStreamDao,
                mockMovieCategoryDao,
                mockSeriesCategoryDao,
                mockLiveStreamCategoryDao,
                mockTmdbCacheDao
            )

            // When
            val result = repository.addUser(testUser)
            advanceUntilIdle()

            // Then
            assert(result == expectedId)
            verify(mockUserDao).insertUser(testUser)
        }

    @Test
    fun `multiple operations should work correctly in sequence`() =
        runTest {
            // Given
            val user1 =
                UserAccountEntity(
                    id = 0,
                    accountName = "User 1",
                    username = "user1",
                    password = "pass1",
                    dns = "https://user1.dns.com",
                )
            val user2 =
                UserAccountEntity(
                    id = 1,
                    accountName = "User 2",
                    username = "user2",
                    password = "pass2",
                    dns = "https://user2.dns.com",
                )
            whenever(mockUserDao.insertUser(user1)).thenReturn(1L)
            whenever(mockUserDao.getAllUsers()).thenReturn(flowOf(emptyList()))
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))
            val repository = UserRepository(
                mockUserDao,
                mockSessionManager,
                mockFavoriteDao,
                mockRecentlyWatchedDao,
                mockPlaybackPositionDao,
                mockWatchedEpisodeDao,
                mockViewerDao,
                mockMovieDao,
                mockSeriesDao,
                mockLiveStreamDao,
                mockMovieCategoryDao,
                mockSeriesCategoryDao,
                mockLiveStreamCategoryDao,
                mockTmdbCacheDao
            )

            // When
            repository.addUser(user1)
            advanceUntilIdle()
            repository.updateUser(user2)
            advanceUntilIdle()
            repository.setCurrentUser(user2)
            advanceUntilIdle()
            repository.deleteUser(user2)
            advanceUntilIdle()

            // Then
            verify(mockUserDao).insertUser(user1)
            verify(mockUserDao).updateUser(user2)
            verify(mockSessionManager).saveCurrentUser(user2.id)
            verify(mockUserDao).deleteUser(user2)
        }

    @Test
    fun `setCurrentUser should handle different userId values`() =
        runTest {
            // Given
            val userWithId1 =
                UserAccountEntity(
                    id = 1,
                    accountName = "User 1",
                    username = "user1",
                    password = "pass1",
                    dns = "https://user1.dns.com",
                )
            val userWithId999 =
                UserAccountEntity(
                    id = 999,
                    accountName = "User 999",
                    username = "user999",
                    password = "pass999",
                    dns = "https://user999.dns.com",
                )
            whenever(mockUserDao.getAllUsers()).thenReturn(flowOf(emptyList()))
            whenever(mockSessionManager.getCurrentUserId()).thenReturn(flowOf(null))
            val repository = UserRepository(
                mockUserDao,
                mockSessionManager,
                mockFavoriteDao,
                mockRecentlyWatchedDao,
                mockPlaybackPositionDao,
                mockWatchedEpisodeDao,
                mockViewerDao,
                mockMovieDao,
                mockSeriesDao,
                mockLiveStreamDao,
                mockMovieCategoryDao,
                mockSeriesCategoryDao,
                mockLiveStreamCategoryDao,
                mockTmdbCacheDao
            )

            // When
            repository.setCurrentUser(userWithId1)
            advanceUntilIdle()
            repository.setCurrentUser(userWithId999)
            advanceUntilIdle()

            // Then
            verify(mockSessionManager).saveCurrentUser(1)
            verify(mockSessionManager).saveCurrentUser(999)
        }
}
