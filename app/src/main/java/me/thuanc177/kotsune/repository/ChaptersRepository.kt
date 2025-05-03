package me.thuanc177.kotsune.repository

import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.ChapterModel

/**
 * Singleton repository to store chapter lists for manga
 * Used to pass data between MangaDetailedScreen and ReadMangaScreen
 */
object ChaptersRepository {
    private val chaptersMap = mutableMapOf<String, List<ChapterModel>>()

    fun setChaptersForManga(mangaId: String, chapters: List<ChapterModel>) {
        chaptersMap[mangaId] = chapters
    }

    fun getChaptersForManga(mangaId: String): List<ChapterModel> {
        return chaptersMap[mangaId] ?: emptyList()
    }

    fun clearChapters() {
        chaptersMap.clear()
    }
}