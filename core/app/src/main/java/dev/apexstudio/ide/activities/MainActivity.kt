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

package dev.apexstudio.ide.activities

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.Insets
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.transition.TransitionManager
import androidx.transition.doOnEnd
import com.google.android.material.transition.MaterialSharedAxis
import dev.apexstudio.ide.R
import dev.apexstudio.ide.activities.editor.EditorActivityKt
import dev.apexstudio.ide.app.BaseApplication
import dev.apexstudio.ide.app.EdgeToEdgeIDEActivity
import dev.apexstudio.ide.databinding.ActivityMainBinding
import dev.apexstudio.ide.preferences.internal.GeneralPreferences
import dev.apexstudio.ide.projects.ProjectManagerImpl
import dev.apexstudio.ide.resources.R.string
import dev.apexstudio.ide.roomData.recentproject.RecentProject
import dev.apexstudio.ide.templates.ITemplateProvider
import dev.apexstudio.ide.utils.DialogUtils
import dev.apexstudio.ide.utils.EnvPackages
import dev.apexstudio.ide.utils.flashInfo
import dev.apexstudio.ide.utils.getCreatedTime
import dev.apexstudio.ide.utils.getLastModifiedTime
import dev.apexstudio.ide.utils.readProjectLanguage
import dev.apexstudio.ide.viewmodel.MainViewModel
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_CLONE_REPO
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_DELETE_PROJECTS
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_MAIN
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_SAVED_PROJECTS
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_TEMPLATE_DETAILS
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_TEMPLATE_LIST
import com.termux.app.TermuxInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File

class MainActivity : EdgeToEdgeIDEActivity() {

    private val viewModel by viewModel<MainViewModel>()
    private var _binding: ActivityMainBinding? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            viewModel.apply {

                // Ignore back press if project creating is in progress
                if (creatingProject.value == true) {
                    return@apply
                }

                val newScreen = when (currentScreen.value) {
                    SCREEN_TEMPLATE_DETAILS -> SCREEN_TEMPLATE_LIST
                    SCREEN_TEMPLATE_LIST -> SCREEN_MAIN
                    else -> SCREEN_MAIN
                }

                if (currentScreen.value != newScreen) {
                    setScreen(newScreen)
                }
            }
        }
    }

    private val binding: ActivityMainBinding
        get() = checkNotNull(_binding)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openLastProject()

        viewModel.currentScreen.observe(this) { screen ->
            if (screen == -1) {
                return@observe
            }

            onScreenChanged(screen)
            onBackPressedCallback.isEnabled = screen != SCREEN_MAIN
        }

        // Data in a ViewModel is kept between activity rebuilds on
        // configuration changes (i.e. screen rotation)
        // * previous == -1 and current == -1 -> this is an initial instantiation of the activity
        if (viewModel.currentScreen.value == -1 && viewModel.previousScreen == -1) {
            viewModel.setScreen(SCREEN_MAIN)
        } else {
            onScreenChanged(viewModel.currentScreen.value)
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        val onClick = { _: View ->
            if (!TermuxInstaller.isBootstrapInstalled()) {
                launchIdeSetup()
            } else {
                launchSdkManager()
            }
        }
        binding.setupBanner.setOnClickListener(onClick)
        binding.btnSetupNow.setOnClickListener(onClick)

        setupNavigationDrawer()
    }

    private fun setupNavigationDrawer() {
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_home -> if (viewModel.currentScreen.value != SCREEN_MAIN) {
                    viewModel.setScreen(SCREEN_MAIN)
                }

                R.id.nav_recent_projects -> if (requireEnvPackagesSetup()) {
                    viewModel.setScreen(SCREEN_SAVED_PROJECTS)
                }

                R.id.nav_create_project -> if (requireEnvPackagesSetup()) {
                    viewModel.setScreen(SCREEN_TEMPLATE_LIST)
                }

                R.id.nav_clone_repo -> if (requireEnvPackagesSetup()) {
                    viewModel.setScreen(SCREEN_CLONE_REPO)
                }

                R.id.nav_terminal -> if (requireBootstrapSetup()) {
                    startActivity(Intent(this, TerminalActivity::class.java))
                }

                R.id.nav_sdk_manager -> startActivity(Intent(this, SdkManagerActivity::class.java))
                R.id.nav_preferences -> startActivity(
                    Intent(this, PreferencesActivity::class.java))
                R.id.nav_donate -> BaseApplication.getBaseInstance().openDonationsPage()
                R.id.nav_docs -> BaseApplication.getBaseInstance().openDocs()
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSetupBanner()
    }

    private fun refreshSetupBanner() {
        lifecycleScope.launch {
            val (bootstrapInstalled, missing) = withContext(Dispatchers.IO) {
                TermuxInstaller.isBootstrapInstalled() to EnvPackages.missingEnvPackages()
            }
            when {
                !bootstrapInstalled -> {
                    binding.tvBannerTitle.setText(R.string.title_setup_not_completed)
                    binding.tvBannerMsg.setText(R.string.msg_setup_not_completed)
                    binding.setupBanner.isVisible = true
                }

                missing.isNotEmpty() -> {
                    binding.tvBannerTitle.setText(R.string.title_env_not_completed)
                    binding.tvBannerMsg.setText(
                        getString(R.string.msg_env_missing, missing.joinToString(", ")),
                    )
                    binding.setupBanner.isVisible = true
                }

                else -> binding.setupBanner.isVisible = false
            }
        }
    }

