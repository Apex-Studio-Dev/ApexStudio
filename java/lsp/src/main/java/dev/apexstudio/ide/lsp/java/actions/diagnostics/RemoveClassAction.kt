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
package dev.apexstudio.ide.lsp.java.actions.diagnostics

import dev.apexstudio.ide.actions.ActionData
import dev.apexstudio.ide.actions.hasRequiredData
import dev.apexstudio.ide.actions.markInvisible
import dev.apexstudio.ide.actions.requireFile
import dev.apexstudio.ide.actions.requirePath
import dev.apexstudio.ide.lsp.java.JavaCompilerProvider
import dev.apexstudio.ide.lsp.java.actions.BaseJavaCodeAction
import dev.apexstudio.ide.lsp.java.models.DiagnosticCode
import dev.apexstudio.ide.lsp.java.Rewrite.RemoveClass
import dev.apexstudio.ide.lsp.java.utils.CodeActionUtils.findPosition
import dev.apexstudio.ide.projects.IProjectManager
import dev.apexstudio.ide.resources.R
import org.slf4j.LoggerFactory

/** @author Akash Yadav */
class RemoveClassAction : BaseJavaCodeAction() {

  override val id: String = "ide.editor.lsp.java.diagnostics.removeClass"
  override var label: String = ""
  private val diagnosticCode = DiagnosticCode.UNUSED_CLASS.id

  override val titleTextRes: Int = R.string.action_remove_class

  companion object {

    private val log = LoggerFactory.getLogger(RemoveClassAction::class.java)
  }

  override fun prepare(data: ActionData) {
    super.prepare(data)

    if (!visible || !data.hasRequiredData(
        dev.apexstudio.ide.lsp.models.DiagnosticItem::class.java)
    ) {
      markInvisible()
      return
    }

    val diagnostic = data[dev.apexstudio.ide.lsp.models.DiagnosticItem::class.java]!!
    if (diagnosticCode != diagnostic.code) {
      markInvisible()
      return
    }
  }

  override suspend fun execAction(data: ActionData): Any {
    val diagnostic = data[dev.apexstudio.ide.lsp.models.DiagnosticItem::class.java]!!
    val compiler =
      JavaCompilerProvider.get(
        IProjectManager.getInstance().workspace?.findModuleForFile(data.requireFile(), false)
          ?: return Any()
      )
    val file = data.requirePath()

    return compiler.compile(file).get {
      RemoveClass(file, findPosition(it, diagnostic.range.start))
    }
  }

  override fun postExec(data: ActionData, result: Any) {
    if (result !is RemoveClass) {
      log.warn("Unable to remove class")
      return
    }

    performCodeAction(data, result)
  }
}
