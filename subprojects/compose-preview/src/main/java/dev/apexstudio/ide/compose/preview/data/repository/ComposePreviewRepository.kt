package dev.apexstudio.ide.compose.preview.data.repository

import android.content.Context
import dev.apexstudio.ide.compose.preview.compiler.CompileDiagnostic
import dev.apexstudio.ide.compose.preview.data.source.ProjectContext
import dev.apexstudio.ide.compose.preview.domain.model.ParsedPreviewSource
import java.io.File

interface ComposePreviewRepository {

    suspend fun initialize(context: Context, filePath: String): Result<InitializationResult>

    suspend fun compilePreview(
        source: String,
        parsedSource: ParsedPreviewSource,
    ): Result<CompilationResult>

    fun computeSourceHash(source: String): String

    fun reset()
}

sealed class InitializationResult {
    data class Ready(
        val runtimeDex: List<File>,
        val projectContext: ProjectContext,
    ) : InitializationResult()

    data class NeedsBuild(
        val modulePath: String,
        val variantName: String,
    ) : InitializationResult()

    data class Failed(val message: String) : InitializationResult()
}

data class CompilationResult(
    val dexFile: File,
    val className: String,
    val runtimeDex: List<File>,
    val projectDexFiles: List<File>,
)

class CompilationException(
    message: String,
    val diagnostics: List<CompileDiagnostic> = emptyList(),
) : Exception(message)
