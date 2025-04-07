package me.thuanc177.kotsune.libs.animeProvider

import java.nio.charset.StandardCharsets

/**
 * Utility functions for anime providers
 */
object Utils {
    // Dictionary to map hex values to characters
    private val hexToChar = mapOf(
        "01" to "9",
        "08" to "0",
        "05" to "=",
        "0a" to "2",
        "0b" to "3",
        "0c" to "4",
        "07" to "?",
        "00" to "8",
        "5c" to "d",
        "0f" to "7",
        "5e" to "f",
        "17" to "/",
        "54" to "l",
        "09" to "1",
        "48" to "p",
        "4f" to "w",
        "0e" to "6",
        "5b" to "c",
        "5d" to "e",
        "0d" to "5",
        "53" to "k",
        "1e" to "&",
        "5a" to "b",
        "59" to "a",
        "4a" to "r",
        "4c" to "t",
        "4e" to "v",
        "57" to "o",
        "51" to "i"
    )

    /**
     * Assigns random quality values to stream links in a cyclic pattern
     *
     * @param links List of episode stream maps
     * @return List of episode streams with quality values added
     */
    fun giveRandomQuality(links: List<Map<String, Any>>): List<Map<String, Any>> {
        val qualities = listOf("1080", "720", "480", "360")
        var qualityIndex = 0

        return links.map { episodeStream ->
            val result = episodeStream.toMutableMap()
            result["quality"] = qualities[qualityIndex]
            qualityIndex = (qualityIndex + 1) % qualities.size
            result
        }
    }

    /**
     * Performs a symmetric XOR operation with a single-digit password
     *
     * @param password The integer password to use for XOR
     * @param target The hex string to decrypt
     * @return Decrypted UTF-8 string
     */
    fun oneDigitSymmetricXor(password: Int, target: String): String {
        val bytes = target.chunked(2)
            .map { it.toInt(16) xor password }
            .map { it.toByte() }
            .toByteArray()

        return String(bytes, StandardCharsets.UTF_8)
    }

    /**
     * Decodes a hex string using the predefined mapping
     * Some anime sources encrypt URLs into hex codes and this function decrypts them
     *
     * @param hexString The hex string to decode
     * @return Decoded string
     */
    fun decodeHexString(hexString: String): String {
        // Split the hex string into pairs of characters
        val hexPairs = hexString.chunked(2)

        // Decode each hex pair
        val decodedChars = hexPairs.map { pair ->
            hexToChar.getOrDefault(pair.lowercase(), pair)
        }

        return decodedChars.joinToString("")
    }
}