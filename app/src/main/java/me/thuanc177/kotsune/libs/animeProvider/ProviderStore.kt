package me.thuanc177.kotsune.libs.animeProvider

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import me.thuanc177.kotsune.KotsuneApplication

class ProviderStore(
    private val storeType: String,
    private val providerName: String = "",
    private val databasePath: String = ""
) {
    private val memoryStore = mutableMapOf<String, String>()
    private val dbHelper = if (storeType == "persistent") DatabaseHelper(databasePath) else null

    fun set(key: String, value: String) {
        if (storeType == "memory") {
            memoryStore[key] = value
        } else {
            val db = dbHelper!!.writableDatabase
            val values = ContentValues().apply {
                put("provider", providerName)
                put("key", key)
                put("value", value)
            }
            db.insertWithOnConflict("provider_store", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun get(key: String, defaultValue: String = ""): String {
        if (storeType == "memory") {
            return memoryStore[key] ?: defaultValue
        } else {
            val db = dbHelper!!.readableDatabase
            val cursor = db.query(
                "provider_store",
                arrayOf("value"),
                "provider = ? AND key = ?",
                arrayOf(providerName, key),
                null, null, null
            )

            val result = if (cursor.moveToFirst()) cursor.getString(0) else defaultValue
            cursor.close()
            return result
        }
    }

    private class DatabaseHelper(dbPath: String) : SQLiteOpenHelper(
        KotsuneApplication.instance, dbPath, null, 1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE provider_store (
                    provider TEXT,
                    `key` TEXT,
                    value TEXT,
                    PRIMARY KEY (provider, `key`)
                )
            """)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS provider_store")
            onCreate(db)
        }
    }
}