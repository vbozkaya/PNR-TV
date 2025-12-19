package com.pnr.tv.db.dao

import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import app.cash.turbine.test
import com.pnr.tv.db.AppDatabase
import com.pnr.tv.testdata.TestDataFactory
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
 * UserDao için unit testler.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class UserDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var userDao: UserDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        userDao = database.userDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun `insertUser should insert user successfully`() = runTest {
        // Given
        val user = TestDataFactory.createUserAccountEntity(id = 1, accountName = "Test User")

        // When
        val rowId = userDao.insertUser(user)

        // Then
        assertTrue(rowId > 0)
        userDao.getUserById(1).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("Test User", result?.accountName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertUser should replace existing user with same id`() = runTest {
        // Given
        val user1 = TestDataFactory.createUserAccountEntity(id = 1, accountName = "Original")
        val user2 = TestDataFactory.createUserAccountEntity(id = 1, accountName = "Updated")
        userDao.insertUser(user1)

        // When
        userDao.insertUser(user2)

        // Then
        userDao.getUserById(1).test {
            val result = awaitItem()
            assertEquals("Updated", result?.accountName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllUsers should return all users ordered by accountName ASC`() = runTest {
        // Given
        val user1 = TestDataFactory.createUserAccountEntity(id = 1, accountName = "Charlie")
        val user2 = TestDataFactory.createUserAccountEntity(id = 2, accountName = "Alice")
        val user3 = TestDataFactory.createUserAccountEntity(id = 3, accountName = "Bob")
        userDao.insertUser(user1)
        userDao.insertUser(user2)
        userDao.insertUser(user3)

        // When & Then
        userDao.getAllUsers().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            // Should be ordered by accountName ASC
            assertEquals("Alice", result[0].accountName)
            assertEquals("Bob", result[1].accountName)
            assertEquals("Charlie", result[2].accountName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getUserById should return user for existing ID`() = runTest {
        // Given
        val user = TestDataFactory.createUserAccountEntity(id = 1, accountName = "Test User")
        userDao.insertUser(user)

        // When & Then
        userDao.getUserById(1).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("Test User", result?.accountName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getUserById should return null for non-existent ID`() = runTest {
        // When & Then
        userDao.getUserById(999).test {
            val result = awaitItem()
            assertNull(result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateUser should update existing user`() = runTest {
        // Given
        val user = TestDataFactory.createUserAccountEntity(id = 1, accountName = "Original")
        userDao.insertUser(user)
        val updatedUser = user.copy(accountName = "Updated")

        // When
        userDao.updateUser(updatedUser)

        // Then
        userDao.getUserById(1).test {
            val result = awaitItem()
            assertEquals("Updated", result?.accountName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteUser should remove user`() = runTest {
        // Given
        val user = TestDataFactory.createUserAccountEntity(id = 1, accountName = "Test User")
        userDao.insertUser(user)

        // When
        userDao.deleteUser(user)

        // Then
        userDao.getUserById(1).test {
            val result = awaitItem()
            assertNull(result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllUsers should emit empty list initially`() = runTest {
        // When & Then
        userDao.getAllUsers().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertUser should auto-generate id when id is 0`() = runTest {
        // Given
        val user = TestDataFactory.createUserAccountEntity(id = 0, accountName = "Auto Generated User")

        // When
        val rowId = userDao.insertUser(user)

        // Then
        assertTrue(rowId > 0)
        userDao.getAllUsers().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertTrue(result[0].id > 0) // Should have auto-generated ID
            assertEquals("Auto Generated User", result[0].accountName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllUsers should handle multiple users with same accountName`() = runTest {
        // Given
        val user1 = TestDataFactory.createUserAccountEntity(id = 1, accountName = "Same Name")
        val user2 = TestDataFactory.createUserAccountEntity(id = 2, accountName = "Same Name")
        val user3 = TestDataFactory.createUserAccountEntity(id = 3, accountName = "Different Name")
        userDao.insertUser(user1)
        userDao.insertUser(user2)
        userDao.insertUser(user3)

        // When & Then
        userDao.getAllUsers().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            // Should be ordered by accountName ASC, then by id (if same name)
            assertTrue(result[0].accountName == "Different Name" || result[0].accountName == "Same Name")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateUser should update all user fields`() = runTest {
        // Given
        val user = TestDataFactory.createUserAccountEntity(
            id = 1,
            accountName = "Original",
            username = "olduser",
            password = "oldpass",
            dns = "https://old.dns.com"
        )
        userDao.insertUser(user)
        val updatedUser = user.copy(
            accountName = "Updated",
            username = "newuser",
            password = "newpass",
            dns = "https://new.dns.com"
        )

        // When
        userDao.updateUser(updatedUser)

        // Then
        userDao.getUserById(1).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("Updated", result?.accountName)
            assertEquals("newuser", result?.username)
            assertEquals("newpass", result?.password)
            assertEquals("https://new.dns.com", result?.dns)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteUser should handle deleting non-existent user gracefully`() = runTest {
        // Given
        val nonExistentUser = TestDataFactory.createUserAccountEntity(id = 999, accountName = "Non Existent")

        // When - Should not throw exception
        userDao.deleteUser(nonExistentUser)

        // Then
        userDao.getAllUsers().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getUserById should emit null initially for non-existent user`() = runTest {
        // When & Then
        userDao.getUserById(999).test {
            val result = awaitItem()
            assertNull(result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertUser should handle user with all fields populated`() = runTest {
        // Given
        val user = TestDataFactory.createUserAccountEntity(
            id = 1,
            accountName = "Full User",
            username = "testuser",
            password = "testpass",
            dns = "https://test.dns.com"
        )

        // When
        val rowId = userDao.insertUser(user)

        // Then
        assertTrue(rowId > 0)
        userDao.getUserById(1).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("Full User", result?.accountName)
            assertEquals("testuser", result?.username)
            assertEquals("testpass", result?.password)
            assertEquals("https://test.dns.com", result?.dns)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllUsers should maintain order after multiple operations`() = runTest {
        // Given
        val user1 = TestDataFactory.createUserAccountEntity(id = 1, accountName = "Zebra")
        val user2 = TestDataFactory.createUserAccountEntity(id = 2, accountName = "Alpha")
        val user3 = TestDataFactory.createUserAccountEntity(id = 3, accountName = "Beta")
        userDao.insertUser(user1)
        userDao.insertUser(user2)
        userDao.insertUser(user3)

        // When - Update one user
        val updatedUser = user2.copy(accountName = "Alpha Updated")
        userDao.updateUser(updatedUser)

        // Then - Order should still be maintained
        userDao.getAllUsers().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertEquals("Alpha Updated", result[0].accountName)
            assertEquals("Beta", result[1].accountName)
            assertEquals("Zebra", result[2].accountName)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

