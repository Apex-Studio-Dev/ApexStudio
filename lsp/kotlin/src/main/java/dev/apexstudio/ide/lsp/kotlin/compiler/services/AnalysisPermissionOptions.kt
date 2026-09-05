package dev.apexstudio.ide.lsp.kotlin.compiler.services

import org.jetbrains.kotlin.analysis.api.platform.permissions.KotlinAnalysisPermissionOptions

class AnalysisPermissionOptions(
	override val defaultIsAnalysisAllowedOnEdt: Boolean = false,
	override val defaultIsAnalysisAllowedInWriteAction: Boolean = true,
) : KotlinAnalysisPermissionOptions