package dev.apexstudio.ide.lsp.xml.providers.snippet

import dev.apexstudio.ide.lsp.snippets.ISnippet
import dev.apexstudio.ide.lsp.snippets.SnippetRegistry

object XmlSnippetRepository {

    val snippets: Map<IXmlSnippetScope, List<ISnippet>>
        get() = XML_SNIPPET_SCOPES.associateWith { scope ->
            SnippetRegistry.getSnippets("xml",scope.filename)
        }

    fun init() {
        SnippetRegistry.initBuiltIn("xml", XML_SNIPPET_SCOPES)
    }
}
