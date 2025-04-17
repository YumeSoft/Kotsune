package me.thuanc177.kotsune.libs.common

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import me.thuanc177.kotsune.MainActivity

class CachedRequestsSession(
    private val databasePath: String,
    private val maxLifetimeSeconds: Int = 259200
) : Session {
    private val standardSession = StandardSession()
    private val dbHelper = CacheDbHelper(databasePath)

    override fun updateHeaders(headers: Map<String, String>) {
        standardSession.updateHeaders(headers)
    }

    override fun get(url: String, headers: Map<String, String>?): String {
        val cachedResponse = getCachedResponse(url)
        if (cachedResponse != null) {
            return cachedResponse
        }

        val response = standardSession.get(url, headers)
        cacheResponse(url, response)
        return response
    }

    override fun post(url: String, data: Map<String, Any>?, headers: Map<String, String>?): String {
        return standardSession.post(url, data, headers)
    }

    private fun getCachedResponse(url: String): String? {
        val db = dbHelper.readableDatabase
        val currentTime = System.currentTimeMillis() / 1000

        val cursor = db.query(
            "requests_cache",
            arrayOf("response", "timestamp"),
            "url = ?",
            arrayOf(url),
            null, null, null
        )

        return if (cursor.moveToFirst()) {
            val response = cursor.getString(0)
            val timestamp = cursor.getLong(1)

            cursor.close()
            if (currentTime - timestamp <= maxLifetimeSeconds) response else null
        } else {
            cursor.close()
            null
        }
    }

    private fun cacheResponse(url: String, response: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("url", url)
            put("response", response)
            put("timestamp", System.currentTimeMillis() / 1000)
        }
        db.insertWithOnConflict("requests_cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private class CacheDbHelper(dbPath: String) : SQLiteOpenHelper(
        MainActivity.appContext, dbPath, null, 1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE requests_cache (
                    url TEXT PRIMARY KEY,
                    response TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS requests_cache")
            onCreate(db)
        }
    }
}