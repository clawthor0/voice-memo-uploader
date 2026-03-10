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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                // Permissions granted
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaStoreRepository = MediaStoreRepository(this)
        uploadService = UploadService()

        // Request permissions
        val permissionsToRequest = mutableListOf(
            Manifest.permission.INTERNET
        )

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

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Text(
                    text = "Voice Memo Uploader",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Scan Button
                Button(
                    onClick = {
                        memos = mediaStoreRepository.getVoiceMemos()
                        selectedMemos = emptySet()
                        status = if (memos.isEmpty()) "No voice memos found" else "Found ${memos.size} memo(s)"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isUploading
                ) {
                    Text("Scan Voice Memos", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Server Config Button
                Button(
                    onClick = { showServerConfig = !showServerConfig },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray
                    )
                ) {
                    Text("Server Config")
                }

                // Server Config Section
                if (showServerConfig) {
                    Spacer(modifier = Modifier.height(12.dp))
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
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Text
                if (status.isNotEmpty()) {
                    Text(
                        text = status,
                        color = if (isUploading) Color.Blue else Color.Black,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.LightGray)
                            .padding(12.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Memos List
                if (memos.isNotEmpty()) {
                    Text(
                        text = "Memos (${selectedMemos.size}/${memos.size} selected)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFFF5F5F5))
                            .padding(8.dp)
                    ) {
                        items(memos) { memo ->
                            MemoListItem(
                                memo = memo,
                                isSelected = selectedMemos.contains(memo.id),
                                onSelectionChanged = { selected ->
                                    selectedMemos = if (selected) {
                                        selectedMemos + memo.id
                                    } else {
                                        selectedMemos - memo.id
                                    }
                                },
                                mediaStoreRepository = mediaStoreRepository
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Upload Button
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6200EE)
                    ),
                    enabled = !isUploading
                ) {
                    Text("Upload Selected", fontSize = 16.sp, color = Color.White)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = onSelectionChanged,
            modifier = Modifier.padding(end = 12.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = memo.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = mediaStoreRepository.formatDate(memo.dateAdded),
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = "Duration: ${mediaStoreRepository.formatDuration(memo.duration)} | Size: ${formatBytes(memo.size)}",
                fontSize = 11.sp,
                color = Color.Gray
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
