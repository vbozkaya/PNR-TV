package com.pnr.tv.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pnr.tv.db.entity.LiveStreamEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Dao
interface LiveStreamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(liveStreams: List<LiveStreamEntity>)

    @Query("SELECT * FROM live_streams")
    fun getAll(): Flow<List<LiveStreamEntity>>

    @Query("SELECT * FROM live_streams WHERE categoryId = :categoryId ORDER BY name ASC")
    fun getByCategoryId(categoryId: Int): Flow<List<LiveStreamEntity>>

    @Query("SELECT * FROM live_streams WHERE categoryId = :categoryId ORDER BY name ASC")
    suspend fun getByCategoryIdSync(categoryId: Int): List<LiveStreamEntity>

    @Query("SELECT * FROM live_streams WHERE streamId IN (:streamIds)")
    suspend fun getByIds(streamIds: List<Int>): List<LiveStreamEntity>

    @Query("DELETE FROM live_streams")
    suspend fun clearAll()

    @Delete
    suspend fun deleteStreams(liveStreams: List<LiveStreamEntity>)

    @Transaction
    suspend fun replaceAll(liveStreams: List<LiveStreamEntity>) {
        clearAll()
        if (liveStreams.isNotEmpty()) {
            insertAll(liveStreams)
        }
    }

    /**
     * Akıllı güncelleme (Upsert) metodu.
     * Sadece değişiklikleri işleyerek veritabanı performansını optimize eder.
     *
     * Mantık:
     * 1. Mevcut kayıtları al
     * 2. Silinecekleri tespit et (eskide olup yeni de olmayanlar)
     * 3. Eklenecek/güncellenecekleri tespit et (upsert)
     * 4. Sadece gerekli işlemleri yap
     */
    @Transaction
    suspend fun upsert(newStreams: List<LiveStreamEntity>) {
        // Mevcut tüm yayınları al
        val oldStreams = getAll().first()
        val oldStreamMap = oldStreams.associateBy { it.streamId }
        val newStreamMap = newStreams.associateBy { it.streamId }

        // Silinecekler: Eskide olup yeni de olmayanlar
        val toDelete = oldStreams.filter { it.streamId !in newStreamMap }
        if (toDelete.isNotEmpty()) {
            deleteStreams(toDelete)
        }

        // Eklenecek ve güncellenecekler (Upsert)
        // Room'un @Insert(onConflict = OnConflictStrategy.REPLACE) stratejisi
        // bu işi tek seferde verimli bir şekilde yapar.
        if (newStreams.isNotEmpty()) {
            insertAll(newStreams)
        }
    }
}
