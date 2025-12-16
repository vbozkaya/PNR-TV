package com.pnr.tv.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pnr.tv.db.entity.MovieCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieCategoryDao {
    @Query("SELECT * FROM movie_categories ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<MovieCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<MovieCategoryEntity>)

    @Query("DELETE FROM movie_categories")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(categories: List<MovieCategoryEntity>) {
        clearAll()
        insertAll(categories)
    }
}
