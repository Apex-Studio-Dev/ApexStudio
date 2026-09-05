package dev.apexstudio.ide.lsp.kotlin.completion

import dev.apexstudio.ide.editor.api.ILspEditor
import dev.apexstudio.ide.lsp.edits.DefaultEditHandler
import dev.apexstudio.ide.lsp.models.Command
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Implementation of [DefaultEditHandler] which avoids reflection in
 * [DefaultEditHandler.executeCommand].
 *
 * @author Akash Yadav
 */
open class BaseKotlinEditHandler : DefaultEditHandler() {

	override fun executeCommand(editor: CodeEditor, command: Command?) {
		if (editor is ILspEditor) {
			editor.executeCommand(command)
			return
		}
		super.executeCommand(editor, command)
	}
}
