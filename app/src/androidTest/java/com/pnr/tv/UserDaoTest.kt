package com.pnr.tv

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import com.pnr.tv.db.AppDatabase
import com.pnr.tv.db.dao.UserDao
import com.pnr.tv.db.entity.UserAccountEntity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
@SmallTest
@HiltAndroidTest
class UserDaoTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var userDao: UserDao

    @Before
    fun setup() {
        hiltRule.inject()
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            ).allowMainThreadQueries().build()
        userDao = database.userDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertUser_and_getUserById_returnsCorrectUser() =
        runTest {
            // Given
            val newUser = UserAccountEntity(accountName = "Test", username = "testuser", password = "123", dns = "dns.test")

            // When: Kullanıcıyı veritabanına ekle ve dönen id'yi al
            val newUserId = userDao.insertUser(newUser)
            val retrievedUser = userDao.getUserById(newUserId.toInt()).first()

            // Then: Geri alınan kullanıcının doğru olduğunu doğrula
            assertThat(retrievedUser).isNotNull()
            retrievedUser?.let { user ->
                assertThat(user.id).isEqualTo(newUserId.toInt())
                assertThat(user.accountName).isEqualTo(newUser.accountName)
                assertThat(user.username).isEqualTo(newUser.username)
                assertThat(user.password).isEqualTo(newUser.password)
                assertThat(user.dns).isEqualTo(newUser.dns)
            }
        }
}
