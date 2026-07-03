package com.example.myplay

data class Album(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val treeUri: String,
    val trackIndex: Int = 0,
    val positionMs: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val orderIndex: Int = 0
)

data class Track(
    val name: String,
    val uri: android.net.Uri
)
