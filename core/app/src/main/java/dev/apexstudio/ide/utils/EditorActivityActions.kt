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
package dev.apexstudio.ide.utils

import android.content.Context
import dev.apexstudio.ide.actions.ActionItem.Location.EDITOR_FILE_TABS
import dev.apexstudio.ide.actions.ActionItem.Location.EDITOR_FILE_TREE
import dev.apexstudio.ide.actions.ActionItem.Location.EDITOR_TOOLBAR
import dev.apexstudio.ide.actions.ActionsRegistry
import dev.apexstudio.ide.actions.build.ProjectSyncAction
import dev.apexstudio.ide.actions.build.QuickRunAction
import dev.apexstudio.ide.actions.build.RunTasksAction
import dev.apexstudio.ide.actions.editor.CopyAction
import dev.apexstudio.ide.actions.editor.CutAction
import dev.apexstudio.ide.actions.editor.ExpandSelectionAction
import dev.apexstudio.ide.actions.editor.LongSelectAction
import dev.apexstudio.ide.actions.editor.PasteAction
import dev.apexstudio.ide.actions.editor.SelectAllAction
import dev.apexstudio.ide.actions.etc.DisconnectLogSendersAction
import dev.apexstudio.ide.actions.etc.FindActionMenu
import dev.apexstudio.ide.actions.etc.LaunchAppAction
import dev.apexstudio.ide.actions.etc.PreviewLayoutAction
import dev.apexstudio.ide.actions.etc.ReloadColorSchemesAction
import dev.apexstudio.ide.actions.file.CloseAllFilesAction
import dev.apexstudio.ide.actions.file.CloseFileAction
import dev.apexstudio.ide.actions.file.CloseOtherFilesAction
import dev.apexstudio.ide.actions.file.FormatCodeAction
import dev.apexstudio.ide.actions.file.SaveFileAction
import dev.apexstudio.ide.actions.filetree.CopyPathAction
import dev.apexstudio.ide.actions.filetree.DeleteAction
import dev.apexstudio.ide.actions.filetree.NewFileAction
import dev.apexstudio.ide.actions.filetree.NewFolderAction
import dev.apexstudio.ide.actions.filetree.OpenWithAction
import dev.apexstudio.ide.actions.filetree.RenameAction
import dev.apexstudio.ide.actions.text.RedoAction
import dev.apexstudio.ide.actions.text.UndoAction

/**
 * Takes care of registering actions to the actions registry for the editor activity.
 *
 * @author Akash Yadav
 */
class EditorActivityActions {

  companion object {

    @JvmStatic
    fun register(context: Context) {
      clear()
      val registry = ActionsRegistry.getInstance()
      var order = 0

      // Toolbar actions
      registry.registerAction(UndoAction(context, order++))
      registry.registerAction(RedoAction(context, order++))
      registry.registerAction(QuickRunAction(context, order++))
      registry.registerAction(RunTasksAction(context, order++))
      registry.registerAction(SaveFileAction(context, order++))
      registry.registerAction(PreviewLayoutAction(context, order++))
      registry.registerAction(FindActionMenu(context, order++))
      registry.registerAction(ProjectSyncAction(context, order++))
      registry.registerAction(ReloadColorSchemesAction(context, order++))
      registry.registerAction(DisconnectLogSendersAction(context, order++))
      registry.registerAction(LaunchAppAction(context, order++))

      // editor text actions
      registry.registerAction(ExpandSelectionAction(context, order++))
      registry.registerAction(SelectAllAction(context, order++))
      registry.registerAction(LongSelectAction(context, order++))
      registry.registerAction(CutAction(context, order++))
      registry.registerAction(CopyAction(context, order++))
      registry.registerAction(PasteAction(context, order++))
      registry.registerAction(FormatCodeAction(context, order++))

      // file tab actions
      registry.registerAction(CloseFileAction(context, order++))
      registry.registerAction(CloseOtherFilesAction(context, order++))
      registry.registerAction(CloseAllFilesAction(context, order++))

      // file tree actions
      registry.registerAction(CopyPathAction(context, order++))
      registry.registerAction(DeleteAction(context, order++))
      registry.registerAction(NewFileAction(context, order++))
      registry.registerAction(NewFolderAction(context, order++))
      registry.registerAction(OpenWithAction(context, order++))
      registry.registerAction(RenameAction(context, order++))
    }

    @JvmStatic
    fun clear() {
      // EDITOR_TEXT_ACTIONS should not be cleared as the language servers register actions there as
      // well
      val locations = arrayOf(EDITOR_TOOLBAR, EDITOR_FILE_TABS, EDITOR_FILE_TREE)
      val registry = ActionsRegistry.getInstance()
      locations.forEach(registry::clearActions)
    }
  }
}
