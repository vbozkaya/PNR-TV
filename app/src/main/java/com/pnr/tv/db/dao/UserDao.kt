package com.pnr.tv.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pnr.tv.db.entity.UserAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    /**
     * Returns a Flow that emits the list of all user accounts whenever the data changes.
     */
    @Query("SELECT * FROM user_accounts ORDER BY accountName ASC")
    fun getAllUsers(): Flow<List<UserAccountEntity>>

    @Query("SELECT * FROM user_accounts WHERE id = :userId")
    fun getUserById(userId: Int): Flow<UserAccountEntity?>

    /**
     * Inserts a new user into the database. If the user already exists, it will be replaced.
     * @return The row ID of the newly inserted user.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserAccountEntity): Long

    /**
     * Updates an existing user.
     */
    @Update
    suspend fun updateUser(user: UserAccountEntity)

    /**
     * Deletes a user from the database.
     */
    @Delete
    suspend fun deleteUser(user: UserAccountEntity)
}
