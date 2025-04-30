package me.thuanc177.kotsune.model

/**
 * UI model for episode data that can be used across different screens
 */
data class UiEpisodeModel(
    val number: Float,
    val title: String? = null,
    val thumbnail: String? = null,
    val description: String? = null,
    val uploadDate: String? = null,
    val isWatched: Boolean = false,
    val progress: Float = 0f  // Progress percentage (0-100) if partially watched
)
