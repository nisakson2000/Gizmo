package ai.gizmo.app.model

import java.util.UUID

data class MessageVariant(
    val content: String,
    val thinking: String = "",
    val toolCalls: List<ToolCall> = emptyList()
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val thinking: String = "",
    val timestamp: String = "",
    val traceId: String = "",
    val audioUrl: String? = null,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val variants: List<MessageVariant> = emptyList(),
    val currentVariantIndex: Int = 0
) {
    val displayContent: String get() =
        if (variants.isNotEmpty() && currentVariantIndex < variants.size)
            variants[currentVariantIndex].content else content
    val displayThinking: String get() =
        if (variants.isNotEmpty() && currentVariantIndex < variants.size)
            variants[currentVariantIndex].thinking else thinking
    val displayToolCalls: List<ToolCall> get() =
        if (variants.isNotEmpty() && currentVariantIndex < variants.size)
            variants[currentVariantIndex].toolCalls else toolCalls
}

data class ToolCall(
    val tool: String,
    val status: String,
    val result: String = ""
)

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: String = "",
    val updatedAt: String = "",
    val snippet: String = ""
)

data class Mode(
    val name: String,
    val label: String,
    val description: String = "",
    val icon: String = ""
)

data class ServiceHealth(
    val name: String,
    val status: String,
    val error: String? = null
)

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, GENERATING
}
