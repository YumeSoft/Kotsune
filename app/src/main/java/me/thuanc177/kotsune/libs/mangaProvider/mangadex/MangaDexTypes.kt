package me.thuanc177.kotsune.libs.mangaProvider.mangadex

object MangaDexTypes {
    data class Manga(
        val id: String,
        val title: List<String>,
        val coverImage: String?,
        val status: String,
        val description: String,
        val lastUpdated: String?,
        val year: Int?,
        val contentRating: String
    )
}