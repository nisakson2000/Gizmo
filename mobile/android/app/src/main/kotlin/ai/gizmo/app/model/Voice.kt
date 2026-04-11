package ai.gizmo.app.model

data class Voice(
    val id: String,
    val name: String,
    val filename: String = "",
    val size: Long = 0,
    val transcript: String? = null
)
