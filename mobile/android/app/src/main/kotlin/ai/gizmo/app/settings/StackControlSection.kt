package ai.gizmo.app.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.gizmo.app.model.StackStatus
import ai.gizmo.app.network.GizmoApi
import ai.gizmo.app.ui.theme.Accent
import ai.gizmo.app.ui.theme.BgSecondary
import ai.gizmo.app.ui.theme.BgTertiary
import ai.gizmo.app.ui.theme.ErrorColor
import ai.gizmo.app.ui.theme.Success
import ai.gizmo.app.ui.theme.TextDim
import ai.gizmo.app.ui.theme.TextPrimary
import ai.gizmo.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WIKI_SETUP_URL = "https://github.com/nisakson2000/Gizmo/wiki/setup"

@Composable
fun StackControlSection(api: GizmoApi) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var stackStatus by remember { mutableStateOf<StackStatus?>(null) }
    // null = still checking, true = service reachable, false = connection refused / unreachable
    var isReachable by remember { mutableStateOf<Boolean?>(null) }
    var inFlight by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showStopConfirm by remember { mutableStateOf(false) }

    // Poll health + status every 5s while this section is visible. Skip polling during
    // an in-flight start/stop so we don't stomp on the operation's own polling loop.
    LaunchedEffect(Unit) {
        while (true) {
            if (!inFlight) {
                val healthOk = api.checkStackControlHealth()
                isReachable = healthOk
                stackStatus = if (healthOk) api.getStackStatus() else null
            }
            delay(5000)
        }
    }

    fun startStack() {
        scope.launch {
            inFlight = true
            statusMessage = null
            val result = api.startStack()
            if (result == null) {
                statusMessage = "Start request failed"
                inFlight = false
                return@launch
            }
            statusMessage = result.message
            if (!result.success) {
                inFlight = false
                return@launch
            }
            // Wait for the stack to come up. LLM load can take ~20s so poll generously.
            val deadline = System.currentTimeMillis() + 90_000
            var running = false
            while (System.currentTimeMillis() < deadline) {
                val s = api.getStackStatus()
                stackStatus = s
                if (s?.running == true) { running = true; break }
                delay(2000)
            }
            if (!running) {
                statusMessage = "Stack started, services still warming up"
            }
            inFlight = false
        }
    }

    fun stopStack() {
        scope.launch {
            inFlight = true
            statusMessage = null
            val result = api.stopStack()
            if (result == null) {
                statusMessage = "Stop request failed"
                inFlight = false
                return@launch
            }
            statusMessage = result.message
            if (!result.success) {
                inFlight = false
                return@launch
            }
            val deadline = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < deadline) {
                val s = api.getStackStatus()
                stackStatus = s
                if (s?.running == false) break
                delay(2000)
            }
            inFlight = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Stack Control",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        when (isReachable) {
            null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Accent,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Checking…", color = TextDim, fontSize = 13.sp)
                }
            }
            false -> {
                Text(
                    "Stack control service not installed on this server.",
                    color = TextDim,
                    fontSize = 13.sp
                )
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WIKI_SETUP_URL)))
                    } catch (_: Exception) { }
                }) {
                    Text("Setup instructions →", color = Accent)
                }
            }
            true -> {
                StatusRow(stackStatus, inFlight)
                Spacer(modifier = Modifier.height(8.dp))
                ButtonsRow(
                    stackStatus = stackStatus,
                    inFlight = inFlight,
                    onStart = { startStack() },
                    onStopRequest = { showStopConfirm = true }
                )
                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(msg, color = TextDim, fontSize = 12.sp)
                }
            }
        }
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("Stop stack?", color = TextPrimary) },
            text = { Text("This will shut down the LLM and end any active chats.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    stopStack()
                }) { Text("Stop", color = ErrorColor) }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgSecondary
        )
    }
}

@Composable
private fun StatusRow(status: StackStatus?, inFlight: Boolean) {
    val total = status?.containers?.size ?: 0
    val runningCount = status?.containers?.count { it.status == "running" } ?: 0
    val (color, label) = when {
        status == null -> TextDim to "Unknown"
        inFlight -> Accent to "Working…"
        !status.running || total == 0 -> ErrorColor to "Stopped"
        runningCount == total -> Success to "Running · $runningCount/$total"
        else -> Color(0xFFE09040) to "Partial · $runningCount/$total"
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgTertiary
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape))
            Spacer(modifier = Modifier.size(8.dp))
            Text(label, color = TextPrimary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ButtonsRow(
    stackStatus: StackStatus?,
    inFlight: Boolean,
    onStart: () -> Unit,
    onStopRequest: () -> Unit
) {
    val canStart = !inFlight && stackStatus != null && !stackStatus.running
    val canStop = !inFlight && stackStatus != null && stackStatus.running
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onStart,
            enabled = canStart,
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = androidx.compose.ui.graphics.Color.Black,
                disabledContainerColor = BgTertiary,
                disabledContentColor = TextDim
            ),
            modifier = Modifier.weight(1f)
        ) {
            if (inFlight && stackStatus?.running == false) {
                CircularProgressIndicator(
                    color = TextDim,
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.size(6.dp))
            }
            Text("Start")
        }
        Button(
            onClick = onStopRequest,
            enabled = canStop,
            colors = ButtonDefaults.buttonColors(
                containerColor = ErrorColor,
                contentColor = androidx.compose.ui.graphics.Color.White,
                disabledContainerColor = BgTertiary,
                disabledContentColor = TextDim
            ),
            modifier = Modifier.weight(1f)
        ) {
            if (inFlight && stackStatus?.running == true) {
                CircularProgressIndicator(
                    color = TextDim,
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.size(6.dp))
            }
            Text("Stop")
        }
    }
}
