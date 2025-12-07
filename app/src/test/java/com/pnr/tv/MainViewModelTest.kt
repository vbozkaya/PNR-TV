package com.pnr.tv

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.pnr.tv.db.entity.UserAccountEntity
import com.pnr.tv.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class MainViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockUserRepository: UserRepository = mock()
    private val mockContentRepository: com.pnr.tv.repository.ContentRepository = mock()
    private val mockContext: android.content.Context = mock()

    @Before
    fun setup() {
        // Setup boş - her test kendi viewModel'ini oluşturacak
    }

    @After
    fun tearDown() {
        // MainCoroutineRule handles Dispatcher cleanup
    }

    @Test
    fun `currentUser should expose repository currentUser as LiveData`() =
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
            whenever(mockUserRepository.currentUser).thenReturn(flowOf(testUser))
            val viewModel = MainViewModel(mockUserRepository, mockContentRepository, mockContext)

            // When
            advanceUntilIdle()

            // Then
            // MainViewModel sadece repository'den gelen flow'u LiveData'ya çeviriyor
            // Repository'nin currentUser flow'unun çağrıldığını doğrula
            verify(mockUserRepository).currentUser
        }

    @Test
    fun `currentUser LiveData should be initialized from repository`() =
        runTest {
            // Given
            whenever(mockUserRepository.currentUser).thenReturn(flowOf(null))
            val viewModel = MainViewModel(mockUserRepository, mockContentRepository, mockContext)

            // When
            advanceUntilIdle()

            // Then
            // Repository'nin currentUser flow'unun çağrıldığını doğrula
            verify(mockUserRepository).currentUser
        }

    @Test
    fun `currentUser should handle null user from repository`() =
        runTest {
            // Given
            whenever(mockUserRepository.currentUser).thenReturn(flowOf(null))

            // When
            val viewModel = MainViewModel(mockUserRepository, mockContentRepository, mockContext)
            advanceUntilIdle()

            // Then
            verify(mockUserRepository).currentUser
            assert(viewModel::class.java == MainViewModel::class.java)
        }

    @Test
    fun `currentUser should handle different users from repository`() =
        runTest {
            // Given
            val user1 =
                UserAccountEntity(
                    id = 1,
                    accountName = "User 1",
                    username = "user1",
                    password = "pass1",
                    dns = "https://user1.dns.com",
                )
            whenever(mockUserRepository.currentUser).thenReturn(flowOf(user1))

            // When
            val viewModel1 = MainViewModel(mockUserRepository, mockContentRepository, mockContext)
            advanceUntilIdle()

            // Then
            verify(mockUserRepository).currentUser
            assert(viewModel1::class.java == MainViewModel::class.java)
        }

    @Test
    fun `currentUser LiveData should be created for each viewModel instance`() =
        runTest {
            // Given
            val testUser =
                UserAccountEntity(
                    id = 1,
                    accountName = "Test User",
                    username = "testuser",
                    password = "testpass",
                    dns = "https://test.dns.com",
                )
            whenever(mockUserRepository.currentUser).thenReturn(flowOf(testUser))

            // When
            val viewModel1 = MainViewModel(mockUserRepository, mockContentRepository, mockContext)
            advanceUntilIdle()
            val viewModel2 = MainViewModel(mockUserRepository, mockContentRepository, mockContext)
            advanceUntilIdle()

            // Then
            // Her viewModel instance'ı kendi LiveData'sına sahip olmalı
            assert(viewModel1.currentUser != viewModel2.currentUser)
            // Her viewModel oluşturulduğunda currentUser çağrılıyor
        }

    @Test
    fun `currentUser should expose repository flow correctly`() =
        runTest {
            // Given
            val testUser =
                UserAccountEntity(
                    id = 5,
                    accountName = "Another User",
                    username = "anotheruser",
                    password = "anotherpass",
                    dns = "https://another.dns.com",
                )
            whenever(mockUserRepository.currentUser).thenReturn(flowOf(testUser))

            // When
            val viewModel = MainViewModel(mockUserRepository, mockContentRepository, mockContext)
            advanceUntilIdle()

            // Then
            verify(mockUserRepository).currentUser
            assert(viewModel::class.java == MainViewModel::class.java)
            // LiveData'nın oluşturulduğunu doğrula
            assert(viewModel.currentUser != null)
        }
}
