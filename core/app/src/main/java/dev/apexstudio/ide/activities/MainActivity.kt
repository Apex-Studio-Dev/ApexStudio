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
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.transition.TransitionManager
import androidx.transition.doOnEnd
import com.google.android.material.transition.MaterialSharedAxis
import dev.apexstudio.ide.R
import dev.apexstudio.ide.activities.editor.EditorActivityKt
import dev.apexstudio.ide.app.EdgeToEdgeIDEActivity
import dev.apexstudio.ide.databinding.ActivityMainBinding
import dev.apexstudio.ide.fragments.AboutPanelFragment
import dev.apexstudio.ide.fragments.IDEPreferencesFragment
import dev.apexstudio.ide.fragments.SdkManagerFragment
import dev.apexstudio.ide.preferences.IDEPreferences as prefs
import dev.apexstudio.ide.preferences.addRootPreferences
import dev.apexstudio.ide.preferences.internal.GeneralPreferences
import dev.apexstudio.ide.projects.ProjectManagerImpl
import dev.apexstudio.ide.resources.R.string
import dev.apexstudio.ide.roomData.recentproject.RecentProject
import dev.apexstudio.ide.templates.ITemplateProvider
import dev.apexstudio.ide.utils.DialogUtils
import dev.apexstudio.ide.utils.EnvPackages
import dev.apexstudio.ide.utils.flashInfo
import dev.apexstudio.ide.utils.flashSuccess
import dev.apexstudio.ide.utils.getCreatedTime
import dev.apexstudio.ide.utils.getLastModifiedTime
import dev.apexstudio.ide.utils.readProjectLanguage
import dev.apexstudio.ide.viewmodel.MainViewModel
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_ABOUT
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_CLONE_REPO
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_DELETE_PROJECTS
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_MAIN
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_SAVED_PROJECTS
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_SDK_MANAGER
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_SETTINGS
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_TEMPLATE_DETAILS
import dev.apexstudio.ide.viewmodel.MainViewModel.Companion.SCREEN_TEMPLATE_LIST
import com.termux.app.TermuxInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File

class MainActivity : EdgeToEdgeIDEActivity() {

    private val viewModel by viewModel<MainViewModel>()
    private var _binding: ActivityMainBinding? = null

    private var sdkManagerFragment: SdkManagerFragment? = null
    private var lastNeedsInstall = true
    private var settingsFragmentAdded = false
    private var aboutFragmentAdded = false

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            viewModel.apply {

                // Ignore back press if project creating is in progress
                if (creatingProject.value == true) {
                    return@apply
                }

                // If a settings sub-page (e.g. "General") is open, pop it first
                // so back returns to the settings root instead of leaving the panel.
                if (currentScreen.value == SCREEN_SETTINGS && supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return@apply
                }

                val newScreen = when (currentScreen.value) {
                    SCREEN_TEMPLATE_DETAILS -> SCREEN_TEMPLATE_LIST
                    SCREEN_TEMPLATE_LIST -> SCREEN_MAIN
                    SCREEN_SDK_MANAGER,
                    SCREEN_SETTINGS,
                    SCREEN_ABOUT,
                    SCREEN_SAVED_PROJECTS,
                    SCREEN_DELETE_PROJECTS,
                    SCREEN_CLONE_REPO,
                    -> SCREEN_MAIN

                    else -> SCREEN_MAIN
                }

                if (currentScreen.value != newScreen) {
                    updateSidebarSelection(newScreen)
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

        setupSidebar()
        setupSdkInstallButton()
    }

    private fun setupSidebar() {
        binding.navProject.setOnClickListener {
            if (viewModel.currentScreen.value != SCREEN_MAIN) {
                viewModel.setScreen(SCREEN_MAIN)
            }
        }
        binding.navSdk.setOnClickListener {
            viewModel.setScreen(SCREEN_SDK_MANAGER)
        }
        binding.navSettings.setOnClickListener {
            clearSettingsBackStack()
            viewModel.setScreen(SCREEN_SETTINGS)
        }
        binding.navAbout.setOnClickListener {
            viewModel.setScreen(SCREEN_ABOUT)
        }
    }

    private fun clearSettingsBackStack() {
        val fragmentManager = supportFragmentManager
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
        }
    }

    private fun updateSidebarSelection(screen: Int?) {
        val selectedId = when (screen) {
            SCREEN_MAIN -> R.id.nav_project
            SCREEN_SDK_MANAGER -> R.id.nav_sdk
            SCREEN_SETTINGS -> R.id.nav_settings
            SCREEN_ABOUT -> R.id.nav_about
            else -> R.id.nav_project
        }
        for (item in arrayOf(binding.navProject, binding.navSdk, binding.navSettings, binding.navAbout)) {
            item.isActivated = item.id == selectedId
        }
    }

