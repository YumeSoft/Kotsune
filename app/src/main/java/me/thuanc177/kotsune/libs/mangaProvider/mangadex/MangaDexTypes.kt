package me.thuanc177.kotsune.libs.mangaProvider.mangadex

import me.thuanc177.kotsune.viewmodel.SearchViewModel

object MangaDexTypes {
    data class Manga(
        val id: String,
        val title: List<String>,
        val poster: String?,
        val status: String,
        val description: String,
        val lastUpdated: String?,
        val lastChapter: Int?,
        val latestUploadedChapterId: String?,
        val year: Int?,
        val contentRating: String,
        val tags: List<SearchViewModel.MangaTag> = emptyList(),
        val rating: Float? = null
    )
}