private fun launchIdeSetup() {
    startActivity(Intent(this, SetupActivity::class.java))
  }

  private fun launchSdkManager() {
    startActivity(Intent(this, SdkManagerActivity::class.java))
  }

    /** @return true if bootstrap is installed and the user may continue; otherwise opens the setup screen. */
    fun requireBootstrapSetup(): Boolean {
        if (TermuxInstaller.isBootstrapInstalled()) {
            return true
        }
        launchIdeSetup()
        return false
    }

    /** @return true if the environment packages are installed and the user may continue; otherwise opens the SDK manager screen. */
    fun requireEnvPackagesSetup(): Boolean {
        if (!TermuxInstaller.isBootstrapInstalled()) {
            launchIdeSetup()
            return false
        }
        if (EnvPackages.areEnvPackagesInstalled()) {
            return true
        }
        launchSdkManager()
        return false
    }

    override fun onApplySystemBarInsets(insets: Insets) {
        binding.fragmentContainersParent.setPadding(
            insets.left,
            0,
            insets.right,
            insets.bottom
        )
    }

    private fun onScreenChanged(screen: Int?) {
        val previous = viewModel.previousScreen
        if (previous != -1) {
            // template list -> template details
            // ------- OR -------
            // template details -> template list
            val setAxisToX =
                (previous == SCREEN_TEMPLATE_LIST || previous == SCREEN_TEMPLATE_DETAILS) && (screen == SCREEN_TEMPLATE_LIST || screen == SCREEN_TEMPLATE_DETAILS)

            val axis = if (setAxisToX) {
                MaterialSharedAxis.X
            } else {
                MaterialSharedAxis.Y
            }

            val isForward = (screen ?: 0) - previous == 1

            val transition = MaterialSharedAxis(axis, isForward)
            transition.doOnEnd {
                viewModel.isTransitionInProgress = false
                onBackPressedCallback.isEnabled = viewModel.currentScreen.value != SCREEN_MAIN
            }

            viewModel.isTransitionInProgress = true
            TransitionManager.beginDelayedTransition(binding.root, transition)
        }

        val currentFragment = when (screen) {
            SCREEN_MAIN -> binding.main
            SCREEN_TEMPLATE_LIST -> binding.templateList
            SCREEN_TEMPLATE_DETAILS -> binding.templateDetails
            SCREEN_SAVED_PROJECTS -> binding.savedProjectsView
            SCREEN_DELETE_PROJECTS -> binding.deleteProjectsView
            SCREEN_CLONE_REPO -> binding.cloneRepositoryView
            else -> throw IllegalArgumentException("Invalid screen id: '$screen'")
        }

        for (fragment in arrayOf(
            binding.main,
            binding.templateList,
            binding.templateDetails,
            binding.savedProjectsView,
            binding.deleteProjectsView,
            binding.cloneRepositoryView
        )) {
            fragment.isVisible = fragment == currentFragment
        }
    }

    override fun bindLayout(): View {
        _binding = ActivityMainBinding.inflate(layoutInflater)
        return binding.root
    }

    private fun openLastProject() {
        binding.root.post { tryOpenLastProject() }
    }

    private fun tryOpenLastProject() {
        if (!requireBootstrapSetup()) {
            return
        }

        if (!EnvPackages.areEnvPackagesInstalled()) {
            return
        }

        if (!GeneralPreferences.autoOpenProjects) {
            return
        }

        val openedProject = GeneralPreferences.lastOpenedProject
        if (GeneralPreferences.NO_OPENED_PROJECT == openedProject) {
            return
        }

        if (TextUtils.isEmpty(openedProject)) {
            app
            flashInfo(string.msg_opened_project_does_not_exist)
            return
        }

        val project = File(openedProject)
        if (!project.exists()) {
            flashInfo(string.msg_opened_project_does_not_exist)
            return
        }

        if (GeneralPreferences.confirmProjectOpen) {
            askProjectOpenPermission(project)
            return
        }

        openProject(project)
    }

    private fun askProjectOpenPermission(root: File) {
        val builder = DialogUtils.newMaterialDialogBuilder(this)
        builder.setTitle(string.title_confirm_open_project)
        builder.setMessage(getString(string.msg_confirm_open_project, root.absolutePath))
        builder.setCancelable(false)
        builder.setPositiveButton(string.yes) { _, _ -> openProject(root) }
        builder.setNegativeButton(string.no, null)
        builder.show()
    }

    internal fun openProject(
        root: File,
        project: RecentProject? = null,
        hasTemplateIssues: Boolean = false,
    ) {
        if (!requireEnvPackagesSetup()) {
            return
        }

        ProjectManagerImpl.getInstance().projectPath = root.absolutePath
        GeneralPreferences.lastOpenedProject = root.absolutePath

        lifecycleScope.launch(Dispatchers.IO) {
            val location = root.absolutePath
            val recentProject =
                project ?: RecentProject(
                    name = root.name,
                    location = location,
                    createdAt = getCreatedTime(location).toString(),
                    lastModified = getLastModifiedTime(location).toString(),
                    language = readProjectLanguage(root),
                )
            viewModel.saveProjectToRecents(recentProject)
        }

        if (isFinishing) {
            return
        }

        val intent =
            Intent(this, EditorActivityKt::class.java).apply {
                putExtra("PROJECT_PATH", root.absolutePath)
                if (hasTemplateIssues) {
                    putExtra("HAS_TEMPLATE_ISSUES", true)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        startActivity(intent)
    }

    override fun onDestroy() {
        ITemplateProvider.getInstance().release()
        super.onDestroy()
        _binding = null
    }
}
