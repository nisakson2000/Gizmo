package ai.gizmo.app.model

data class ExecutionResult(
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = 0,
    val timedOut: Boolean = false,
    val outputFiles: List<OutputFile> = emptyList()
)

data class OutputFile(
    val filename: String,
    val url: String
)

val EXECUTABLE_LANGUAGES = listOf("python", "javascript", "bash", "c", "cpp", "go", "lua")
val PREVIEW_LANGUAGES = listOf("html", "css", "svg", "markdown")
val ALL_LANGUAGES = EXECUTABLE_LANGUAGES + PREVIEW_LANGUAGES
