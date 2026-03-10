package com.example.voicememouploader

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private lateinit var mediaStoreRepository: MediaStoreRepository
    private lateinit var uploadService: UploadService

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaStoreRepository = MediaStoreRepository(this)
        uploadService = UploadService()

        val permissionsToRequest = mutableListOf(Manifest.permission.INTERNET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissions.launch(permissionsToRequest.toTypedArray())

        setContent {
            VoiceMemoUploaderApp(
                mediaStoreRepository = mediaStoreRepository,
                uploadService = uploadService
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceMemoUploaderApp(
    mediaStoreRepository: MediaStoreRepository,
    uploadService: UploadService
) {
    var memos by remember { mutableStateOf<List<VoiceMemo>>(emptyList()) }
    var selectedMemos by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var status by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var showServerConfig by remember { mutableStateOf(false) }
    var serverIp by remember { mutableStateOf("100.100.100.100") }
    var serverPort by remember { mutableStateOf("8080") }

    var recordingsOnly by remember { mutableStateOf(true) }
    var minDurationSeconds by remember { mutableStateOf("10") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var folderExpanded by remember { mutableStateOf(false) }
    val topFolders by remember { mutableStateOf(mediaStoreRepository.getTopLevelFolders()) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "Voice Memo Uploader",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Filters",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )

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
                        DropdownMenuItem(
                            text = { Text("All folders") },
                            onClick = {
                                selectedFolder = null
                                folderExpanded = false
                            }
                        )
                        topFolders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder) },
                                onClick = {
                                    selectedFolder = folder
                                    folderExpanded = false
                                }
                            )
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
                        status = if (memos.isEmpty()) {
                            "No matching voice memos found"
                        } else {
                            "Found ${memos.size} voice memo(s)"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isUploading
                ) {
                    Text("Scan Voice Memos", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showServerConfig = !showServerConfig },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) { Text("Server Config") }

                if (showServerConfig) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverIp,
                        onValueChange = { serverIp = it },
                        label = { Text("Tailscale IP") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverPort,
                        onValueChange = { serverPort = it },
                        label = { Text("Port") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

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
                    Text(
                        text = "Memos (${selectedMemos.size}/${memos.size} selected)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF5F5F5)).padding(8.dp)
                    ) {
                        items(memos) { memo ->
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
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Button(
                    onClick = {
                        if (selectedMemos.isNotEmpty()) {
                            isUploading = true
                            status = "Uploading..."
                            val memosToUpload = memos.filter { selectedMemos.contains(it.id) }
                            uploadService.uploadMemos(
                                memosToUpload,
                                onProgress = {
                                    status = it
                                    isUploading = false
                                },
                                onError = {
                                    status = it
                                    isUploading = false
                                }
                            )
                        } else {
                            status = "Select memos to upload"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isUploading
                ) {
                    Text("Upload Selected", fontSize = 16.sp)
                }
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
        Checkbox(
            checked = isSelected,
            onCheckedChange = onSelectionChanged,
            modifier = Modifier.padding(end = 12.dp)
        )

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
