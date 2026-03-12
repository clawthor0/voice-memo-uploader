package com.example.voicememouploader

import android.os.Build
import android.os.Handler
import android.os.Looper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.File
import java.io.IOException

class UploadService(
    private var serverHostOrUrl: String = "",
    private var port: Int = 443,
    private var uploadPath: String = "/voice/webhook/upload-voice-memo",
    private var telemetryWebhookUrl: String = "",
    private var appVersion: String = "unknown",
    private var releaseChannel: String = "main"
) {

    private val client: OkHttpClient
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recentLogs = ArrayDeque<String>()

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
            if (serverHostOrUrl.isBlank()) {
                postError(onError, "Upload error: Server URL is empty. Set it in Server Config first.")
                return
            }
            val baseUrl = buildBaseUrl(serverHostOrUrl, port)
            val normalizedPath = normalizePath(uploadPath)
            val uploadUrl = "$baseUrl$normalizedPath"

            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)

            var filesAdded = 0
            for (memo in memos) {
                val file = File(memo.path)
                if (file.exists() && file.isFile) {
                    val requestBody = file.asRequestBody("audio/mpeg".toMediaType())
                    val safeName = sanitizeFilename(memo.title)
                    multipartBuilder.addFormDataPart("files", safeName, requestBody)
                    filesAdded++
                }
            }

            if (filesAdded == 0) {
                postError(onError, "Upload error: No readable audio files found in selection")
                return
            }

            val requestBody = multipartBuilder.build()
            val request = Request.Builder().url(uploadUrl).post(requestBody).build()

            addLog("UPLOAD_START endpoint=$uploadUrl files=$filesAdded")

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val details = e.message ?: e.javaClass.simpleName
                    addLog("UPLOAD_FAIL network=$details")
                    reportFailure(
                        attemptedUrl = uploadUrl,
                        errorType = "network_failure",
                        errorMessage = details,
                        httpCode = null,
                        responseBody = null
                    )
                    postError(onError, "Network error: $details; endpoint=$uploadUrl")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val responseText = it.body?.string().orEmpty().take(500)
                        if (!it.isSuccessful) {
                            addLog("UPLOAD_FAIL http=${it.code} body=${responseText.take(120)}")
                            reportFailure(
                                attemptedUrl = uploadUrl,
                                errorType = "http_failure",
                                errorMessage = it.message,
                                httpCode = it.code,
                                responseBody = responseText
                            )
                            postError(
                                onError,
                                "Upload failed: HTTP ${it.code} ${it.message}; endpoint=$uploadUrl; response=$responseText"
                            )
                            return
                        }
                        addLog("UPLOAD_OK files=$filesAdded")
                        postProgress(onProgress, "Upload successful: $filesAdded file(s). Response: $responseText")
                    }
                }
            })
        } catch (e: Exception) {
            val details = e.message ?: e.javaClass.simpleName
            addLog("UPLOAD_EXCEPTION $details")
            reportFailure(
                attemptedUrl = "(construction)",
                errorType = "client_exception",
                errorMessage = details,
                httpCode = null,
                responseBody = null
            )
            postError(onError, "Upload error: $details (${e.javaClass.simpleName})")
        }
    }

    fun pingEndpoint(onResult: (String) -> Unit) {
        if (serverHostOrUrl.isBlank()) {
            postProgress(onResult, "Ping failed: Server URL is empty")
            return
        }

        val baseUrl = buildBaseUrl(serverHostOrUrl, port)
        val normalizedPath = normalizePath(uploadPath)
        val url = "$baseUrl$normalizedPath"

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val details = e.message ?: e.javaClass.simpleName
                addLog("PING_FAIL network=$details")
                reportFailure(url, "ping_network_failure", details, null, null)
                postProgress(onResult, "Ping failed: $details")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val preview = it.body?.string().orEmpty().take(120)
                    if (it.code >= 400) {
                        addLog("PING_FAIL http=${it.code}")
                        reportFailure(url, "ping_http_failure", it.message, it.code, preview)
                    } else {
                        addLog("PING_OK http=${it.code}")
                    }
                    postProgress(onResult, "Ping: HTTP ${it.code}. Body: $preview")
                }
            }
        })
    }

    fun updateServerConfig(
        hostOrUrl: String,
        port: Int,
        uploadPath: String,
        telemetryWebhookUrl: String,
        appVersion: String,
        releaseChannel: String
    ) {
        this.serverHostOrUrl = hostOrUrl
        this.port = port
        this.uploadPath = uploadPath
        this.telemetryWebhookUrl = telemetryWebhookUrl
        this.appVersion = appVersion
        this.releaseChannel = releaseChannel
    }

    private fun buildBaseUrl(hostOrUrl: String, port: Int): String {
        val trimmed = hostOrUrl.trim().replace(" ", "")
        val defaultScheme = if (port == 443) "https" else "http"

        return try {
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "$defaultScheme://$trimmed"
            val uri = java.net.URI(withScheme)
            val scheme = uri.scheme ?: defaultScheme
            val host = uri.host ?: uri.authority?.substringBefore(':') ?: trimmed
            val explicitPort = if (uri.port != -1) uri.port else port
            "$scheme://$host:$explicitPort"
        } catch (_: Exception) {
            "$defaultScheme://$trimmed:$port"
        }
    }

    private fun normalizePath(path: String): String {
        val noSpaces = path.trim().replace(" ", "")
        val withLeading = if (noSpaces.startsWith("/")) noSpaces else "/$noSpaces"
        return withLeading.replace(Regex("/{2,}"), "/")
    }

    private fun postProgress(onProgress: (String) -> Unit, message: String) {
        mainHandler.post { onProgress(message) }
    }

    private fun postError(onError: (String) -> Unit, message: String) {
        mainHandler.post { onError(message) }
    }

    private fun sanitizeFilename(name: String): String {
        return name.trim()
            .replace(" ", "_")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
    }

    private fun addLog(message: String) {
        val line = "${System.currentTimeMillis()} $message"
        recentLogs.addLast(line)
        while (recentLogs.size > 20) recentLogs.removeFirst()
    }

    private fun reportFailure(
        attemptedUrl: String,
        errorType: String,
        errorMessage: String?,
        httpCode: Int?,
        responseBody: String?
    ) {
        if (telemetryWebhookUrl.isBlank()) return

        try {
            val payload = JSONObject().apply {
                put("event", "voice_memo_upload_failure")
                put("message", "Looking into this error")
                put("timestamp", System.currentTimeMillis())
                put("appVersion", appVersion)
                put("releaseChannel", releaseChannel)
                put("attemptedUrl", attemptedUrl)
                put("errorType", errorType)
                put("errorMessage", errorMessage ?: "")
                put("httpCode", httpCode ?: JSONObject.NULL)
                put("responseBody", responseBody ?: JSONObject.NULL)
                put("device", "${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
                put("recentLogs", recentLogs.joinToString("\n"))
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(telemetryWebhookUrl.trim()).post(body).build()
            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    addLog("TELEMETRY_FAIL ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    addLog("TELEMETRY_OK")
                }
            })
        } catch (e: Exception) {
            addLog("TELEMETRY_EXCEPTION ${e.message}")
        }
    }
}
