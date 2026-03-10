package com.example.voicememouploader

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
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

            var filesAdded = 0
            for (memo in memos) {
                val file = File(memo.path)
                if (file.exists() && file.isFile) {
                    val requestBody = file.asRequestBody("audio/mpeg".toMediaType())
                    multipartBuilder.addFormDataPart("files", memo.title, requestBody)
                    filesAdded++
                }
            }

            if (filesAdded == 0) {
                onError("Upload error: No readable audio files found in selection")
                return
            }

            val requestBody = multipartBuilder.build()

            val request = Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty().take(500)
                if (!response.isSuccessful) {
                    onError("Upload failed: HTTP ${response.code} ${response.message}; endpoint=$uploadUrl; response=$responseText")
                    return@use
                }

                onProgress("Upload successful: $filesAdded file(s). Response: $responseText")
            }

        } catch (e: IOException) {
            val details = e.message ?: e.javaClass.simpleName
            onError("Network error: $details; endpoint=http://$tailscaleIp:$port/api/voice-memos/upload")
        } catch (e: Exception) {
            val details = e.message ?: e.javaClass.simpleName
            onError("Upload error: $details (${e.javaClass.simpleName})")
        }
    }

    fun updateServerConfig(ipAddress: String, port: Int) {
        // Allow runtime configuration of server
    }
}
