package com.example.voicememouploader

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String?,
    val releaseUrl: String
)

class UpdateService(
    private val owner: String = "clawthor0",
    private val repo: String = "voice-memo-uploader"
) {
    private val client = OkHttpClient()

    fun checkForUpdate(currentVersion: String): Result<UpdateInfo?> {
        return try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("Update check failed: ${response.code}"))
                }

                val body = response.body?.string() ?: return Result.success(null)
                val json = JSONObject(body)

                val tag = json.optString("tag_name", "").removePrefix("v")
                val notes = json.optString("body", "")
                val htmlUrl = json.optString("html_url", "")

                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "").lowercase()
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", null)
                            break
                        }
                    }
                }

                if (tag.isBlank()) return Result.success(null)

                val isNewer = compareVersions(tag, currentVersion) > 0
                if (!isNewer) return Result.success(null)

                Result.success(
                    UpdateInfo(
                        versionName = tag,
                        notes = notes,
                        apkUrl = apkUrl,
                        releaseUrl = htmlUrl
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val aParts = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bParts = b.split(".").map { it.toIntOrNull() ?: 0 }
        val max = maxOf(aParts.size, bParts.size)

        for (i in 0 until max) {
            val av = aParts.getOrElse(i) { 0 }
            val bv = bParts.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }
}
