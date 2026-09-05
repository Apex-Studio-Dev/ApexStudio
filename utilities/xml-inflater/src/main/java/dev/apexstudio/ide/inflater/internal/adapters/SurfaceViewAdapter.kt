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

import android.view.SurfaceView
import dev.apexstudio.ide.annotations.uidesigner.IncludeInDesigner
import dev.apexstudio.ide.annotations.uidesigner.IncludeInDesigner.Group.WIDGETS
import dev.apexstudio.ide.inflater.models.UiWidget
import dev.apexstudio.ide.resources.R.drawable
import dev.apexstudio.ide.resources.R.string

/**
 * Attribute adapter for [SurfaceView].
 *
 * @author Akash Yadav
 */
@dev.apexstudio.ide.annotations.inflater.ViewAdapter(SurfaceView::class)
@IncludeInDesigner(group = WIDGETS)
open class SurfaceViewAdapter<T : SurfaceView> : ViewAdapter<T>() {
  override fun createUiWidgets(): List<UiWidget> {
    return listOf(
      UiWidget(SurfaceView::class.java, string.widget_surfaceview, drawable.ic_widget_surface_view)
    )
  }
}
