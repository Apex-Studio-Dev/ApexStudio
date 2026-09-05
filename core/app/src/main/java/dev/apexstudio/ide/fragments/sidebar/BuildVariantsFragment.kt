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

package dev.apexstudio.ide.fragments.sidebar

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import dev.apexstudio.ide.activities.editor.ProjectHandlerActivity
import dev.apexstudio.ide.adapters.BuildVariantsAdapter
import dev.apexstudio.ide.databinding.FragmentBuildVariantsBinding
import dev.apexstudio.ide.fragments.EmptyStateFragment
import dev.apexstudio.ide.tooling.api.models.BuildVariantInfo
import dev.apexstudio.ide.viewmodel.BuildVariantsViewModel
import dev.apexstudio.ide.viewmodel.EditorViewModel

/**
 * A fragment to show the list of Android modules and its build variants.
 *
 * @author Akash Yadav
 */
class BuildVariantsFragment :
  EmptyStateFragment<FragmentBuildVariantsBinding>(FragmentBuildVariantsBinding::inflate) {

  private val variantsViewModel by viewModels<BuildVariantsViewModel>(
    ownerProducer = { requireActivity() }
  )

  private val editorViewModel by viewModels<EditorViewModel>(
    ownerProducer = { requireActivity() }
  )

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    variantsViewModel._buildVariants.observe(viewLifecycleOwner) {
      populateRecyclerView()
      updateButtonStates(variantsViewModel.updatedBuildVariants)
    }

    // observe values and update the button states accordingly
    variantsViewModel._updatedBuildVariants.observe(viewLifecycleOwner) { updatedVariants ->
      updateButtonStates(updatedVariants)
    }

    editorViewModel._isBuildInProgress.observe(viewLifecycleOwner) {
      updateButtonStates(variantsViewModel.updatedBuildVariants)
    }

    editorViewModel._isInitializing.observe(viewLifecycleOwner) {
      updateButtonStates(variantsViewModel.updatedBuildVariants)
    }

    binding.apply.setOnClickListener {
      (activity as? ProjectHandlerActivity?)?.initializeProject()
    }

    binding.discard.setOnClickListener {
      variantsViewModel.resetUpdatedSelections()
      populateRecyclerView()
    }

    binding.variantsList.addItemDecoration(
      DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
    )

    populateRecyclerView()
  }

  private fun updateButtonStates(
    updatedVariants: MutableMap<String, BuildVariantInfo>?
  ) {
    _binding?.apply {
      // enable buttons only if any of the project's selected build variant was changed
      // also, changes can only if be applied if no build is in progress
      val isBuilding = editorViewModel.let { it.isBuildInProgress || it.isInitializing }
      val isEnabled = updatedVariants?.isNotEmpty() == true && !isBuilding

      apply.isEnabled = isEnabled
      discard.isEnabled = isEnabled
    }
  }

  private fun populateRecyclerView() {
    _binding?.variantsList?.apply {
      this.adapter = BuildVariantsAdapter(variantsViewModel,
        variantsViewModel.buildVariants.values.toList())
      checkIsEmpty()
    }
  }

  private fun checkIsEmpty() {
    emptyStateViewModel.setEmpty(_binding?.variantsList?.adapter?.itemCount == 0)
  }
}