package dev.apexstudio.ide.compose.preview.domain.model

import dev.apexstudio.ide.compose.preview.PreviewConfig

data class ParsedPreviewSource(
    val packageName: String,
    val className: String?,
    val previewConfigs: List<PreviewConfig>,
)
