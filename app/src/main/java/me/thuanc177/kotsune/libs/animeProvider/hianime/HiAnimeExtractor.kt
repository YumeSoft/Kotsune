package me.thuanc177.kotsune.libs.animeProvider.hianime

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.nio.charset.StandardCharsets

class HiAnimeExtractor(private val client: OkHttpClient) {

    // Constants
    private val megacloud = mapOf(
        "script" to "https://megacloud.tv/js/player/a/prod/e1-player.min.js?v=",
        "sources" to "https://megacloud.tv/embed-2/ajax/e-1/getSources?id="
    )

    class HiAnimeError(message: String, val context: String, val statusCode: Int) :
        Exception("$context: $message (Status: $statusCode)")

    inner class MegaCloud {

        fun extract(videoUrl: String): Map<String, Any> {
            try {
                val extractedData = mutableMapOf<String, Any>(
                    "tracks" to emptyList<Any>(),
                    "intro" to mapOf("start" to 0, "end" to 0),
                    "outro" to mapOf("start" to 0, "end" to 0),
                    "sources" to emptyList<Any>()
                )

                val videoId = videoUrl.split("/").last().split("?")[0]
                val request = Request.Builder()
                    .url(megacloud["sources"] + videoId)
                    .header("Accept", "*/*")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", videoUrl)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                val srcsData = JSONObject(responseBody)

                if (!srcsData.has("sources")) {
                    throw HiAnimeError("Url may have an invalid video id", "getAnimeEpisodeSources", 400)
                }

                val encryptedString = srcsData.getString("sources")
                if (!srcsData.getBoolean("encrypted") && encryptedString.startsWith("[")) {
                    val sourcesArray = JSONArray(encryptedString)
                    val sources = mutableListOf<Map<String, String>>()

                    for (i in 0 until sourcesArray.length()) {
                        val source = sourcesArray.getJSONObject(i)
                        sources.add(mapOf(
                            "url" to source.getString("file"),
                            "type" to source.getString("type")
                        ))
                    }

                    extractedData["intro"] = srcsData.optJSONObject("intro")?.let {
                        mapOf("start" to it.optInt("start", 0), "end" to it.optInt("end", 0))
                    } ?: mapOf("start" to 0, "end" to 0)

                    extractedData["outro"] = srcsData.optJSONObject("outro")?.let {
                        mapOf("start" to it.optInt("start", 0), "end" to it.optInt("end", 0))
                    } ?: mapOf("start" to 0, "end" to 0)

                    extractedData["tracks"] = srcsData.optJSONArray("tracks")?.let {
                        MutableList(it.length()) { i -> it.getJSONObject(i).toMap() }
                    } ?: emptyList<Any>()

                    extractedData["sources"] = sources
                    return extractedData
                }

                // Fetch decryption script
                val scriptRequest = Request.Builder()
                    .url(megacloud["script"] + Instant.now().toEpochMilli())
                    .build()

                val scriptResponse = client.newCall(scriptRequest).execute()
                val scriptText = scriptResponse.body?.string() ?: ""

                if (scriptText.isEmpty()) {
                    throw HiAnimeError(
                        "Couldn't fetch script to decrypt resource",
                        "getAnimeEpisodeSources",
                        500
                    )
                }

                val vars = extractVariables(scriptText)
                if (vars.isEmpty()) {
                    throw Exception("Can't find variables. Perhaps the extractor is outdated.")
                }

                val (secret, encryptedSource) = getSecret(encryptedString, vars)
                val decrypted = decrypt(encryptedSource, secret)

                try {
                    val sourcesObj = JSONArray(decrypted)
                    val sources = mutableListOf<Map<String, String>>()

                    for (i in 0 until sourcesObj.length()) {
                        val source = sourcesObj.getJSONObject(i)
                        sources.add(mapOf(
                            "url" to source.getString("file"),
                            "type" to source.getString("type")
                        ))
                    }

                    extractedData["intro"] = srcsData.optJSONObject("intro")?.let {
                        mapOf("start" to it.optInt("start", 0), "end" to it.optInt("end", 0))
                    } ?: mapOf("start" to 0, "end" to 0)

                    extractedData["outro"] = srcsData.optJSONObject("outro")?.let {
                        mapOf("start" to it.optInt("start", 0), "end" to it.optInt("end", 0))
                    } ?: mapOf("start" to 0, "end" to 0)

                    extractedData["tracks"] = srcsData.optJSONArray("tracks")?.let {
                        MutableList(it.length()) { i -> it.getJSONObject(i).toMap() }
                    } ?: emptyList<Any>()

                    extractedData["sources"] = sources
                    return extractedData
                } catch (e: Exception) {
                    throw HiAnimeError(
                        "Failed to decrypt resource",
                        "getAnimeEpisodeSources",
                        500
                    )
                }
            } catch (e: Exception) {
                throw e
            }
        }

        private fun extractVariables(text: String): List<List<Int>> {
            val regex = Regex("""case\s*0x[0-9a-f]+:(?![^;]*=partKey)\s*\w+\s*=\s*(\w+)\s*,\s*\w+\s*=\s*(\w+);""")
            val matches = regex.findAll(text)
            val vars = mutableListOf<List<Int>>()

            for (match in matches) {
                try {
                    val key1 = matchingKey(match.groupValues[1], text)
                    val key2 = matchingKey(match.groupValues[2], text)
                    vars.add(listOf(key1.toInt(16), key2.toInt(16)))
                } catch (e: NumberFormatException) {
                    continue
                }
            }

            return vars
        }

        private fun getSecret(encryptedString: String, values: List<List<Int>>): Pair<String, String> {
            val secret = StringBuilder()
            val encryptedSourceArray = encryptedString.toCharArray()
            var currentIndex = 0

            for ((start, length) in values) {
                val adjustedStart = start + currentIndex
                val end = adjustedStart + length

                secret.append(encryptedString.substring(adjustedStart, end))
                for (i in adjustedStart until end) {
                    encryptedSourceArray[i] = '\u0000'
                }

                currentIndex += length
            }

            val encryptedSource = String(encryptedSourceArray).replace("\u0000", "")
            return Pair(secret.toString(), encryptedSource)
        }

        private fun decrypt(encrypted: String, keyOrSecret: String, maybeIv: String = ""): String {
            return if (maybeIv.isNotEmpty()) {
                val key = keyOrSecret.toByteArray()
                val iv = maybeIv.toByteArray()
                decryptWithKeyAndIv(encrypted, key, iv)
            } else {
                // Decode the Base64 string
                val cypher = Base64.decode(encrypted, Base64.DEFAULT)

                // Extract the salt from the cypher text
                val salt = cypher.copyOfRange(8, 16)

                // Combine the key_or_secret with the salt
                val password = keyOrSecret.toByteArray() + salt

                // Generate MD5 hashes
                val md5Hashes = mutableListOf<ByteArray>()
                var digest = password

                for (i in 0 until 3) {
                    val md = MessageDigest.getInstance("MD5")
                    md.update(digest)
                    md5Hashes.add(md.digest())
                    digest = md5Hashes.last() + password
                }

                // Derive the key and IV
                val key = md5Hashes[0] + md5Hashes[1]
                val iv = md5Hashes[2]

                // Extract the encrypted contents
                val contents = cypher.copyOfRange(16, cypher.size)

                // Initialize the AES decipher
                val decipher = Cipher.getInstance("AES/GCM/NoPadding")
                val secretKeySpec = SecretKeySpec(key, 0, 32, "AES")
                val gcmParameterSpec = GCMParameterSpec(128, iv, 0, 16)

                decipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec)
                val decrypted = String(decipher.doFinal(contents), StandardCharsets.UTF_8)

                // Remove any padding (PKCS#7)
                val pad = decrypted.last().code
                decrypted.substring(0, decrypted.length - pad)
            }
        }

        private fun decryptWithKeyAndIv(data: String, key: ByteArray, iv: ByteArray): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val secretKeySpec = SecretKeySpec(key, "AES")
            val gcmParameterSpec = GCMParameterSpec(128, iv)

            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec)
            return String(cipher.doFinal(data.toByteArray()))
        }

        private fun matchingKey(value: String, script: String): String {
            val regex = Regex(""",$value=((?:0x)?[0-9a-fA-F]+)""")
            val match = regex.find(script)
            return match?.groupValues?.get(1)?.replace("0x", "")
                ?: throw Exception("Failed to match the key")
        }
    }
}

// Extension function to convert JSONObject to Map
private fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    val keysIterator = this.keys()

    while (keysIterator.hasNext()) {
        val key = keysIterator.next()
        var value = this.get(key)

        when (value) {
            is JSONObject -> value = value.toMap()
            is JSONArray -> {
                val list = mutableListOf<Any>()
                for (i in 0 until value.length()) {
                    list.add(when (val element = value.get(i)) {
                        is JSONObject -> element.toMap()
                        else -> element
                    })
                }
                value = list
            }
        }

        map[key] = value
    }

    return map
}