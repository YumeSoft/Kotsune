package me.thuanc177.kotsune.data.model

import me.thuanc177.kotsune.libs.anime.Anime

data class AnimeListState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val trending: List<Anime> = emptyList(),
    val newEpisodes: List<Anime> = emptyList(),
    val highRating: List<Anime> = emptyList()
)
