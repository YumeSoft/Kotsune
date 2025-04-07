package me.thuanc177.kotsune.libs.common

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

class StandardSession : Session {
    private val client = OkHttpClient()
    private val headers = mutableMapOf<String, String>()

    override fun updateHeaders(headers: Map<String, String>) {
        this.headers.putAll(headers)
    }

    override fun get(url: String, headers: Map<String, String>?): String {
        val combinedHeaders = this.headers + (headers ?: emptyMap())
        val request = Request.Builder()
            .url(url)
            .apply { combinedHeaders.forEach { (name, value) -> addHeader(name, value) } }
            .build()

        val response = client.newCall(request).execute()
        return response.body?.string() ?: ""
    }

    override fun post(url: String, data: Map<String, Any>?, headers: Map<String, String>?): String {
        val combinedHeaders = this.headers + (headers ?: emptyMap())
        val jsonData = data?.let { JSONObject(it).toString() } ?: ""

        val request = Request.Builder()
            .url(url)
            .apply { combinedHeaders.forEach { (name, value) -> addHeader(name, value) } }
            .post(jsonData.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        return response.body?.string() ?: ""
    }
}