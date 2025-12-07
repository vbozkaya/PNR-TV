package com.pnr.tv.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "viewers")
data class ViewerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val isDeletable: Boolean = true,
)



