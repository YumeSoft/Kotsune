package me.thuanc177.kotsune.ui.screens

data class TrackedMediaItem (
    val id: Int,
    val title: String,
    val imageUrl: String,
    val status: String,
    val progress: Int,
    val total: Int? = null,
    val score: Float? = null
)