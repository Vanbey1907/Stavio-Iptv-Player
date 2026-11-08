package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Long,
    val url: String,
    val name: String,
    val groupTitle: String,
    val logoUrl: String = "",
    val tvgId: String? = null,
    val tvgUrl: String? = null,
    val isFavorite: Boolean = false
)
