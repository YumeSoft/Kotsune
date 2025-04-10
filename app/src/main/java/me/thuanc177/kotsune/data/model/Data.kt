package me.thuanc177.kotsune.data.model

import me.thuanc177.kotsune.libs.anilist.AnilistTypes.Anime
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes

data class AnimeListState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val trending: List<Anime> = emptyList(),
    val recentlyUpdated: List<Anime> = emptyList(),
    val highRating: List<Anime> = emptyList()
)

data class MangaListState(
    val isLoading: Boolean = false,
    val popular: List<MangaDexTypes.Manga> = emptyList(),
    val latestUpdates: List<MangaDexTypes.Manga> = emptyList(),
    val error: String? = null
)
