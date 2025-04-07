package me.thuanc177.kotsune.libs.common

interface Session {
    fun updateHeaders(headers: Map<String, String>)
    fun get(url: String, headers: Map<String, String>? = null): String
    fun post(url: String, data: Map<String, Any>? = null, headers: Map<String, String>? = null): String
}