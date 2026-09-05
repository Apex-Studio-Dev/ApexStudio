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

package dev.apexstudio.ide.inflater.internal.adapters

import android.widget.ViewSwitcher
import dev.apexstudio.ide.annotations.inflater.ViewAdapter
import dev.apexstudio.ide.annotations.uidesigner.IncludeInDesigner
import dev.apexstudio.ide.annotations.uidesigner.IncludeInDesigner.Group.LAYOUTS
import dev.apexstudio.ide.inflater.models.UiWidget
import dev.apexstudio.ide.resources.R.drawable
import dev.apexstudio.ide.resources.R.string

/**
 * Attribute adapter for [ViewSwitcher].
 *
 * @author Deep Kr. Ghosh
 */
@ViewAdapter(ViewSwitcher::class)
@IncludeInDesigner(group = LAYOUTS)
open class ViewSwitcherAdapter<T : ViewSwitcher> : ViewAnimatorAdapter<T>() {
  override fun createUiWidgets(): List<UiWidget> {
    return listOf(
      UiWidget(
        ViewSwitcher::class.java,
        string.widget_viewswitcher,
        drawable.ic_widget_viewswitcher
      )
    )
  }
}
