package me.thuanc177.kotsune.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexKtorClient
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.ChapterModel
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.Manga
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaWithStatus
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaDexUserProfile
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaStatistics
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.ReadingHistoryItem

/**
 * Repository for MangaDex operations
 */
class MangaDexRepository(
    private val mangaDexClient: MangaDexKtorClient
) {
    private val TAG = "MangaDexRepository"
    
    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return mangaDexClient.isAuthenticated()
    }
    
    /**
     * Get user profile
     */
    suspend fun getUserProfile(): Result<MangaDexUserProfile?> = withContext(Dispatchers.IO) {
        try {
            val profile = mangaDexClient.getUserProfile()
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user profile", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get user's library
     */
    suspend fun getUserLibrary(): Result<List<MangaWithStatus>> = withContext(Dispatchers.IO) {
        try {
            val library = mangaDexClient.getUserLibrary()
            Result.success(library)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user library", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get manga details
     */
    suspend fun getMangaDetails(mangaId: String): Result<Manga> = withContext(Dispatchers.IO) {
        try {
            val manga = mangaDexClient.getMangaDetails(mangaId)
            if (manga != null) {
                Result.success(manga)
            } else {
                Result.failure(Exception("Failed to get manga details"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting manga details for $mangaId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get chapters for a manga
     */
    suspend fun getChapters(mangaId: String): Result<List<ChapterModel>> = withContext(Dispatchers.IO) {
        try {
            val chapters = mangaDexClient.getChapters(mangaId)
            Result.success(chapters)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chapters for manga $mangaId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if manga is in favorites
     */
    suspend fun isMangaInFavorites(mangaId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val isFavorite = mangaDexClient.isMangaInFavorites(mangaId)
            Result.success(isFavorite)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if manga $mangaId is in favorites", e)
            Result.failure(e)
        }
    }
    
    /**
     * Add manga to favorites
     */
    suspend fun addMangaToFavorites(mangaId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = mangaDexClient.addMangaToFavorites(mangaId)
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding manga $mangaId to favorites", e)
            Result.failure(e)
        }
    }
    
    /**
     * Remove manga from favorites
     */
    suspend fun removeMangaFromFavorites(mangaId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = mangaDexClient.removeMangaFromFavorites(mangaId)
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing manga $mangaId from favorites", e)
            Result.failure(e)
        }
    }
    
    /**
     * Mark chapter as read
     */
    suspend fun markChapterRead(chapterId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = mangaDexClient.markChapterRead(chapterId)
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking chapter $chapterId as read", e)
            Result.failure(e)
        }
    }
    
    /**
     * Mark chapter as unread
     */
    suspend fun markChapterUnread(chapterId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = mangaDexClient.markChapterUnread(chapterId)
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking chapter $chapterId as unread", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update reading status for a manga
     */
    suspend fun updateReadingStatus(mangaId: String, status: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = mangaDexClient.updateReadingStatus(mangaId, status)
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating reading status for manga $mangaId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get popular manga
     */
    suspend fun getPopularManga(limit: Int = 20): Result<List<Manga>> = withContext(Dispatchers.IO) {
        try {
            val manga = mangaDexClient.getPopularManga(limit)
            Result.success(manga)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting popular manga", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get latest manga updates
     */
    suspend fun getLatestMangaUpdates(limit: Int = 50, offset: Int = 0): Result<List<Manga>> = withContext(Dispatchers.IO) {
        try {
            val manga = mangaDexClient.getLatestMangaUpdates(limit, offset)
            Result.success(manga)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting latest manga updates", e)
            Result.failure(e)
        }
    }
    
    /**
     * Search for manga
     */
    suspend fun searchForManga(title: String): Result<List<Manga>> = withContext(Dispatchers.IO) {
        try {
            val manga = mangaDexClient.searchForManga(title)
            Result.success(manga)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for manga with title '$title'", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get manga statistics
     */
    suspend fun getMangaStatistics(mangaId: String): Result<MangaStatistics> = withContext(Dispatchers.IO) {
        try {
            val statistics = mangaDexClient.getMangaStatistics(mangaId)
            if (statistics != null) {
                Result.success(statistics)
            } else {
                Result.failure(Exception("Failed to get manga statistics"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting manga statistics for $mangaId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get user's reading status for a manga
     */
    suspend fun getMangaUserStatus(mangaId: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val status = mangaDexClient.getMangaUserStatus(mangaId)
            Result.success(status)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting manga user status for $mangaId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update user's reading status for a manga
     */
    suspend fun updateMangaUserStatus(mangaId: String, status: String?): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = mangaDexClient.updateMangaUserStatus(mangaId, status)
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating manga user status for $mangaId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Rate a manga
     */
    suspend fun rateManga(mangaId: String, rating: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = mangaDexClient.rateManga(mangaId, rating)
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error rating manga $mangaId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete user's rating for a manga
     */
    suspend fun deleteMangaRating(mangaId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = mangaDexClient.deleteMangaRating(mangaId)
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting manga rating for $mangaId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get user's current rating for a manga
     */
    suspend fun getUserMangaRating(mangaId: String): Result<Int?> = withContext(Dispatchers.IO) {
        try {
            val rating = mangaDexClient.getUserMangaRating(mangaId)
            Result.success(rating)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user manga rating for $mangaId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Follow a manga
     */
    suspend fun followManga(mangaId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = mangaDexClient.followManga(mangaId)
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error following manga $mangaId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Unfollow a manga
     */
    suspend fun unfollowManga(mangaId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = mangaDexClient.unfollowManga(mangaId)
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error unfollowing manga $mangaId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get manga read markers
     */
    suspend fun getMangaReadMarkers(mangaId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val markers = mangaDexClient.getMangaReadMarkers(mangaId)
            Result.success(markers)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting manga read markers for $mangaId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update batch manga read markers
     */
    suspend fun updateBatchMangaReadMarkers(
        mangaId: String,
        chaptersToMarkRead: List<String>,
        chaptersToMarkUnread: List<String>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = mangaDexClient.updateBatchMangaReadMarkers(
                mangaId, chaptersToMarkRead, chaptersToMarkUnread
            )
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating batch manga read markers for $mangaId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all user read chapter markers
     */
    suspend fun getAllUserReadChapterMarkers(mangaIds: List<String>? = null): Result<Map<String, List<String>>> = withContext(Dispatchers.IO) {
        try {
            val markers = mangaDexClient.getAllUserReadChapterMarkers(mangaIds)
            Result.success(markers)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all user read chapter markers", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get user's reading history
     */
    suspend fun getUserReadingHistory(limit: Int = 20, offset: Int = 0): Result<List<ReadingHistoryItem>> = withContext(Dispatchers.IO) {
        try {
            val history = mangaDexClient.getUserReadingHistory(limit, offset)
            Result.success(history)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user reading history", e)
            Result.failure(e)
        }
    }
}