package com.example.voicememouploader

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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

    fun checkForUpdate(currentVersion: String, channel: String = "main"): Result<UpdateInfo?> {
        return try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "VoiceMemoUploader-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) {
                        return Result.failure(IOException("Update feed not found (404)."))
                    }
                    return Result.failure(IOException("Update check failed: HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return Result.success(null)
                val releases = JSONArray(body)
                if (releases.length() == 0) return Result.success(null)

                val target = pickChannelRelease(releases, channel)
                    ?: return Result.success(null)

                val tagRaw = target.optString("tag_name", "")
                val tagVersion = extractVersion(tagRaw)
                if (tagVersion.isBlank()) return Result.success(null)

                val notes = target.optString("body", "")
                val htmlUrl = target.optString("html_url", "")

                var apkUrl: String? = null
                val assets = target.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "").lowercase()
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                            break
                        }
                    }
                }

                Result.success(
                    UpdateInfo(
                        versionName = tagVersion,
                        notes = notes,
                        apkUrl = apkUrl,
                        releaseUrl = htmlUrl
                    )
                )
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            Result.failure(IOException("Update check error: $msg", e))
        }
    }

    private fun pickChannelRelease(releases: JSONArray, channel: String): JSONObject? {
        val normalized = channel.lowercase()
        val candidates = mutableListOf<JSONObject>()

        for (i in 0 until releases.length()) {
            val rel = releases.getJSONObject(i)
            val tag = rel.optString("tag_name", "").lowercase()
            val isPrerelease = rel.optBoolean("prerelease", false)

            if (normalized == "dev") {
                if (isPrerelease || tag.startsWith("dev-")) candidates.add(rel)
            } else {
                if (!isPrerelease && (tag.startsWith("v") || tag.startsWith("main-"))) candidates.add(rel)
            }
        }

        if (candidates.isEmpty()) return null

        // GitHub API order can be surprising; choose newest by publishedAt.
        return candidates.maxByOrNull { it.optString("published_at", "") }
    }

    private fun extractVersion(tag: String): String {
        val clean = tag.removePrefix("v")
        val regex = Regex("(\\d+\\.\\d+\\.\\d+)")
        return regex.find(clean)?.value ?: clean
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
