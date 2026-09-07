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

package dev.apexstudio.ide.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import com.google.android.material.transition.MaterialSharedAxis
import dev.apexstudio.ide.R
import dev.apexstudio.ide.preferences.IPreference
import dev.apexstudio.ide.preferences.IPreferenceGroup
import dev.apexstudio.ide.preferences.IPreferenceScreen

class IDEPreferencesFragment : BasePreferenceFragment() {

  private var children: List<IPreference> = emptyList()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
    reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
    return super.onCreateView(inflater, container, savedInstanceState)
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)

    if (context == null) {
      return
    }

    @Suppress("DEPRECATION")
    this.children = arguments?.getParcelableArrayList(EXTRA_CHILDREN) ?: emptyList()

    preferenceScreen.removeAll()
    addChildren(this.children, preferenceScreen)
  }

  private fun addChildren(children: List<IPreference>, pref: PreferenceGroup) {
    // Card-style rows at this level are every preference except category headers.
    // All rows of a category are grouped into a single card: the first row rounds
    // the top corners, middle rows stay square and the last one rounds the bottom.
    val cards = children.filter { it !is IPreferenceGroup || it is IPreferenceScreen }

    for (child in children) {
      val preference = child.onCreateView(requireContext())
      if (child is IPreferenceScreen) {
        preference.fragment = IDEPreferencesFragment::class.java.name
        preference.extras.putParcelableArrayList(EXTRA_CHILDREN, ArrayList(child.children))
      } else if (child is IPreferenceGroup) {
        pref.addPreference(preference as PreferenceCategory)
        addChildren(child.children, preference)
        continue
      } else if (preference is androidx.preference.SwitchPreference) {
        // Make sure an inline switch is always rendered for switch preferences.
        preference.widgetLayoutResource = R.layout.preference_widget_materialswitch
      }

      val index = cards.indexOf(child)
      preference.layoutResource =
        when {
          cards.size == 1 -> R.layout.layout_preference_card
          index == 0 -> R.layout.layout_preference_card_top
          index == cards.lastIndex -> R.layout.layout_preference_card_bottom
          else -> R.layout.layout_preference_card_middle
        }
      pref.addPreference(preference)
    }
  }

  companion object {
    const val EXTRA_CHILDREN = "ide.preferences.fragment.children"
  }
}
