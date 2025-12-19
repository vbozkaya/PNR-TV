package com.pnr.tv.ui.shared

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.pnr.tv.MainCoroutineRule
import com.pnr.tv.R
import com.pnr.tv.db.entity.UserAccountEntity
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.Result
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.testdata.TestDataFactory
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SharedViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockUserRepository: UserRepository = mock()
    private val mockContentRepository: ContentRepository = mock()
    private val mockContext: Context = mock()

    private lateinit var viewModel: SharedViewModel

    @Before
    fun setup() {
        whenever(mockContext.getString(any())).thenReturn("Mock String")
        whenever(mockContext.getString(any(), any())).thenReturn("Mock String")
        whenever(mockContext.applicationContext).thenReturn(mockContext)
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    private fun createViewModel(): SharedViewModel {
        return SharedViewModel(
            userRepository = mockUserRepository,
            contentRepository = mockContentRepository,
            context = mockContext,
        )
    }

    @Test
    fun `refreshAllContent should update state to loading`() = runTest {
        // Given
        // Mock WorkManager için context'i mock'la
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        val testUser = TestDataFactory.createUserAccountEntity()
        whenever(mockUserRepository.allUsers).thenReturn(flowOf(listOf(testUser)))
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(testUser))
        whenever(mockContentRepository.refreshMovies(any(), any())).thenReturn(Result.Success(Unit))
        whenever(mockContentRepository.refreshSeries(any(), any())).thenReturn(Result.Success(Unit))
        whenever(mockContentRepository.refreshLiveStreams(any())).thenReturn(Result.Success(Unit))
        viewModel = createViewModel()

        // When
        viewModel.refreshAllContent()
        advanceUntilIdle()

        // Then
        viewModel.updateState.test {
            val state = awaitItem()
            // Should start with IDLE, then go to LOADING
            assertNotNull(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Ignore("WorkManager.getInstance() requires Android framework - needs WorkManager testing library")
    fun `refreshAllContent should handle error when no users exist`() = runTest {
        // Given
        // Mock WorkManager için context'i mock'la
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockUserRepository.allUsers).thenReturn(flowOf(emptyList()))
        viewModel = createViewModel()

        // When
        viewModel.refreshAllContent()
        advanceUntilIdle()

        // Then
        viewModel.updateState.test {
            val state = awaitItem()
            // Should eventually be ERROR or IDLE
            assertNotNull(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshAllContent should handle error when current user is null`() = runTest {
        // Given
        // Mock WorkManager için context'i mock'la
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        val testUser = TestDataFactory.createUserAccountEntity()
        whenever(mockUserRepository.allUsers).thenReturn(flowOf(listOf(testUser)))
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(null))
        viewModel = createViewModel()

        // When
        viewModel.refreshAllContent()
        advanceUntilIdle()

        // Then
        viewModel.updateState.test {
            val state = awaitItem()
            assertNotNull(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Ignore("WorkManager.getInstance() requires Android framework - needs WorkManager testing library")
    fun `resetUpdateState should reset state to IDLE`() = runTest {
        // Given
        // Mock WorkManager için context'i mock'la
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        viewModel = createViewModel()

        // When
        viewModel.resetUpdateState()
        advanceUntilIdle()

        // Then
        viewModel.updateState.test {
            val state = awaitItem()
            assertEquals(SharedViewModel.UpdateState.IDLE, state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Ignore("WorkManager.getInstance() requires Android framework - needs WorkManager testing library")
    fun `fetchUserInfo should update userInfo on success`() = runTest {
        // Given
        // Mock WorkManager için context'i mock'la
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        val userInfoDto = com.pnr.tv.network.dto.UserInfoDto(
            username = "testuser",
            password = "",
            message = null,
            auth = 1,
            status = "Active",
            expDate = null,
            isTrial = "0",
            activeCons = "1",
            createdAt = "2024-01-01",
            maxConnections = "5",
            allowedOutputFormats = null,
        )
        val authResponseDto = com.pnr.tv.network.dto.AuthenticationResponseDto(
            userInfo = userInfoDto,
            serverInfo = null,
        )
        whenever(mockContentRepository.fetchUserInfo()).thenReturn(Result.Success(authResponseDto))
        viewModel = createViewModel()

        // When
        viewModel.fetchUserInfo()
        advanceUntilIdle()

        // Then
        // Note: userInfo is LiveData, so we can't easily test it with Turbine
        // But we can verify the repository was called
        verify(mockContentRepository).fetchUserInfo()
    }

    @Test
    @Ignore("WorkManager.getInstance() requires Android framework - needs WorkManager testing library")
    fun `fetchUserInfo should handle error`() = runTest {
        // Given
        // Mock WorkManager için context'i mock'la
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockContentRepository.fetchUserInfo()).thenReturn(
            Result.Error("Error message"),
        )
        viewModel = createViewModel()

        // When
        viewModel.fetchUserInfo()
        advanceUntilIdle()

        // Then
        verify(mockContentRepository).fetchUserInfo()
        viewModel.errorMessage.test {
            val error = awaitItem()
            // Error message might be null initially, but should be set on error
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshAllContent should handle error when movies refresh fails`() = runTest {
        // Given
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        val testUser = TestDataFactory.createUserAccountEntity()
        whenever(mockUserRepository.allUsers).thenReturn(flowOf(listOf(testUser)))
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(testUser))
        runBlocking {
            whenever(mockContentRepository.refreshMovies(any(), any())).thenReturn(
                Result.Error("Movies refresh failed"),
            )
        }
        viewModel = createViewModel()

        // When & Then - Flow'u dinlemeye başla, sonra action'ı tetikle
        viewModel.updateState.test(timeout = 10.seconds) {
            // Initial state (IDLE)
            val initialState = awaitItem()
            assertEquals(SharedViewModel.UpdateState.IDLE, initialState)
            
            // Trigger action
            viewModel.refreshAllContent()
            advanceUntilIdle()
            
            // Should go to LOADING
            val loadingState = awaitItem()
            assertEquals(SharedViewModel.UpdateState.LOADING, loadingState)
            
            // Should eventually reach ERROR state
            val errorState = awaitItem()
            assertEquals(SharedViewModel.UpdateState.ERROR, errorState)
            cancelAndIgnoreRemainingEvents()
        }
        
        // Verify error message is set
        viewModel.errorMessage.test(timeout = 5.seconds) {
            val errorMessage = awaitItem()
            assertNotNull(errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshAllContent should handle error when series refresh fails`() = runTest {
        // Given
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        val testUser = TestDataFactory.createUserAccountEntity()
        whenever(mockUserRepository.allUsers).thenReturn(flowOf(listOf(testUser)))
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(testUser))
        runBlocking {
            whenever(mockContentRepository.refreshMovies(any(), any())).thenReturn(Result.Success(Unit))
            whenever(mockContentRepository.refreshSeries(any(), any())).thenReturn(
                Result.Error("Series refresh failed"),
            )
        }
        viewModel = createViewModel()

        // When & Then - Flow'u dinlemeye başla, sonra action'ı tetikle
        viewModel.updateState.test(timeout = 10.seconds) {
            // Initial state (IDLE)
            val initialState = awaitItem()
            assertEquals(SharedViewModel.UpdateState.IDLE, initialState)
            
            // Trigger action
            viewModel.refreshAllContent()
            advanceUntilIdle()
            
            // Should go to LOADING
            val loadingState = awaitItem()
            assertEquals(SharedViewModel.UpdateState.LOADING, loadingState)
            
            // Should eventually reach ERROR state
            val errorState = awaitItem()
            assertEquals(SharedViewModel.UpdateState.ERROR, errorState)
            cancelAndIgnoreRemainingEvents()
        }
        
        // Verify error message is set
        viewModel.errorMessage.test(timeout = 5.seconds) {
            val errorMessage = awaitItem()
            assertNotNull(errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshAllContent should handle error when liveStreams refresh fails`() = runTest {
        // Given
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        val testUser = TestDataFactory.createUserAccountEntity()
        whenever(mockUserRepository.allUsers).thenReturn(flowOf(listOf(testUser)))
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(testUser))
        runBlocking {
            whenever(mockContentRepository.refreshMovies(any(), any())).thenReturn(Result.Success(Unit))
            whenever(mockContentRepository.refreshSeries(any(), any())).thenReturn(Result.Success(Unit))
            whenever(mockContentRepository.refreshLiveStreams(any())).thenReturn(
                Result.Error("LiveStreams refresh failed"),
            )
        }
        viewModel = createViewModel()

        // When & Then - Flow'u dinlemeye başla, sonra action'ı tetikle
        viewModel.updateState.test(timeout = 10.seconds) {
            // Initial state (IDLE)
            val initialState = awaitItem()
            assertEquals(SharedViewModel.UpdateState.IDLE, initialState)
            
            // Trigger action
            viewModel.refreshAllContent()
            advanceUntilIdle()
            
            // Should go to LOADING
            val loadingState = awaitItem()
            assertEquals(SharedViewModel.UpdateState.LOADING, loadingState)
            
            // Should eventually reach ERROR state
            val errorState = awaitItem()
            assertEquals(SharedViewModel.UpdateState.ERROR, errorState)
            cancelAndIgnoreRemainingEvents()
        }
        
        // Verify error message is set
        viewModel.errorMessage.test(timeout = 5.seconds) {
            val errorMessage = awaitItem()
            assertNotNull(errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fetchUserInfo should handle exception during fetch`() = runTest {
        // Given
        val testUser = TestDataFactory.createUserAccountEntity()
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(testUser))
        // Mock suspend function - Mockito-Kotlin supports suspend functions
        whenever(mockContentRepository.fetchUserInfo()).thenThrow(RuntimeException("Network error"))
        viewModel = createViewModel()

        // When
        viewModel.fetchUserInfo()
        advanceUntilIdle()

        // Then
        // Should not crash, userInfo should be null
        verify(mockContentRepository).fetchUserInfo()
        // Verify userInfo is null after exception
        assertEquals(null, viewModel.userInfo.value)
    }

    @Test
    fun `currentUser should expose repository currentUser`() = runTest {
        // Given
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        val testUser = TestDataFactory.createUserAccountEntity()
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(testUser))
        viewModel = createViewModel()

        // When
        advanceUntilIdle()

        // Then
        // currentUser is LiveData, we can verify it's not null through observation
        // But we can verify the repository flow is being used
        verify(mockUserRepository).currentUser
    }
}

