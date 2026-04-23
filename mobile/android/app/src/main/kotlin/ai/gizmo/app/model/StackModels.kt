package ai.gizmo.app.model

data class StackContainer(
    val name: String,
    val status: String,
    val uptimeSeconds: Long?
)

data class StackStatus(
    val running: Boolean,
    val containers: List<StackContainer>
)

data class StackOperationResult(
    val success: Boolean,
    val message: String
)
