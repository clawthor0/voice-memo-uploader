package com.example.voicememouploader

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var mediaStoreRepository: MediaStoreRepository
    private lateinit var uploadService: UploadService
    private lateinit var updateService: UpdateService
    private lateinit var recorderHelper: RecorderHelper

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaStoreRepository = MediaStoreRepository(this)
        uploadService = UploadService()
        updateService = UpdateService()
        recorderHelper = RecorderHelper(this)

        val permissionsToRequest = mutableListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissions.launch(permissionsToRequest.toTypedArray())

        setContent {
            val versionName = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
            } catch (_: Exception) {
                "unknown"
            }

            VoiceMemoUploaderApp(
                mediaStoreRepository = mediaStoreRepository,
                uploadService = uploadService,
                updateService = updateService,
                recorderHelper = recorderHelper,
                currentVersion = versionName
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceMemoUploaderApp(
    mediaStoreRepository: MediaStoreRepository,
    uploadService: UploadService,
    updateService: UpdateService,
    recorderHelper: RecorderHelper,
    currentVersion: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("voice_memo_uploader", 0) }

    var memos by remember { mutableStateOf<List<VoiceMemo>>(emptyList()) }
    var selectedMemos by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var status by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var showServerConfig by remember { mutableStateOf(false) }
    var serverIp by remember { mutableStateOf(prefs.getString("server_url", "") ?: "") }
    var serverPort by remember { mutableStateOf(prefs.getString("server_port", "443") ?: "443") }
    var uploadPath by remember { mutableStateOf(prefs.getString("upload_path", "/voice/webhook/upload-voice-memo") ?: "/voice/webhook/upload-voice-memo") }
    var telemetryWebhookUrl by remember { mutableStateOf(prefs.getString("telemetry_webhook_url", "") ?: "") }

    var recordingsOnly by remember { mutableStateOf(true) }
    var minDurationSeconds by remember { mutableStateOf("10") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var folderExpanded by remember { mutableStateOf(false) }
    val topFolders by remember { mutableStateOf(mediaStoreRepository.getTopLevelFolders()) }

    var isRecording by remember { mutableStateOf(false) }
    var recordedPath by remember { mutableStateOf<String?>(null) }
    var recordingName by remember { mutableStateOf("voice memo") }
    var autoUpload by remember { mutableStateOf(true) }
    var autoSummarize by remember { mutableStateOf(true) }

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateStatus by remember { mutableStateOf("") }
    var releaseChannel by remember { mutableStateOf("main") }
    var releaseChannelExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(serverIp, serverPort, uploadPath, telemetryWebhookUrl) {
        prefs.edit()
            .putString("server_url", serverIp)
            .putString("server_port", serverPort)
            .putString("upload_path", uploadPath)
            .putString("telemetry_webhook_url", telemetryWebhookUrl)
            .apply()
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Voice Memo Uploader",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Quick Record", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = recordingName,
                    onValueChange = { recordingName = it },
                    label = { Text("Recording name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (!isRecording) {
                            val started = recorderHelper.startRecording(recordingName)
                            started.onSuccess {
                                isRecording = true
                                status = "Recording started..."
                            }.onFailure {
                                status = "Recording failed to start: ${it.message ?: it.javaClass.simpleName}"
                            }
                        } else {
                            val stopped = recorderHelper.stopRecording()
                            stopped.onSuccess { path ->
                                isRecording = false
                                recordedPath = path
                                status = "Recording captured. Review options, then Save."
                            }.onFailure {
                                isRecording = false
                                status = "Recording stop failed: ${it.message ?: it.javaClass.simpleName}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isRecording) "Stop Recording" else "Start Recording")
                }

                if (recordedPath != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoUpload, onCheckedChange = { autoUpload = it })
                        Text("Upload automatically")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoSummarize, onCheckedChange = { autoSummarize = it })
                        Text("Summarize automatically")
                    }
                    Button(
                        onClick = {
                            val path = recordedPath
                            if (path == null) {
                                status = "No recorded file found"
                                return@Button
                            }
                            if (!autoUpload && autoSummarize) {
                                status = "Summarize requires upload. Enable Upload or disable Summarize."
                                return@Button
                            }

                            val file = java.io.File(path)
                            if (!file.exists()) {
                                status = "Recorded file missing"
                                return@Button
                            }

                            if (autoUpload) {
                                val configuredPort = serverPort.toIntOrNull() ?: 443
                                uploadService.updateServerConfig(serverIp, configuredPort, uploadPath, telemetryWebhookUrl, currentVersion, releaseChannel)
                                val vm = VoiceMemo(
                                    id = -System.currentTimeMillis(),
                                    title = file.name,
                                    path = file.absolutePath,
                                    duration = 0L,
                                    dateAdded = System.currentTimeMillis() / 1000,
                                    size = file.length()
                                )
                                status = if (autoSummarize) {
                                    "Uploading recording (summary enabled)..."
                                } else {
                                    "Uploading recording..."
                                }
                                isUploading = true
                                uploadService.uploadMemos(
                                    listOf(vm),
                                    onProgress = { status = it; isUploading = false },
                                    onError = { status = it; isUploading = false }
                                )
                            } else {
                                status = "Saved locally only: ${file.name}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRecording
                    ) {
                        Text("Save")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Filters", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = recordingsOnly,
                        onCheckedChange = { recordingsOnly = it }
                    )
                    Text("Only recorder voice memos (exclude Slack/notifications)")
                }

                OutlinedTextField(
                    value = minDurationSeconds,
                    onValueChange = { minDurationSeconds = it.filter(Char::isDigit) },
                    label = { Text("Min duration (seconds)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = folderExpanded,
                    onExpandedChange = { folderExpanded = !folderExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedFolder ?: "All folders",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Top-level folder to scan") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = folderExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = folderExpanded,
                        onDismissRequest = { folderExpanded = false }
                    ) {
                        DropdownMenuItem(text = { Text("All folders") }, onClick = {
                            selectedFolder = null
                            folderExpanded = false
                        })
                        topFolders.forEach { folder ->
                            DropdownMenuItem(text = { Text(folder) }, onClick = {
                                selectedFolder = folder
                                folderExpanded = false
                            })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        status = "Scanning..."
                        val options = MediaStoreRepository.ScanOptions(
                            recordingsOnly = recordingsOnly,
                            topLevelFolder = selectedFolder,
                            minDurationMs = (minDurationSeconds.toLongOrNull() ?: 10L) * 1000
                        )
                        memos = mediaStoreRepository.getVoiceMemos(options)
                        selectedMemos = emptySet()
                        status = if (memos.isEmpty()) "No matching voice memos found" else "Found ${memos.size} voice memo(s)"
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isUploading
                ) { Text("Scan Voice Memos", fontSize = 16.sp) }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showServerConfig = !showServerConfig },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) { Text("Server Config") }

                if (showServerConfig) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = serverIp, onValueChange = { serverIp = it }, label = { Text("Server host or URL (https recommended)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = serverPort, onValueChange = { serverPort = it.filter(Char::isDigit) }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = uploadPath, onValueChange = { uploadPath = it }, label = { Text("Upload path (or webhook path)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = telemetryWebhookUrl,
                        onValueChange = { telemetryWebhookUrl = it },
                        label = { Text("Fallback telemetry webhook URL (OpenClaw)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val configuredPort = serverPort.toIntOrNull() ?: 443
                            uploadService.updateServerConfig(serverIp, configuredPort, uploadPath, telemetryWebhookUrl, currentVersion, releaseChannel)
                            uploadService.pingEndpoint { status = it }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ping Upload Endpoint") }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val base = serverIp.trim().ifBlank { "https://siemtestclient.tail8ca5.ts.net" }.trimEnd('/')
                        val dashUrl = if (base.contains("/voice")) "$base/dashboard" else "$base/voice/dashboard"
                        val i = Intent(context, DashboardActivity::class.java).apply {
                            putExtra("url", dashUrl)
                        }
                        context.startActivity(i)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open Voice Dashboard") }

                Spacer(modifier = Modifier.height(12.dp))
                if (status.isNotEmpty()) {
                    Text(
                        text = status,
                        color = if (isUploading) Color.Blue else Color.Black,
                        modifier = Modifier.fillMaxWidth().background(Color.LightGray).padding(12.dp)
                    )
                }

                if (memos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Memos (${selectedMemos.size}/${memos.size} selected)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)).padding(8.dp)) {
                        memos.forEach { memo ->
                            MemoListItem(
                                memo = memo,
                                isSelected = selectedMemos.contains(memo.id),
                                onSelectionChanged = { selected ->
                                    selectedMemos = if (selected) selectedMemos + memo.id else selectedMemos - memo.id
                                },
                                mediaStoreRepository = mediaStoreRepository
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = Color(0xFFE8F0FE),
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            text = "App Version: v$currentVersion",
                            color = Color(0xFF1A73E8),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = releaseChannelExpanded,
                            onExpandedChange = { releaseChannelExpanded = !releaseChannelExpanded }
                        ) {
                            OutlinedTextField(
                                value = releaseChannel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Release channel") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = releaseChannelExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = releaseChannelExpanded,
                                onDismissRequest = { releaseChannelExpanded = false }
                            ) {
                                DropdownMenuItem(text = { Text("main") }, onClick = {
                                    releaseChannel = "main"
                                    releaseChannelExpanded = false
                                })
                                DropdownMenuItem(text = { Text("dev") }, onClick = {
                                    releaseChannel = "dev"
                                    releaseChannelExpanded = false
                                })
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                isCheckingUpdates = true
                                updateStatus = "Checking $releaseChannel releases..."
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        updateService.checkForUpdate(currentVersion, releaseChannel)
                                    }
                                    result.onSuccess { info ->
                                        updateInfo = info
                                        updateStatus = if (info == null) {
                                            "No release found for channel '$releaseChannel'"
                                        } else {
                                            "Latest $releaseChannel release: v${info.versionName}"
                                        }
                                    }.onFailure {
                                        updateStatus = "Update check failed: ${it.message ?: it.javaClass.simpleName}"
                                    }
                                    isCheckingUpdates = false
                                }
                            },
                            enabled = !isCheckingUpdates,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isCheckingUpdates) "Checking..." else "Check Release Channel")
                        }

                        if (updateStatus.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(updateStatus, fontSize = 12.sp, color = Color.Gray)
                        }

                        updateInfo?.let { info ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Published on $releaseChannel: v${info.versionName}", fontWeight = FontWeight.SemiBold)
                            if (info.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(info.notes.take(180), fontSize = 12.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                info.apkUrl?.let { apk ->
                                    Button(onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apk))
                                        context.startActivity(intent)
                                    }) { Text("Download This Version") }
                                }
                                OutlinedButton(onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
                                    context.startActivity(intent)
                                }) { Text("Open Release") }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (selectedMemos.isNotEmpty()) {
                            isUploading = true
                            status = "Uploading..."
                            val configuredPort = serverPort.toIntOrNull() ?: 443
                            uploadService.updateServerConfig(serverIp, configuredPort, uploadPath, telemetryWebhookUrl, currentVersion, releaseChannel)
                            val memosToUpload = memos.filter { selectedMemos.contains(it.id) }
                            uploadService.uploadMemos(
                                memosToUpload,
                                onProgress = { status = it; isUploading = false },
                                onError = { status = it; isUploading = false }
                            )
                        } else {
                            status = "Select memos to upload"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isUploading
                ) { Text("Upload Selected", fontSize = 16.sp) }
            }
        }
    }
}

@Composable
fun MemoListItem(
    memo: VoiceMemo,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
    mediaStoreRepository: MediaStoreRepository
) {
    val voiceLevel = mediaStoreRepository.estimateVoiceLevel(memo.duration, memo.size)

    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = onSelectionChanged, modifier = Modifier.padding(end = 12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = memo.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(text = mediaStoreRepository.formatDate(memo.dateAdded), fontSize = 12.sp, color = Color.Gray)
            Text(
                text = "Duration: ${mediaStoreRepository.formatDuration(memo.duration)} | Size: ${formatBytes(memo.size)}",
                fontSize = 11.sp,
                color = Color.Gray
            )
            Text(
                text = "Voice level: ${"▮".repeat(voiceLevel)}${"▯".repeat(5 - voiceLevel)}",
                fontSize = 12.sp,
                color = Color(0xFF5E35B1)
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024
    val mb = kb / 1024
    return when {
        mb > 0 -> "$mb MB"
        kb > 0 -> "$kb KB"
        else -> "$bytes B"
    }
}
