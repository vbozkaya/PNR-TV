package com.pnr.tv.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pnr.tv.db.entity.SeriesCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesCategoryDao {
    @Query("SELECT * FROM series_categories ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<SeriesCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<SeriesCategoryEntity>)

    @Query("DELETE FROM series_categories")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(categories: List<SeriesCategoryEntity>) {
        clearAll()
        insertAll(categories)
    }
}
