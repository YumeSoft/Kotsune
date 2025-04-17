package me.thuanc177.kotsune.model

/**
 * Common model for representing anime episodes across the application
 */
data class UiEpisodeModel(
    val number: Float,
    val thumbnail: String? = null,
    val title: String? = null,
    val description: String? = null,
    val uploadDate: String? = null
)