package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // "M3U", "MAG", "XTREAM"
    val urlOrPath: String,
    val macAddress: String? = null,
    val expirationDate: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
