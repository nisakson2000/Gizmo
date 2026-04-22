package ai.gizmo.app.voice

import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.gizmo.app.model.Voice
import ai.gizmo.app.network.GizmoApi
import ai.gizmo.app.ui.theme.Accent
import ai.gizmo.app.ui.theme.BgPrimary
import ai.gizmo.app.ui.theme.BgSecondary
import ai.gizmo.app.ui.theme.BgTertiary
import ai.gizmo.app.ui.theme.Border
import ai.gizmo.app.ui.theme.ErrorColor
import ai.gizmo.app.ui.theme.TextDim
import ai.gizmo.app.ui.theme.TextPrimary
import ai.gizmo.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceStudioScreen(
    api: GizmoApi,
    selectedVoiceId: String?,
    onSelectVoice: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voices = remember { mutableStateListOf<Voice>() }
    var showAddDialog by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var maxDuration by remember { mutableIntStateOf(60) }
    var previewVoiceId by remember { mutableStateOf<String?>(null) }
    var previewText by remember { mutableStateOf("Hello, this is a voice preview.") }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        addUri = uri
    }

    LaunchedEffect(Unit) {
        voices.clear()
        voices.addAll(api.getVoices())
    }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.release() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Studio", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Accent,
                contentColor = BgPrimary
            ) {
                Icon(Icons.Default.Add, "Add Voice")
            }
        },
        containerColor = BgPrimary
    ) { padding ->
        if (voices.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No voices yet", color = TextDim, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tap + to add a voice", color = TextDim, fontSize = 14.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(voices, key = { it.id }) { voice ->
                    VoiceItem(
                        voice = voice,
                        isSelected = voice.id == selectedVoiceId,
                        onSelect = { onSelectVoice(voice.id) },
                        onPreview = { previewVoiceId = voice.id },
                        onDelete = {
                            scope.launch {
                                if (api.deleteVoice(voice.id)) {
                                    voices.removeAll { it.id == voice.id }
                                    if (selectedVoiceId == voice.id) onSelectVoice(null)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Add voice dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; addUri = null; addName = ""; maxDuration = 60 },
            title = { Text("Add Voice") },
            text = {
                Column {
                    OutlinedTextField(
                        value = addName,
                        onValueChange = { addName = it },
                        label = { Text("Voice name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { filePicker.launch("audio/*") }) {
                        Text(if (addUri != null) "File selected" else "Pick audio file", color = Accent)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Clip duration", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30, 60, 90, 120).forEach { seconds ->
                            FilterChip(
                                selected = maxDuration == seconds,
                                onClick = { maxDuration = seconds },
                                label = { Text("${seconds}s") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent,
                                    selectedLabelColor = BgPrimary,
                                    containerColor = BgTertiary,
                                    labelColor = TextPrimary
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = addUri ?: return@TextButton
                        val name = addName.ifBlank { "Voice" }
                        val duration = maxDuration
                        showAddDialog = false
                        scope.launch {
                            val success = api.uploadVoice(uri, name, duration, context.contentResolver)
                            if (success) {
                                voices.clear()
                                voices.addAll(api.getVoices())
                            }
                        }
                        addUri = null
                        addName = ""
                        maxDuration = 60
                    },
                    enabled = addUri != null
                ) {
                    Text("Upload", color = if (addUri != null) Accent else TextDim)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; addUri = null; addName = ""; maxDuration = 60 }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgSecondary
        )
    }

    // Preview dialog
    previewVoiceId?.let { voiceId ->
        AlertDialog(
            onDismissRequest = { previewVoiceId = null; mediaPlayer?.release(); mediaPlayer = null },
            title = { Text("Preview Voice") },
            text = {
                Column {
                    OutlinedTextField(
                        value = previewText,
                        onValueChange = { previewText = it },
                        label = { Text("Preview text") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val path = api.previewVoice(voiceId, previewText, context.cacheDir)
                        if (path != null) {
                            mediaPlayer?.release()
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(path)
                                prepare()
                                start()
                            }
                        }
                    }
                }) {
                    Text("Synthesize", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { previewVoiceId = null; mediaPlayer?.release(); mediaPlayer = null }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = BgSecondary
        )
    }
}

@Composable
private fun VoiceItem(
    voice: Voice,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) BgTertiary else BgSecondary,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Accent) else null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onSelect() }.padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(voice.name, color = TextPrimary, fontWeight = FontWeight.Medium)
                if (!voice.transcript.isNullOrEmpty()) {
                    Text(voice.transcript, color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (isSelected) {
                Icon(Icons.Default.Check, "Selected", tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            IconButton(onClick = onPreview) {
                Icon(Icons.Default.PlayArrow, "Preview", tint = TextSecondary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = ErrorColor.copy(alpha = 0.7f))
            }
        }
    }
}
