/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package dev.apexstudio.ide.lsp.xml

import androidx.annotation.RestrictTo
import dev.apexstudio.ide.eventbus.events.editor.DocumentChangeEvent
import dev.apexstudio.ide.lsp.api.ICompletionProvider
import dev.apexstudio.ide.lsp.api.ILanguageClient
import dev.apexstudio.ide.lsp.api.ILanguageServer
import dev.apexstudio.ide.lsp.api.IServerSettings
import dev.apexstudio.ide.lsp.models.CodeFormatResult
import dev.apexstudio.ide.lsp.models.CompletionParams
import dev.apexstudio.ide.lsp.models.CompletionResult
import dev.apexstudio.ide.lsp.models.DefinitionParams
import dev.apexstudio.ide.lsp.models.DefinitionResult
import dev.apexstudio.ide.lsp.models.DiagnosticResult
import dev.apexstudio.ide.lsp.models.ExpandSelectionParams
import dev.apexstudio.ide.lsp.models.FormatCodeParams
import dev.apexstudio.ide.lsp.models.LSPFailure
import dev.apexstudio.ide.lsp.models.ReferenceParams
import dev.apexstudio.ide.lsp.models.ReferenceResult
import dev.apexstudio.ide.lsp.models.SignatureHelp
import dev.apexstudio.ide.lsp.models.SignatureHelpParams
import dev.apexstudio.ide.lsp.util.NoCompletionsProvider
import dev.apexstudio.ide.lsp.xml.models.XMLServerSettings
import dev.apexstudio.ide.lsp.xml.providers.AdvancedEditProvider.onContentChange
import dev.apexstudio.ide.lsp.xml.providers.CodeFormatProvider
import dev.apexstudio.ide.lsp.xml.providers.XmlCompletionProvider
import dev.apexstudio.ide.models.Range
import dev.apexstudio.ide.projects.api.Workspace
import dev.apexstudio.ide.utils.DocumentUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.nio.file.Path

/**
 * Language server implementation for XML files.
 *
 * @author Akash Yadav
 */
class XMLLanguageServer : ILanguageServer {

  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  override var client: ILanguageClient? = null
    private set

  private var settings: IServerSettings? = null

  override val serverId: String = SERVER_ID

  init {
    EventBus.getDefault().register(this)
  }

  override fun shutdown() {
    if (EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().unregister(this)
    }
  }

  override fun connectClient(client: ILanguageClient?) {
    this.client = client
  }

  override fun applySettings(settings: IServerSettings?) {
    this.settings = settings
  }

  override fun setupWithProject(workspace: Workspace) {}
  override fun complete(params: CompletionParams?): CompletionResult {
    val completionProvider: ICompletionProvider = if (!getSettings().completionsEnabled()) {
      NoCompletionsProvider()
    } else {
      XmlCompletionProvider(getSettings())
    }
    return completionProvider.complete(params)
  }

  fun getSettings(): IServerSettings {
    if (settings == null) {
      settings = XMLServerSettings
    }
    return settings!!
  }

  override suspend fun findReferences(params: ReferenceParams): ReferenceResult {
    return ReferenceResult(emptyList())
  }

  override suspend fun findDefinition(params: DefinitionParams): DefinitionResult {
    return DefinitionResult(emptyList())
  }

  override suspend fun expandSelection(params: ExpandSelectionParams): Range {
    return params.selection
  }

  override suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp {
    return SignatureHelp(emptyList(), -1, -1)
  }

  override suspend fun analyze(file: Path): DiagnosticResult {
    return DiagnosticResult.NO_UPDATE
  }

  override fun formatCode(params: FormatCodeParams?): CodeFormatResult {
    return CodeFormatProvider().format(params)
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun onDocumentChange(event: DocumentChangeEvent) {
    if (!DocumentUtils.isXmlFile(event.changedFile)) {
      return
    }
    onContentChange(event)
  }

  override fun handleFailure(failure: LSPFailure?): Boolean {
    return super<ILanguageServer>.handleFailure(failure)
  }

  companion object {

    const val SERVER_ID = "ide.lsp.xml"
  }
}