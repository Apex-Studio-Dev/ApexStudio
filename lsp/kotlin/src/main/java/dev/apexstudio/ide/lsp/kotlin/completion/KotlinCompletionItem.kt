package dev.apexstudio.ide.lsp.kotlin.completion

import dev.apexstudio.ide.lsp.edits.IEditHandler
import dev.apexstudio.ide.lsp.models.Command
import dev.apexstudio.ide.lsp.models.CompletionItem
import dev.apexstudio.ide.lsp.models.CompletionItemKind
import dev.apexstudio.ide.lsp.models.ICompletionData
import dev.apexstudio.ide.lsp.models.InsertTextFormat
import dev.apexstudio.ide.lsp.models.MatchLevel
import dev.apexstudio.ide.lsp.models.TextEdit

class KotlinCompletionItem(
	ideLabel: String,
	detail: String,
	insertText: String?,
	insertTextFormat: InsertTextFormat?,
	sortText: String?,
	command: Command?,
	completionKind: CompletionItemKind,
	matchLevel: MatchLevel,
	additionalTextEdits: List<TextEdit>?,
	data: ICompletionData?,
	editHandler: IEditHandler = BaseKotlinEditHandler()
) : CompletionItem(
	ideLabel,
	detail,
	insertText,
	insertTextFormat,
	sortText,
	command,
	completionKind,
	matchLevel,
	additionalTextEdits,
	data,
	editHandler
) {

	constructor() : this(
		"", // label
		"", // detail
		null, // insertText
		null, // insertTextFormat
		null, // sortText
		null, // command
		CompletionItemKind.NONE, // kind
		MatchLevel.NO_MATCH, // match level
		ArrayList(), // additionalEdits
		null // data
	)
}