package com.example.voicememouploader

import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.IOException

class UploadService(
    private val tailscaleIp: String = "100.100.100.100",
    private val port: Int = 8080
) {

    private val client: OkHttpClient

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    fun uploadMemos(
        memos: List<VoiceMemo>,
        onProgress: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val baseUrl = "http://$tailscaleIp:$port"
            val uploadUrl = "$baseUrl/api/voice-memos/upload"

            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)

            // Add each memo file
            for (memo in memos) {
                val file = File(memo.path)
                if (file.exists()) {
                    val requestBody = file.asRequestBody("audio/mpeg".toMediaType())
                    multipartBuilder.addFormDataPart(
                        "files",
                        memo.title,
                        requestBody
                    )
                }
            }

            val requestBody = multipartBuilder.build()

            val request = Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onError("Upload failed: ${response.code} - ${response.message}")
                    return@use
                }

                val responseBody = response.body?.string() ?: ""
                onProgress("Upload successful: $responseBody")
            }

        } catch (e: IOException) {
            onError("Network error: ${e.message}")
        } catch (e: Exception) {
            onError("Upload error: ${e.message}")
        }
    }

    fun updateServerConfig(ipAddress: String, port: Int) {
        // Allow runtime configuration of server
    }
}
