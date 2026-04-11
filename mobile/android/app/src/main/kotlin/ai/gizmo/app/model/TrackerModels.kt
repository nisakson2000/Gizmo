package ai.gizmo.app.model

data class TrackerTask(
    val id: String = "",
    val title: String,
    val description: String = "",
    val status: String = "todo",
    val priority: String = "medium",
    val dueDate: String? = null,
    val tags: List<String> = emptyList(),
    val parentId: String? = null,
    val recurrence: String = "none",
    val createdAt: String = "",
    val updatedAt: String = "",
    val completedAt: String? = null,
    val subtasks: List<TrackerTask> = emptyList()
)

data class TrackerNote(
    val id: String = "",
    val title: String,
    val content: String = "",
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = ""
)
