package com.pnr.tv

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.pnr.tv.db.entity.UserAccountEntity
import com.pnr.tv.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

@OptIn(ExperimentalCoroutinesApi::class)
class AddUserViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val mockUserRepository: UserRepository = mock()
    private lateinit var viewModel: AddUserViewModel

    @Before
    fun setup() {
        viewModel = AddUserViewModel(mockUserRepository)
    }

    @After
    fun tearDown() {
        // MainCoroutineRule handles Dispatcher cleanup
    }

    @Test
    fun `addUser should call repository addUser with correct user`() =
        runTest {
            // Given
            val testUser =
                UserAccountEntity(
                    accountName = "Test Account",
                    username = "testuser",
                    password = "testpass",
                    dns = "https://test.dns.com",
                )

            // When
            viewModel.addUser(testUser)
            advanceUntilIdle()

            // Then
            verify(mockUserRepository).addUser(testUser)
            verifyNoMoreInteractions(mockUserRepository)
        }

    @Test
    fun `addUser should handle user with id zero`() =
        runTest {
            // Given
            val testUser =
                UserAccountEntity(
                    id = 0,
                    accountName = "New Account",
                    username = "newuser",
                    password = "newpass",
                    dns = "https://new.dns.com",
                )

            // When
            viewModel.addUser(testUser)
            advanceUntilIdle()

            // Then
            verify(mockUserRepository).addUser(testUser)
            verifyNoMoreInteractions(mockUserRepository)
        }

    @Test
    fun `addUser should handle user with existing id`() =
        runTest {
            // Given
            val testUser =
                UserAccountEntity(
                    id = 5,
                    accountName = "Existing Account",
                    username = "existinguser",
                    password = "existingpass",
                    dns = "https://existing.dns.com",
                )

            // When
            viewModel.addUser(testUser)
            advanceUntilIdle()

            // Then
            verify(mockUserRepository).addUser(testUser)
            verifyNoMoreInteractions(mockUserRepository)
        }

    @Test
    fun `updateUser should call repository updateUser with correct user`() =
        runTest {
            // Given
            val testUser =
                UserAccountEntity(
                    accountName = "Updated Account",
                    username = "updateduser",
                    password = "updatedpass",
                    dns = "https://updated.dns.com",
                )

            // When
            viewModel.updateUser(testUser)
            advanceUntilIdle()

            // Then
            verify(mockUserRepository).updateUser(testUser)
            verifyNoMoreInteractions(mockUserRepository)
        }

    @Test
    fun `updateUser should handle user with different id values`() =
        runTest {
            // Given
            val testUser =
                UserAccountEntity(
                    id = 10,
                    accountName = "Updated Account 2",
                    username = "updateduser2",
                    password = "updatedpass2",
                    dns = "https://updated2.dns.com",
                )

            // When
            viewModel.updateUser(testUser)
            advanceUntilIdle()

            // Then
            verify(mockUserRepository).updateUser(testUser)
            verifyNoMoreInteractions(mockUserRepository)
        }

    @Test
    fun `addUser and updateUser should work independently`() =
        runTest {
            // Given
            val userToAdd =
                UserAccountEntity(
                    accountName = "New User",
                    username = "newuser",
                    password = "newpass",
                    dns = "https://new.dns.com",
                )
            val userToUpdate =
                UserAccountEntity(
                    id = 1,
                    accountName = "Updated User",
                    username = "updateduser",
                    password = "updatedpass",
                    dns = "https://updated.dns.com",
                )

            // When
            viewModel.addUser(userToAdd)
            advanceUntilIdle()
            viewModel.updateUser(userToUpdate)
            advanceUntilIdle()

            // Then
            verify(mockUserRepository).addUser(userToAdd)
            verify(mockUserRepository).updateUser(userToUpdate)
        }
}