    private fun setupSdkInstallButton() {
        binding.btnSdkInstall.setOnClickListener {
            val fragment = sdkManagerFragment ?: return@setOnClickListener
            if (fragment.isInstalling) {
                return@setOnClickListener
            }
            binding.btnSdkInstall.isEnabled = false
            fragment.installToolchain(fragment.onComplete!!)
        }
    }

    private fun embedSdkManagerPanel() {
        if (sdkManagerFragment != null && sdkManagerFragment!!.isAdded) {
            return
        }
        val fragment = SdkManagerFragment.newInstance().also {
            sdkManagerFragment = it
        }
        supportFragmentManager.beginTransaction()
            .replace(binding.sdkFragmentContainer.id, fragment, SDK_MANAGER_FRAGMENT_TAG)
            .commit()

        fragment.onComplete = {
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                binding.btnSdkInstall.isEnabled = true
                refreshInstallButton(notify = false)
                flashSuccess(R.string.msg_sdk_manager_installed)
            }
        }
        fragment.onStateChanged = {
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                refreshInstallButton(notify = true)
            }
        }
        binding.btnSdkInstall.post {
            refreshInstallButton(notify = true)
        }
    }

    private fun refreshInstallButton(notify: Boolean) {
        val fragment = sdkManagerFragment ?: return
        val needsInstall = fragment.needsInstall()
        binding.btnSdkInstall.visibility = if (needsInstall) View.VISIBLE else View.GONE
        val changed = needsInstall != lastNeedsInstall
        lastNeedsInstall = needsInstall
        if (notify && !needsInstall && changed) {
            flashInfo(R.string.msg_sdk_manager_up_to_date)
        }
    }

    private fun embedSettingsPanel() {
        if (settingsFragmentAdded) {
            return
        }
        settingsFragmentAdded = true
        (prefs.children as MutableList?)?.clear()
        prefs.addRootPreferences()

        val fragment = IDEPreferencesFragment().apply {
            arguments = Bundle().apply {
                putParcelableArrayList(
                    IDEPreferencesFragment.EXTRA_CHILDREN,
                    ArrayList(prefs.children),
                )
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(binding.settingsFragmentContainer.id, fragment)
            .commit()
    }

    private fun embedAboutPanel() {
        if (aboutFragmentAdded) {
            return
        }
        aboutFragmentAdded = true
        supportFragmentManager.beginTransaction()
            .replace(binding.aboutPanel.id, AboutPanelFragment())
            .commit()
    }

internal fun launchIdeSetup() {
    startActivity(Intent(this, SetupActivity::class.java))
  }

  internal fun launchSdkManager() {
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
        binding.sidebar.setPadding(0, insets.top, 0, insets.bottom)
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

        when (screen) {
            SCREEN_SDK_MANAGER -> embedSdkManagerPanel()
            SCREEN_SETTINGS -> embedSettingsPanel()
            SCREEN_ABOUT -> if (!aboutFragmentAdded) {
                embedAboutPanel()
            }
        }

        val currentFragment = when (screen) {
            SCREEN_MAIN -> binding.main
            SCREEN_TEMPLATE_LIST -> binding.templateList
            SCREEN_TEMPLATE_DETAILS -> binding.templateDetails
            SCREEN_SAVED_PROJECTS -> binding.savedProjectsView
            SCREEN_DELETE_PROJECTS -> binding.deleteProjectsView
            SCREEN_CLONE_REPO -> binding.cloneRepositoryView
            SCREEN_SDK_MANAGER -> binding.sdkManagerPanel
            SCREEN_SETTINGS -> binding.settingsPanel
            SCREEN_ABOUT -> binding.aboutPanel
            else -> throw IllegalArgumentException("Invalid screen id: '$screen'")
        }

        for (view in arrayOf(
            binding.main,
            binding.templateList,
            binding.templateDetails,
            binding.savedProjectsView,
            binding.deleteProjectsView,
            binding.cloneRepositoryView,
            binding.sdkManagerPanel,
            binding.settingsPanel,
            binding.aboutPanel
        )) {
            view.isVisible = view == currentFragment
        }

        updateSidebarSelection(screen)
        syncToolbarTitle(screen)
    }

    private fun syncToolbarTitle(screen: Int?) {
        binding.toolbar.title = when (screen) {
            SCREEN_MAIN -> getString(R.string.section_project)
            SCREEN_SDK_MANAGER -> getString(R.string.title_sdk_manager)
            SCREEN_SETTINGS -> getString(R.string.ide_preferences)
            SCREEN_ABOUT -> getString(R.string.about)
            else -> getString(R.string.app_name)
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

    companion object {
        private const val SDK_MANAGER_FRAGMENT_TAG = "ide.sdk.manager.fragment"
    }
}
