package ai.gizmo.app.model

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ai.gizmo.app.network.GizmoApi
import ai.gizmo.app.network.GizmoWebSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatViewModel(
    val serverUrl: String,
    private val serverId: String,
    val serverName: String
) : ViewModel() {

    val messages = mutableStateListOf<Message>()
    val streamingContent = mutableStateOf("")
    val streamingThinking = mutableStateOf("")
    val streamingToolCalls = mutableStateListOf<ToolCall>()
    val connectionState = mutableStateOf(ConnectionState.DISCONNECTED)
    val conversations = mutableStateListOf<Conversation>()
    val activeConversationId = mutableStateOf<String?>(null)
    val generating = mutableStateOf(false)
    val modes = mutableStateListOf<Mode>()
    val selectedMode = mutableStateOf("chat")
    val thinkingEnabled = mutableStateOf(false)
    val contextLength = mutableStateOf(32768)
    val serviceHealth = mutableStateListOf<ServiceHealth>()
    val searchResults = mutableStateListOf<Conversation>()
    val isSearching = mutableStateOf(false)
    val pendingImageUri = mutableStateOf<Uri?>(null)
    val pendingDocumentName = mutableStateOf<String?>(null)
    val pendingVideoUri = mutableStateOf<Uri?>(null)
    val pendingAudioName = mutableStateOf<String?>(null)
    val selectedMessageIndex = mutableStateOf<Int?>(null)
    val editingMessageIndex = mutableStateOf<Int?>(null)
    val snackbarMessage = mutableStateOf<String?>(null)

    private var pendingImageDataUrl: String? = null
    private var pendingDocumentContent: String? = null
    private var pendingVideoFrames: List<String>? = null
    private var pendingVideoUrl: String? = null
    private var pendingAudioTranscription: String? = null
    private var currentTraceId: String = ""

    val api = GizmoApi(serverUrl)
    private val webSocket = GizmoWebSocket(
        serverUrl = serverUrl,
        onEvent = ::handleEvent,
        onStateChange = ::handleStateChange
    )

    init {
        webSocket.connect()
        loadConversations()
        loadModes()
    }

    fun sendMessage(text: String) {
        if (text.isBlank() && pendingImageUri.value == null &&
            pendingDocumentName.value == null && pendingVideoUri.value == null &&
            pendingAudioName.value == null) return

        currentTraceId = ""

        val imageDataUrl = pendingImageDataUrl
        val docContent = pendingDocumentContent
        val docName = pendingDocumentName.value
        val videoFrames = pendingVideoFrames
        val videoUrl = pendingVideoUrl
        val audioTranscript = pendingAudioTranscription

        val fullMessage = when {
            audioTranscript != null -> "Analyze this audio transcription: $audioTranscript"
            docContent != null && docName != null ->
                if (text.isNotBlank()) "$text\n\n[Attached: $docName]\n$docContent"
                else "[Attached: $docName]\n$docContent"
            else -> text
        }

        val userMessage = Message(
            role = "user",
            content = if (audioTranscript != null) text.ifBlank { "Transcribe audio" } else text,
            imageUrl = pendingImageUri.value?.toString(),
            videoUrl = pendingVideoUri.value?.toString()
        )
        messages.add(userMessage)

        clearAttachment()
        streamingContent.value = ""
        streamingThinking.value = ""
        streamingToolCalls.clear()
        generating.value = true
        selectedMessageIndex.value = null

        webSocket.send(
            message = fullMessage,
            thinking = thinkingEnabled.value,
            conversationId = activeConversationId.value,
            mode = selectedMode.value,
            contextLength = contextLength.value,
            image = imageDataUrl,
            videoFrames = videoFrames,
            videoUrl = videoUrl
        )
    }

    fun editMessage(index: Int, newText: String) {
        val convId = activeConversationId.value ?: return
        editingMessageIndex.value = null
        selectedMessageIndex.value = null

        // Store old content as variant on the original message
        val oldMsg = messages[index]
        val oldVariants = oldMsg.variants.toMutableList()
        if (oldVariants.isEmpty()) {
            oldVariants.add(MessageVariant(oldMsg.content, oldMsg.thinking, oldMsg.toolCalls))
        }
        oldVariants.add(MessageVariant(newText))
        messages[index] = oldMsg.copy(
            content = newText,
            variants = oldVariants,
            currentVariantIndex = oldVariants.size - 1
        )

        viewModelScope.launch {
            api.deleteMessagesFrom(convId, index + 1)
            // Remove messages after the edited one
            while (messages.size > index + 1) {
                messages.removeAt(messages.size - 1)
            }
            // Re-send with edited text
            currentTraceId = ""
            streamingContent.value = ""
            streamingThinking.value = ""
            streamingToolCalls.clear()
            generating.value = true
            webSocket.send(
                message = newText,
                thinking = thinkingEnabled.value,
                conversationId = convId,
                mode = selectedMode.value,
                contextLength = contextLength.value
            )
        }
    }

    fun regenerateLastResponse() {
        val convId = activeConversationId.value ?: return
        if (messages.size < 2) return
        selectedMessageIndex.value = null

        val lastAssistantIdx = messages.indexOfLast { it.role == "assistant" }
        if (lastAssistantIdx < 0) return
        val lastUserIdx = lastAssistantIdx - 1
        if (lastUserIdx < 0 || messages[lastUserIdx].role != "user") return

        val userText = messages[lastUserIdx].content
        val oldAssistant = messages[lastAssistantIdx]

        // Store old response as variant
        val oldVariants = oldAssistant.variants.toMutableList()
        if (oldVariants.isEmpty()) {
            oldVariants.add(MessageVariant(oldAssistant.content, oldAssistant.thinking, oldAssistant.toolCalls))
        }

        viewModelScope.launch {
            api.deleteMessagesFrom(convId, lastUserIdx)
            // Remove from the user message onward
            while (messages.size > lastUserIdx) {
                messages.removeAt(messages.size - 1)
            }
            // Re-add the user message
            messages.add(Message(role = "user", content = userText))

            currentTraceId = ""
            streamingContent.value = ""
            streamingThinking.value = ""
            streamingToolCalls.clear()
            generating.value = true
            webSocket.send(
                message = userText,
                thinking = thinkingEnabled.value,
                conversationId = convId,
                mode = selectedMode.value,
                contextLength = contextLength.value,
                regenerate = true
            )
        }

        // We'll add the variant to the new response in finalizeAssistantMessage
        pendingOldVariants = oldVariants
    }

    private var pendingOldVariants: List<MessageVariant>? = null

    fun switchVariant(messageIndex: Int, direction: Int) {
        if (messageIndex !in messages.indices) return
        val msg = messages[messageIndex]
        if (msg.variants.isEmpty()) return
        val newIdx = (msg.currentVariantIndex + direction).coerceIn(0, msg.variants.size - 1)
        messages[messageIndex] = msg.copy(currentVariantIndex = newIdx)
    }

    fun stopGeneration() {
        webSocket.stop()
        finalizeAssistantMessage()
    }

    fun newChat() {
        activeConversationId.value = null
        messages.clear()
        streamingContent.value = ""
        streamingThinking.value = ""
        streamingToolCalls.clear()
        generating.value = false
        selectedMessageIndex.value = null
        editingMessageIndex.value = null
        clearAttachment()
    }

    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            val result = api.getConversation(conversationId)
            if (result != null) {
                messages.clear()
                messages.addAll(result)
                activeConversationId.value = conversationId
                streamingContent.value = ""
                streamingThinking.value = ""
                streamingToolCalls.clear()
                generating.value = false
                selectedMessageIndex.value = null
                editingMessageIndex.value = null
            }
        }
    }

    fun loadConversations() {
        viewModelScope.launch {
            val result = api.getConversations()
            conversations.clear()
            conversations.addAll(result)
        }
    }

    fun softDeleteConversation(id: String) {
        conversations.removeAll { it.id == id }
        if (activeConversationId.value == id) newChat()
    }

    fun undoDeleteConversation(conversation: Conversation) {
        conversations.add(0, conversation)
    }

    fun confirmDeleteConversation(id: String) {
        viewModelScope.launch { api.deleteConversation(id) }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            if (api.renameConversation(conversationId, newTitle)) {
                val idx = conversations.indexOfFirst { it.id == conversationId }
                if (idx >= 0) {
                    conversations[idx] = conversations[idx].copy(title = newTitle)
                }
            }
        }
    }

    fun searchConversations(query: String) {
        if (query.isBlank()) {
            isSearching.value = false
            searchResults.clear()
            return
        }
        isSearching.value = true
        viewModelScope.launch {
            val results = api.searchConversations(query)
            searchResults.clear()
            searchResults.addAll(results)
        }
    }

    fun handleImagePick(uri: Uri, contentResolver: ContentResolver) {
        pendingImageUri.value = uri
        pendingDocumentName.value = null
        pendingDocumentContent = null
        pendingVideoUri.value = null
        pendingVideoFrames = null
        pendingVideoUrl = null
        pendingAudioName.value = null
        pendingAudioTranscription = null
        viewModelScope.launch {
            val dataUrl = api.uploadImage(uri, contentResolver)
            if (dataUrl != null) {
                pendingImageDataUrl = dataUrl
            } else {
                pendingImageUri.value = null
                showSnackbar("Upload failed — check your connection")
            }
        }
    }

    fun handleDocumentPick(uri: Uri, contentResolver: ContentResolver) {
        clearAttachment()
        viewModelScope.launch {
            val result = api.uploadDocument(uri, contentResolver)
            if (result != null) {
                pendingDocumentName.value = result.first
                pendingDocumentContent = result.second
            } else {
                showSnackbar("Upload failed — check your connection")
            }
        }
    }

    fun handleVideoPick(uri: Uri, contentResolver: ContentResolver) {
        clearAttachment()
        pendingVideoUri.value = uri
        viewModelScope.launch {
            val result = api.uploadVideo(uri, contentResolver)
            if (result != null) {
                pendingVideoFrames = result.frames
                pendingVideoUrl = result.videoUrl
            } else {
                pendingVideoUri.value = null
                showSnackbar("Video upload failed — check your connection")
            }
        }
    }

    fun handleAudioPick(uri: Uri, contentResolver: ContentResolver) {
        clearAttachment()
        pendingAudioName.value = "Transcribing\u2026"
        viewModelScope.launch {
            val text = api.transcribeAudio(uri, contentResolver)
            if (text != null) {
                pendingAudioName.value = "Audio transcribed"
                pendingAudioTranscription = text
            } else {
                pendingAudioName.value = null
                showSnackbar("Transcription failed — check your connection")
            }
        }
    }

    fun clearAttachment() {
        pendingImageUri.value = null
        pendingImageDataUrl = null
        pendingDocumentName.value = null
        pendingDocumentContent = null
        pendingVideoUri.value = null
        pendingVideoFrames = null
        pendingVideoUrl = null
        pendingAudioName.value = null
        pendingAudioTranscription = null
    }

    fun exportConversation(conversationId: String, contentResolver: ContentResolver) {
        viewModelScope.launch {
            val markdown = api.exportConversation(conversationId)
            if (markdown != null) {
                val title = conversations.find { it.id == conversationId }?.title ?: "conversation"
                val filename = "${title.replace(Regex("[^a-zA-Z0-9 ]"), "").take(50)}.md"
                saveToDownloads(contentResolver, filename, "text/markdown", markdown.toByteArray())
                showSnackbar("Exported to Downloads")
            } else {
                showSnackbar("Export failed")
            }
        }
    }

    fun downloadMediaFile(url: String, contentResolver: ContentResolver) {
        viewModelScope.launch {
            val result = api.downloadFile(url)
            if (result != null) {
                val (filename, bytes) = result
                val mimeType = when {
                    filename.endsWith(".pdf") -> "application/pdf"
                    filename.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    filename.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    else -> "application/octet-stream"
                }
                saveToDownloads(contentResolver, filename, mimeType, bytes)
                showSnackbar("Saved to Downloads: $filename")
            } else {
                showSnackbar("Download failed")
            }
        }
    }

    private fun saveToDownloads(contentResolver: ContentResolver, filename: String, mimeType: String, bytes: ByteArray) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        uri?.let { contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
    }

    fun showSnackbar(message: String) {
        snackbarMessage.value = message
    }

    fun clearSnackbar() {
        snackbarMessage.value = null
    }

    fun loadModes() {
        viewModelScope.launch {
            val result = api.getModes()
            modes.clear()
            modes.addAll(result)
        }
    }

    fun loadServiceHealth() {
        viewModelScope.launch {
            val result = api.getServiceHealth()
            serviceHealth.clear()
            serviceHealth.addAll(result)
        }
    }

    private fun handleEvent(event: ServerEvent) {
        viewModelScope.launch(Dispatchers.Main) {
            when (event) {
                is ServerEvent.TraceId -> {
                    currentTraceId = event.traceId
                }
                is ServerEvent.Thinking -> {
                    streamingThinking.value += event.content
                }
                is ServerEvent.Token -> {
                    streamingContent.value += event.content
                }
                is ServerEvent.ToolCall -> {
                    streamingToolCalls.add(ToolCall(event.tool, event.status))
                }
                is ServerEvent.ToolResult -> {
                    // Match first "running" tool call with this name
                    val idx = streamingToolCalls.indexOfFirst {
                        it.tool == event.tool && it.status == "running"
                    }
                    if (idx >= 0) {
                        streamingToolCalls[idx] = streamingToolCalls[idx].copy(
                            status = "done",
                            result = event.result
                        )
                    }
                }
                is ServerEvent.Title -> {
                    if (activeConversationId.value == null) {
                        activeConversationId.value = event.conversationId
                    }
                    val idx = conversations.indexOfFirst { it.id == event.conversationId }
                    if (idx >= 0) {
                        conversations[idx] = conversations[idx].copy(title = event.title)
                    } else {
                        conversations.add(0, Conversation(
                            id = event.conversationId,
                            title = event.title
                        ))
                    }
                }
                is ServerEvent.Usage -> { /* Available for future analytics display */ }
                is ServerEvent.Done -> {
                    activeConversationId.value = event.conversationId
                    finalizeAssistantMessage()
                    connectionState.value = ConnectionState.CONNECTED
                    loadConversations()
                }
                is ServerEvent.Error -> {
                    messages.add(Message(
                        role = "assistant",
                        content = "Error: ${event.error}",
                        traceId = event.traceId ?: ""
                    ))
                    generating.value = false
                    connectionState.value = ConnectionState.CONNECTED
                }
                is ServerEvent.Unknown -> { /* Ignore unrecognized event types */ }
            }
        }
    }

    private fun finalizeAssistantMessage() {
        val content = streamingContent.value
        val thinking = streamingThinking.value
        val toolCalls = streamingToolCalls.toList()

        if (content.isNotEmpty() || thinking.isNotEmpty() || toolCalls.isNotEmpty()) {
            val oldVariants = pendingOldVariants
            val variants = if (oldVariants != null) {
                val all = oldVariants.toMutableList()
                all.add(MessageVariant(content, thinking, toolCalls))
                all
            } else emptyList()

            messages.add(Message(
                role = "assistant",
                content = content,
                thinking = thinking,
                traceId = currentTraceId,
                toolCalls = toolCalls,
                variants = variants,
                currentVariantIndex = if (variants.isNotEmpty()) variants.size - 1 else 0
            ))
        }

        pendingOldVariants = null
        streamingContent.value = ""
        streamingThinking.value = ""
        streamingToolCalls.clear()
        generating.value = false
    }

    private fun handleStateChange(state: ConnectionState) {
        viewModelScope.launch(Dispatchers.Main) {
            connectionState.value = state
        }
    }

    override fun onCleared() {
        super.onCleared()
        webSocket.disconnect()
    }
}

class ChatViewModelFactory(
    private val serverUrl: String,
    private val serverId: String,
    private val serverName: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(serverUrl, serverId, serverName) as T
    }
}
