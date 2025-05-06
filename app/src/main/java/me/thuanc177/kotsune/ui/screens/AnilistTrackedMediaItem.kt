package me.thuanc177.kotsune.ui.screens

data class AnilistTrackedMediaItem (
    val id: Int,
    val title: String,
    val imageUrl: String,
    val status: String,
    val progress: Int,
    val total: Int? = null,
    val score: Float? = null,
    val startDate: String? = null,
    val finishDate: String? = null,
    val rewatches: Int? = null,
    val notes: String? = null,
    val isPrivate: Boolean = false,
    val isFavorite: Boolean = false
)