package dev.apexstudio.ide.lsp.kotlin

import dev.apexstudio.ide.actions.ActionItem
import dev.apexstudio.ide.lsp.actions.CommentLineAction
import dev.apexstudio.ide.lsp.actions.IActionsMenuProvider
import dev.apexstudio.ide.lsp.actions.SurroundWithTryCatchAction
import dev.apexstudio.ide.lsp.actions.UncommentLineAction
import dev.apexstudio.ide.lsp.kotlin.actions.AddImportAction
import dev.apexstudio.ide.lsp.kotlin.actions.ExtractMethodAction
import dev.apexstudio.ide.lsp.kotlin.actions.ExtractVariableAction
import dev.apexstudio.ide.lsp.kotlin.actions.FindReferencesAction
import dev.apexstudio.ide.lsp.kotlin.actions.GoToDefinitionAction
import dev.apexstudio.ide.lsp.kotlin.actions.ImplementMembersAction
import dev.apexstudio.ide.lsp.kotlin.actions.NullSafetyAction
import dev.apexstudio.ide.lsp.kotlin.actions.OrganizeImportsAction

object KotlinCodeActionsMenu : IActionsMenuProvider {
	internal const val KT_LANG = "kt"
	private val KT_EXTS = listOf("kt", "kts")
	private const val KT_LINE_COMMENT_TOKEN = "//"
	private const val KT_CATCH_CLAUSE = "catch (e: Exception)"
	private const val KT_CATCH_BODY = "e.printStackTrace()"

	override val actions: List<ActionItem> =
		listOf(
			CommentLineAction(
				KT_LANG,
				KT_EXTS,
				KT_LINE_COMMENT_TOKEN,
			),
			UncommentLineAction(
				KT_LANG,
				KT_EXTS,
				KT_LINE_COMMENT_TOKEN,
			),
			GoToDefinitionAction(),
			FindReferencesAction(),
			AddImportAction(),
			OrganizeImportsAction(),
			SurroundWithTryCatchAction(
				KT_LANG,
				KT_EXTS,
				KotlinLanguageServer.SERVER_ID,
				KT_CATCH_CLAUSE,
				KT_CATCH_BODY,
			),
			NullSafetyAction(),
			ImplementMembersAction(),
			ExtractVariableAction(),
			ExtractMethodAction(),
		)
}
