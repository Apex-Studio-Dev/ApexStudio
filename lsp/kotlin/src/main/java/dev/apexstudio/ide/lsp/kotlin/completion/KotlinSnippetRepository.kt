package dev.apexstudio.ide.lsp.kotlin.completion

import dev.apexstudio.ide.lsp.snippets.ISnippet
import dev.apexstudio.ide.lsp.snippets.SnippetParser
import dev.apexstudio.ide.lsp.snippets.SnippetRegistry

object KotlinSnippetRepository {
	val snippets: Map<KotlinSnippetScope, List<ISnippet>>
		get() = KotlinSnippetScope.entries.associateWith { scope ->
			SnippetRegistry.getSnippets("kt", scope.filename)
		}

	fun init() {
		SnippetRegistry.initBuiltIn("kt", KotlinSnippetScope.entries)
	}
}