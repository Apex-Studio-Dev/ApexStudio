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

package dev.apexstudio.ide.uidesigner.fragments

// import dev.apexstudio.ide.uidesigner.utils.applyBackgroundPreview
import dev.apexstudio.ide.inflater.internal.ViewImpl
import dev.apexstudio.ide.uidesigner.models.UiAttribute
import dev.apexstudio.ide.uidesigner.undo.AttrAddedAction
import dev.apexstudio.ide.uidesigner.undo.AttrRemovedAction
import dev.apexstudio.ide.uidesigner.undo.AttrUpdatedAction
import dev.apexstudio.ide.uidesigner.utils.applyAttributePreview

/**
 * Handles view attribute changes in [DesignerWorkspaceFragment].
 *
 * @author Akash Yadav
 */
internal class WorkspaceViewAttrHandler : dev.apexstudio.ide.inflater.IView.AttributeChangeListener {

  private var fragment: DesignerWorkspaceFragment? = null

  internal fun init(fragment: DesignerWorkspaceFragment) {
    this.fragment = fragment
  }

  internal fun release() {
    this.fragment = null
  }

  override fun onAttributeAdded(
      view: dev.apexstudio.ide.inflater.IView,
      attribute: dev.apexstudio.ide.inflater.IAttribute,
  ) {
    val frag = this.fragment ?: return
    frag.undoManager.push(AttrAddedAction(view = view, attr = attribute as UiAttribute))
  }

  override fun onAttributeRemoved(
      view: dev.apexstudio.ide.inflater.IView,
      attribute: dev.apexstudio.ide.inflater.IAttribute,
  ) {
    val frag = this.fragment ?: return
    frag.undoManager.push(AttrRemovedAction(view = view, attr = attribute as UiAttribute))
  }

  override fun onAttributeUpdated(
      view: dev.apexstudio.ide.inflater.IView,
      attribute: dev.apexstudio.ide.inflater.IAttribute,
      oldValue: String,
  ) {
    val frag = this.fragment ?: return

    // if (attribute.name.equals("background", ignoreCase = true) && view is ViewImpl) {
    // // Get the layout file from the fragment's viewModel
    // val layoutFile = frag.viewModel.file
    // view.applyBackgroundPreview(frag.requireContext(), layoutFile)
    // }
    if (view is ViewImpl) {
      val layoutFile = frag.viewModel.file
      (view as ViewImpl).applyAttributePreview(
          attribute.name,
          attribute.value,
          frag.requireContext(),
          null,
          layoutFile,
      )
    }

    frag.undoManager.push(
        AttrUpdatedAction(view = view, attr = attribute as UiAttribute, oldValue = oldValue)
    )
  }
}
