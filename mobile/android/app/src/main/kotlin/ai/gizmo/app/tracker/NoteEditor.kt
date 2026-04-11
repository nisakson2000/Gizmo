package ai.gizmo.app.tracker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ai.gizmo.app.model.TrackerNote
import ai.gizmo.app.network.GizmoApi
import ai.gizmo.app.ui.theme.Accent
import ai.gizmo.app.ui.theme.BgPrimary
import ai.gizmo.app.ui.theme.BgSecondary
import ai.gizmo.app.ui.theme.Border
import ai.gizmo.app.ui.theme.ErrorColor
import ai.gizmo.app.ui.theme.TextDim
import ai.gizmo.app.ui.theme.TextPrimary
import ai.gizmo.app.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditor(api: GizmoApi, noteId: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var saveJob by remember { mutableStateOf<Job?>(null) }

    fun save(fields: Map<String, Any>) {
        saveJob?.cancel()
        saveJob = scope.launch { delay(800); api.updateNote(noteId, fields) }
    }

    LaunchedEffect(noteId) {
        val notes = api.getNotes()
        val note = notes.find { it.id == noteId } ?: return@LaunchedEffect
        title = note.title; content = note.content; pinned = note.pinned
    }

    val fc = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Accent, unfocusedBorderColor = Border, focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary, cursorColor = Accent, focusedLabelColor = Accent, unfocusedLabelColor = TextSecondary)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Note", color = TextPrimary) },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary) } },
                actions = {
                    IconButton(onClick = { pinned = !pinned; save(mapOf("pinned" to pinned)) }) {
                        Icon(Icons.Default.PushPin, "Pin", tint = if (pinned) Accent else TextDim)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        }, containerColor = BgPrimary
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it; save(mapOf("title" to it)) },
                label = { Text("Title") }, singleLine = true, colors = fc, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = content, onValueChange = { content = it; save(mapOf("content" to it)) },
                label = { Text("Content") }, colors = fc,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = TextPrimary),
                modifier = Modifier.fillMaxWidth().weight(1f))
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, null, tint = ErrorColor); Spacer(modifier = Modifier.width(4.dp))
                Text("Delete Note", color = ErrorColor)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Note") }, text = { Text("Delete \"$title\"?") },
            confirmButton = { TextButton(onClick = { showDeleteConfirm = false; scope.launch { api.deleteNote(noteId); onDismiss() } }) { Text("Delete", color = ErrorColor) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = TextSecondary) } },
            containerColor = BgSecondary)
    }
}
