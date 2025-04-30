package me.thuanc177.kotsune.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "favorites")

class FavoritesRepository(private val context: Context) {
    private val TAG = "FavoritesRepository"
    private val FAVORITES_KEY = stringPreferencesKey("manga_favorites")

    suspend fun addFavorite(mangaId: String, title: String, posterUrl: String?) {
        try {
            val currentFavorites = getFavoritesJson()

            // Check if manga already exists in favorites
            if (!isMangaFavorite(mangaId)) {
                val mangaJson = JSONObject().apply {
                    put("id", mangaId)
                    put("title", title)
                    put("poster", posterUrl ?: "")
                    put("addedAt", System.currentTimeMillis())
                }
                currentFavorites.put(mangaJson)

                // Save updated favorites
                saveFavorites(currentFavorites)
                Log.d(TAG, "Added manga to favorites: $mangaId - $title")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding favorite", e)
        }
    }

    suspend fun removeFavorite(mangaId: String) {
        try {
            val currentFavorites = getFavoritesJson()
            val updatedFavorites = JSONArray()

            // Copy all items except the one to remove
            for (i in 0 until currentFavorites.length()) {
                val item = currentFavorites.getJSONObject(i)
                if (item.getString("id") != mangaId) {
                    updatedFavorites.put(item)
                }
            }

            // Save updated favorites
            saveFavorites(updatedFavorites)
            Log.d(TAG, "Removed manga from favorites: $mangaId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing favorite", e)
        }
    }

    suspend fun isMangaFavorite(mangaId: String): Boolean {
        val favorites = getFavoritesJson()
        for (i in 0 until favorites.length()) {
            val item = favorites.getJSONObject(i)
            if (item.getString("id") == mangaId) {
                return true
            }
        }
        return false
    }

    suspend fun getAllFavorites(): List<FavoriteManga> {
        val favorites = getFavoritesJson()
        val result = mutableListOf<FavoriteManga>()

        for (i in 0 until favorites.length()) {
            try {
                val item = favorites.getJSONObject(i)
                result.add(
                    FavoriteManga(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        posterUrl = item.optString("poster"),
                        addedAt = item.optLong("addedAt", 0)
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing favorite item", e)
            }
        }

        return result.sortedByDescending { it.addedAt }
    }

    fun getFavoritesFlow(): Flow<List<FavoriteManga>> {
        return context.favoritesDataStore.data.map { preferences ->
            val favoritesJsonString = preferences[FAVORITES_KEY] ?: "[]"
            val favoritesJson = JSONArray(favoritesJsonString)
            val result = mutableListOf<FavoriteManga>()

            for (i in 0 until favoritesJson.length()) {
                try {
                    val item = favoritesJson.getJSONObject(i)
                    result.add(
                        FavoriteManga(
                            id = item.getString("id"),
                            title = item.getString("title"),
                            posterUrl = item.optString("poster"),
                            addedAt = item.optLong("addedAt", 0)
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing favorite item in flow", e)
                }
            }

            result.sortedByDescending { it.addedAt }
        }
    }

    private suspend fun getFavoritesJson(): JSONArray {
        val preferences = context.favoritesDataStore.data.first()
        val jsonString = preferences[FAVORITES_KEY] ?: "[]"
        return try {
            JSONArray(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing favorites JSON, returning empty array", e)
            JSONArray()
        }
    }

    private suspend fun saveFavorites(favorites: JSONArray) {
        try {
            context.favoritesDataStore.edit { preferences ->
                preferences[FAVORITES_KEY] = favorites.toString()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error saving favorites", e)
        }
    }

    data class FavoriteManga(
        val id: String,
        val title: String,
        val posterUrl: String,
        val addedAt: Long
    )
}