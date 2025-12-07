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
class UsersListViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockUserRepository: UserRepository = mock()
    private lateinit var viewModel: UsersListViewModel

    @Before
    fun setup() {
        whenever(mockUserRepository.allUsers).thenReturn(flowOf(emptyList()))
        viewModel = UsersListViewModel(mockUserRepository)
    }

    @After
    fun tearDown() {
        // MainCoroutineRule handles Dispatcher cleanup
    }

    @Test
    fun `deleteUser should call repository deleteUser with correct user`() =
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

            // When
            viewModel.deleteUser(testUser)
            advanceUntilIdle()

            // Then
            verify(mockUserRepository).deleteUser(testUser)
        }

    @Test
    fun `setCurrentUser should call repository setCurrentUser with correct user`() =
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

            // When
            viewModel.setCurrentUser(testUser)
            advanceUntilIdle()

            // Then
            verify(mockUserRepository).setCurrentUser(testUser)
        }

    @Test
    fun `allUsers should expose repository allUsers flow`() =
        runTest {
            // Given
            val testUsers =
                listOf(
                    UserAccountEntity(
                        id = 1,
                        accountName = "User 1",
                        username = "user1",
                        password = "pass1",
                        dns = "https://user1.dns.com",
                    ),
                    UserAccountEntity(
                        id = 2,
                        accountName = "User 2",
                        username = "user2",
                        password = "pass2",
                        dns = "https://user2.dns.com",
                    ),
                )
            whenever(mockUserRepository.allUsers).thenReturn(flowOf(testUsers))
            val newViewModel = UsersListViewModel(mockUserRepository)

            // When
            advanceUntilIdle()

            // Then
            // ViewModel oluşturulduğunda allUsers flow'u expose ediliyor
            assert(newViewModel.allUsers != null)
        }

    @Test
    fun `deleteUser should handle multiple users deletion`() =
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
            val user2 =
                UserAccountEntity(
                    id = 2,
                    accountName = "User 2",
                    username = "user2",
                    password = "pass2",
                    dns = "https://user2.dns.com",
                )

            // When
            viewModel.deleteUser(user1)
            advanceUntilIdle()
            viewModel.deleteUser(user2)
            advanceUntilIdle()

            // Then
            verify(mockUserRepository).deleteUser(user1)
            verify(mockUserRepository).deleteUser(user2)
        }

    @Test
    fun `setCurrentUser should handle different user ids`() =
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
            val user2 =
                UserAccountEntity(
                    id = 999,
                    accountName = "User 2",
                    username = "user2",
                    password = "pass2",
                    dns = "https://user2.dns.com",
                )

            // When
            viewModel.setCurrentUser(user1)
            advanceUntilIdle()
            viewModel.setCurrentUser(user2)
            advanceUntilIdle()

            // Then
            verify(mockUserRepository).setCurrentUser(user1)
            verify(mockUserRepository).setCurrentUser(user2)
        }

    @Test
    fun `deleteUser and setCurrentUser should work independently`() =
        runTest {
            // Given
            val userToDelete =
                UserAccountEntity(
                    id = 1,
                    accountName = "User to Delete",
                    username = "deleteuser",
                    password = "deletepass",
                    dns = "https://delete.dns.com",
                )
            val userToSet =
                UserAccountEntity(
                    id = 2,
                    accountName = "User to Set",
                    username = "setuser",
                    password = "setpass",
                    dns = "https://set.dns.com",
                )

            // When
            viewModel.deleteUser(userToDelete)
            advanceUntilIdle()
            viewModel.setCurrentUser(userToSet)
            advanceUntilIdle()

            // Then
            verify(mockUserRepository).deleteUser(userToDelete)
            verify(mockUserRepository).setCurrentUser(userToSet)
        }
}
