package com.example.voicememouploader

import android.os.Handler
import android.os.Looper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.IOException

class UploadService(
    private var serverHostOrUrl: String = "https://your-tailnet-name.ts.net",
    private var port: Int = 443,
    private var uploadPath: String = "/webhook/upload-voice-memo"
) {

    private val client: OkHttpClient
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    fun uploadMemos(
        memos: List<VoiceMemo>,
        onProgress: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val baseUrl = buildBaseUrl(serverHostOrUrl, port)
            val normalizedPath = if (uploadPath.startsWith("/")) uploadPath else "/$uploadPath"
            val uploadUrl = "$baseUrl$normalizedPath"

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
                postError(onError, "Upload error: No readable audio files found in selection")
                return
            }

            val requestBody = multipartBuilder.build()
            val request = Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val details = e.message ?: e.javaClass.simpleName
                    postError(onError, "Network error: $details; endpoint=$uploadUrl")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val responseText = it.body?.string().orEmpty().take(500)
                        if (!it.isSuccessful) {
                            postError(
                                onError,
                                "Upload failed: HTTP ${it.code} ${it.message}; endpoint=$uploadUrl; response=$responseText"
                            )
                            return
                        }
                        postProgress(onProgress, "Upload successful: $filesAdded file(s). Response: $responseText")
                    }
                }
            })
        } catch (e: Exception) {
            val details = e.message ?: e.javaClass.simpleName
            postError(onError, "Upload error: $details (${e.javaClass.simpleName})")
        }
    }

    private fun buildBaseUrl(hostOrUrl: String, port: Int): String {
        val trimmed = hostOrUrl.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed.trimEnd('/')
        } else {
            val scheme = if (port == 443) "https" else "http"
            "$scheme://$trimmed:$port"
        }
    }

    private fun postProgress(onProgress: (String) -> Unit, message: String) {
        mainHandler.post { onProgress(message) }
    }

    private fun postError(onError: (String) -> Unit, message: String) {
        mainHandler.post { onError(message) }
    }

    fun pingEndpoint(onResult: (String) -> Unit) {
        val baseUrl = buildBaseUrl(serverHostOrUrl, port)
        val normalizedPath = if (uploadPath.startsWith("/")) uploadPath else "/$uploadPath"
        val url = "$baseUrl$normalizedPath"

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val details = e.message ?: e.javaClass.simpleName
                postProgress(onResult, "Ping failed: $details")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val preview = it.body?.string().orEmpty().take(120)
                    postProgress(onResult, "Ping: HTTP ${it.code}. Body: $preview")
                }
            }
        })
    }

    fun updateServerConfig(hostOrUrl: String, port: Int, uploadPath: String) {
        this.serverHostOrUrl = hostOrUrl
        this.port = port
        this.uploadPath = uploadPath
    }
}
