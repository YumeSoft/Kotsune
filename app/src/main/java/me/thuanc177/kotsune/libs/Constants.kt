package me.thuanc177.kotsune.libs

import android.content.Context
import me.thuanc177.kotsune.KotsuneApplication
import java.io.File

object Constants {
    const val ANILIST_ENDPOINT = "https://graphql.anilist.co"
    // Other ENDPOINT constants can be added here

    val APP_CACHE_DIR: String by lazy {
        val context = KotsuneApplication.instance
        // Primary choice is the app's cache directory
        val cacheDir = context.cacheDir

        // Ensure the directory exists
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        cacheDir.absolutePath
    }
}