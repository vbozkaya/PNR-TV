package com.pnr.tv.db.entity

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val accountName: String,
    val username: String,
    val password: String,
    val dns: String,
) : Parcelable

