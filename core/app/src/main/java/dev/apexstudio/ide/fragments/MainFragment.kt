/*
 *  This file is part of ApexStudio.
 *
 *  ApexStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ApexStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with ApexStudio.  If not, see <https://www.gnu.org/licenses/>.
 */
package dev.apexstudio.ide.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.termux.app.TermuxInstaller
import dev.apexstudio.ide.R
import dev.apexstudio.ide.activities.MainActivity
import dev.apexstudio.ide.activities.TerminalActivity
import dev.apexstudio.ide.adapters.RecentProjectsAdapter
import dev.apexstudio.ide.databinding.FragmentProjectHomeBinding
import dev.apexstudio.ide.utils.EnvPackages
import dev.apexstudio.ide.utils.Environment.PROJECTS_DIR
import dev.apexstudio.ide.utils.findValidProjects
import dev.apexstudio.ide.utils.viewLifecycleScope
import dev.apexstudio.ide.viewmodel.MainViewModel
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_CLONE_REPO
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_SAVED_PROJECTS
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_SDK_MANAGER
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_TEMPLATE_LIST
import dev.apexstudio.ide.viewmodel.RecentProjectsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import java.io.File

/**
 * The "Project" home screen shown in the main panel.
 *
 * Offers Create Project / Clone Project shortcuts and a preview of the most
 * recently opened projects (with a "More" link to the full list).
 *
 * @author Apex Studio Dev
 */
class MainFragment : BaseFragment() {

  private val mainViewModel by activityViewModel<MainViewModel>()
  private val recentsViewModel by activityViewModels<RecentProjectsViewModel>()

  private var binding: FragmentProjectHomeBinding? = null
  private var adapter: RecentProjectsAdapter? = null

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = FragmentProjectHomeBinding.inflate(inflater, container, false)
    return binding!!.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val binding = binding ?: return

    binding.recentProjectsPreview.layoutManager = LinearLayoutManager(requireContext())
    binding.recentProjectsPreview.isNestedScrollingEnabled = false

    binding.btnCreateProject.setOnClickListener {
      if (requireEnvSetup()) {
        mainViewModel.setScreen(SCREEN_TEMPLATE_LIST)
      }
    }

    binding.btnCloneProject.setOnClickListener {
      if (requireEnvSetup()) {
        mainViewModel.setScreen(SCREEN_CLONE_REPO)
      }
    }

    binding.btnShowAllRecent.setOnClickListener {
      mainViewModel.setScreen(SCREEN_SAVED_PROJECTS)
    }

    binding.btnOpenTerminal.setOnClickListener {
      if (requireSetup()) {
        startActivity(Intent(requireContext(), TerminalActivity::class.java))
      }
    }

    binding.setupBanner.setOnClickListener {
      mainViewModel.setScreen(SCREEN_SDK_MANAGER)
    }
    binding.btnSetupNow.setOnClickListener {
      mainViewModel.setScreen(SCREEN_SDK_MANAGER)
    }

    observeRecents()
    bootstrapFromFixedFolderIfNeeded()
  }

  override fun onResume() {
    super.onResume()
    recentsViewModel.loadProjects()
    refreshSetupBanner()
  }

  private fun refreshSetupBanner() {
    viewLifecycleOwner.lifecycleScope.launch {
      val (bootstrapInstalled, missing) = withContext(Dispatchers.IO) {
        TermuxInstaller.isBootstrapInstalled() to EnvPackages.missingEnvPackages()
      }
      val binding = binding ?: return@launch
      when {
        !bootstrapInstalled -> {
          binding.tvBannerTitle.setText(R.string.title_setup_not_completed)
          binding.tvBannerMsg.setText(R.string.msg_setup_not_completed)
          binding.setupBanner.isVisible = true
        }

        missing.isNotEmpty() -> {
          binding.tvBannerTitle.setText(R.string.title_env_not_completed)
          binding.tvBannerMsg.setText(
            requireContext().getString(R.string.msg_env_missing, missing.joinToString(", ")),
          )
          binding.setupBanner.isVisible = true
        }

        else -> binding.setupBanner.isVisible = false
      }
    }
  }

  private fun observeRecents() {
    recentsViewModel.projects.observe(viewLifecycleOwner) { projects ->
      val binding = binding ?: return@observe
      val preview = projects.take(3)

      val currentAdapter = adapter ?: RecentProjectsAdapter(
        preview,
        onProjectClick = ::openProject,
        onRemoveProjectClick = recentsViewModel::deleteProject,
        onFileRenamed = recentsViewModel::updateProject,
        onInfoClick = { project -> openProjectInfo(project) },
        nameExists = recentsViewModel::projectNameExists
      ).also {
        adapter = it
        binding.recentProjectsPreview.adapter = it
      }
      currentAdapter.updateProjects(preview)

      binding.btnShowAllRecent.isVisible = projects.size > 3
      binding.tvNoProjects.isVisible = projects.isEmpty()
    }
  }

  private fun openProjectInfo(project: dev.apexstudio.ide.models.ProjectFile) {
    viewLifecycleOwner.lifecycleScope.launch {
      val recentProject = recentsViewModel.getProjectByName(project.name)
      val sheet = dev.apexstudio.ide.ui.ProjectInfoBottomSheet.newInstance(project, recentProject)
      sheet.show(parentFragmentManager, "project_info_sheet")
    }
  }

  private fun bootstrapFromFixedFolderIfNeeded() {
    if (recentsViewModel.didBootstrap) return
    recentsViewModel.didBootstrap = true

    viewLifecycleScope.launch(Dispatchers.IO) {
      try {
        val validProjects = findValidProjects(PROJECTS_DIR)
        if (validProjects.isEmpty()) return@launch

        val jobs = validProjects.map { dir ->
          recentsViewModel.insertProjectFromFolder(dir.name, dir.absolutePath)
        }
        jobs.joinAll()
        recentsViewModel.loadProjects().join()
      } catch (e: Throwable) {
        e.printStackTrace()
      }
    }
  }

  private fun requireSetup(): Boolean =
    (requireActivity() as MainActivity).requireBootstrapSetup()

  private fun requireEnvSetup(): Boolean =
    (requireActivity() as MainActivity).requireEnvPackagesSetup()

  fun openProject(root: File) {
    (requireActivity() as MainActivity).openProject(root)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    binding = null
  }
}