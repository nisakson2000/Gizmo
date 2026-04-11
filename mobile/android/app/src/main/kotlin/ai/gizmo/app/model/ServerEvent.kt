package ai.gizmo.app.model

import org.json.JSONObject

sealed class ServerEvent {
    data class TraceId(val traceId: String) : ServerEvent()
    data class Thinking(val content: String) : ServerEvent()
    data class Token(val content: String) : ServerEvent()
    data class ToolCall(val tool: String, val status: String) : ServerEvent()
    data class ToolResult(val tool: String, val result: String) : ServerEvent()
    data class Title(val title: String, val conversationId: String) : ServerEvent()
    data class Usage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int) : ServerEvent()
    data class Done(val traceId: String, val conversationId: String) : ServerEvent()
    data class Error(val error: String, val traceId: String?) : ServerEvent()
    data class AudioChunk(val chunkIndex: Int, val sentenceIndex: Int, val sampleRate: Int, val isLast: Boolean) : ServerEvent()
    data class Audio(val url: String) : ServerEvent()
    data class TtsInfo(val message: String) : ServerEvent()
    data class Unknown(val type: String) : ServerEvent()

    companion object {
        fun parse(json: JSONObject): ServerEvent {
            return when (val type = json.optString("type", "")) {
                "trace_id" -> TraceId(json.optString("trace_id", ""))
                "thinking" -> Thinking(json.optString("content", ""))
                "token" -> Token(json.optString("content", ""))
                "tool_call" -> ToolCall(
                    tool = json.optString("tool", ""),
                    status = json.optString("status", "")
                )
                "tool_result" -> ToolResult(
                    tool = json.optString("tool", ""),
                    result = json.optString("result", "")
                )
                "title" -> Title(
                    title = json.optString("title", ""),
                    conversationId = json.optString("conversation_id", "")
                )
                "usage" -> Usage(
                    promptTokens = json.optInt("prompt_tokens", 0),
                    completionTokens = json.optInt("completion_tokens", 0),
                    totalTokens = json.optInt("total_tokens", 0)
                )
                "done" -> Done(
                    traceId = json.optString("trace_id", ""),
                    conversationId = json.optString("conversation_id", "")
                )
                "error" -> Error(
                    error = json.optString("error", ""),
                    traceId = json.optString("trace_id").takeIf { it.isNotEmpty() }
                )
                "audio_chunk" -> AudioChunk(
                    chunkIndex = json.optInt("chunk_index", 0),
                    sentenceIndex = json.optInt("sentence_index", 0),
                    sampleRate = json.optInt("sample_rate", 24000),
                    isLast = json.optBoolean("is_last", false)
                )
                "audio" -> Audio(url = json.optString("url", ""))
                "tts_info" -> TtsInfo(message = json.optString("message", ""))
                else -> Unknown(type)
            }
        }
    }
}
