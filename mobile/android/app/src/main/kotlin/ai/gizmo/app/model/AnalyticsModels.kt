package ai.gizmo.app.model

data class AnalyticsSummary(
    val totalPromptTokens: Long = 0, val totalCompletionTokens: Long = 0, val totalTokens: Long = 0,
    val totalMessages: Int = 0, val totalConversations: Int = 0,
    val avgResponseMs: Long = 0, val avgContextMs: Long = 0,
    val estimatedSavingsUsd: Double = 0.0,
    val providers: List<ProviderCost> = emptyList()
)

data class DailyUsage(
    val date: String, val promptTokens: Long, val completionTokens: Long,
    val totalTokens: Long, val messages: Int, val avgResponseMs: Long = 0
)

data class ConversationUsage(
    val conversationId: String, val title: String,
    val promptTokens: Long, val completionTokens: Long, val totalTokens: Long,
    val messages: Int, val lastActive: String = ""
)

data class ProviderCost(
    val provider: String, val inputPricePer1m: Double, val outputPricePer1m: Double,
    val estimatedCostUsd: Double
)

data class ModeUsage(val mode: String, val totalTokens: Long, val messages: Int)

fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> "$n"
}
