package com.example.data.giphy

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GiphyGif(
    val id: String,
    val title: String,
    val previewUrl: String,
    val fullUrl: String,
    val width: Int = 200,
    val height: Int = 200
) {
    val aspectRatio: Float
        get() = if (height > 0 && width > 0) {
            (width.toFloat() / height.toFloat()).coerceIn(0.65f, 1.85f)
        } else {
            1.2f
        }
}

sealed class GiphyResult {
    data object MissingApiKey : GiphyResult()
    data class Success(val gifs: List<GiphyGif>) : GiphyResult()
    data class Error(val message: String) : GiphyResult()
}

/**
 * Real Giphy REST API v1 client authenticated via `BuildConfig.GIPHY_API_KEY`
 * (configured via the Secrets panel in AI Studio).
 */
object GiphyRepository {

    private const val BASE_URL = "https://api.giphy.com/v1/gifs"
    private const val PLACEHOLDER_VALUE = "GIPHY_API_KEY_DEFAULT_VALUE"

    fun hasValidApiKey(): Boolean {
        val key = BuildConfig.GIPHY_API_KEY.trim()
        return key.isNotEmpty() && key != PLACEHOLDER_VALUE
    }

    suspend fun fetchTrending(limit: Int = 24): GiphyResult = withContext(Dispatchers.IO) {
        if (!hasValidApiKey()) return@withContext GiphyResult.MissingApiKey
        val apiKey = URLEncoder.encode(BuildConfig.GIPHY_API_KEY.trim(), "UTF-8")
        val endpoint = "$BASE_URL/trending?api_key=$apiKey&limit=$limit&rating=pg-13&bundle=messaging_non_clips"
        executeRequest(endpoint)
    }

    suspend fun searchGifs(query: String, limit: Int = 24): GiphyResult = withContext(Dispatchers.IO) {
        if (!hasValidApiKey()) return@withContext GiphyResult.MissingApiKey
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext fetchTrending(limit)
        val apiKey = URLEncoder.encode(BuildConfig.GIPHY_API_KEY.trim(), "UTF-8")
        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        val endpoint = "$BASE_URL/search?api_key=$apiKey&q=$encodedQuery&limit=$limit&rating=pg-13&lang=en&bundle=messaging_non_clips"
        executeRequest(endpoint)
    }

    private fun executeRequest(endpoint: String): GiphyResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            }.orEmpty()

            if (code == 401 || code == 403) {
                return GiphyResult.Error("Giphy rejected the API key (HTTP $code). Verify GIPHY_API_KEY in AI Studio Secrets.")
            }
            if (code == 429) {
                return GiphyResult.Error("Giphy rate limit reached. Please try again in a moment.")
            }
            if (code !in 200..299) {
                return GiphyResult.Error("Could not reach Giphy (HTTP $code).")
            }

            val root = JSONObject(body)
            val dataArray = root.optJSONArray("data") ?: return GiphyResult.Success(emptyList())
            val results = ArrayList<GiphyGif>(dataArray.length())

            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val id = item.optString("id").ifBlank { continue }
                val title = item.optString("title").ifBlank { "GIF" }
                val images = item.optJSONObject("images") ?: continue

                val fixedWidth = images.optJSONObject("fixed_width")
                val downsized = images.optJSONObject("downsized_medium")
                    ?: images.optJSONObject("downsized")
                val original = images.optJSONObject("original")

                val previewUrl = fixedWidth?.optString("url").orEmpty()
                    .ifBlank { downsized?.optString("url").orEmpty() }
                    .ifBlank { original?.optString("url").orEmpty() }
                val fullUrl = downsized?.optString("url").orEmpty()
                    .ifBlank { fixedWidth?.optString("url").orEmpty() }
                    .ifBlank { original?.optString("url").orEmpty() }

                if (previewUrl.isBlank() || fullUrl.isBlank()) continue

                val width = fixedWidth?.optString("width")?.toIntOrNull()
                    ?: downsized?.optString("width")?.toIntOrNull()
                    ?: 200
                val height = fixedWidth?.optString("height")?.toIntOrNull()
                    ?: downsized?.optString("height")?.toIntOrNull()
                    ?: 200

                results.add(
                    GiphyGif(
                        id = id,
                        title = title,
                        previewUrl = previewUrl,
                        fullUrl = fullUrl,
                        width = width,
                        height = height
                    )
                )
            }
            GiphyResult.Success(results)
        } catch (e: Exception) {
            GiphyResult.Error(e.message ?: "Network error while loading GIFs.")
        } finally {
            conn?.disconnect()
        }
    }
}
