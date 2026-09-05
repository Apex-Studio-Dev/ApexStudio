package dev.apexstudio.ide.lsp.kotlin.completion

import dev.apexstudio.ide.lsp.kotlin.utils.AnalysisContext
import dev.apexstudio.ide.lsp.models.CompletionItem
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import org.jetbrains.kotlin.psi.KtFile
import org.slf4j.LoggerFactory

internal abstract class AdvancedKotlinEditHandler(
	protected val analysisContext: AnalysisContext,
) : BaseKotlinEditHandler() {

	companion object {
		private val logger = LoggerFactory.getLogger(AdvancedKotlinEditHandler::class.java)
	}

	override fun performEdits(
		item: CompletionItem,
		editor: CodeEditor,
		text: Content,
		line: Int,
		column: Int,
		index: Int
	) {
		val managedFile = analysisContext.env.ktSymbolIndex.getCurrentKtFileIfPresent(analysisContext.file)
		if (managedFile == null) {
			logger.error("Unable to perform edit. File not open.")
			return
		}

		context(analysisContext) {
			performEdits(managedFile, editor, item)
		}

		if (item.command != null) {
			executeCommand(editor, item.command)
		}
	}

	context(ctx: AnalysisContext)
	abstract fun performEdits(
		ktFile: KtFile,
		editor: CodeEditor,
		item: CompletionItem
	)
}
