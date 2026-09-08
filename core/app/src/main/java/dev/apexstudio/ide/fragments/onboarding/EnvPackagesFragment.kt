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
package dev.apexstudio.ide.fragments.onboarding

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import com.github.appintro.SlidePolicy
import dev.apexstudio.ide.R
import dev.apexstudio.ide.activities.OnboardingActivity
import dev.apexstudio.ide.databinding.LayoutEnvPackagesBinding
import dev.apexstudio.ide.fragments.SdkManagerFragment
import dev.apexstudio.ide.utils.EnvPackages
import dev.apexstudio.ide.utils.flashInfo

/**
 * Environment packages slide of the onboarding flow.
 *
 * Embeds the [SdkManagerFragment] so the user can pick which JDK, Android
 * platforms, build-tools, NDK and CMake versions to install while setting up.
 * This step is skippable (nothing is installed automatically).
 *
 * @author Apex Studio Dev
 */
class EnvPackagesFragment : OnboardingFragment(), SlidePolicy {

  private var _content: LayoutEnvPackagesBinding? = null
  private val content: LayoutEnvPackagesBinding
    get() = checkNotNull(_content) { "Fragment has been destroyed" }

  @Volatile
  private var envReady = false

  private var sdkManager: SdkManagerFragment? = null

  companion object {
    private const val SDK_MANAGER_TAG = "env_sdk_manager"

    @JvmStatic
    fun newInstance(context: Context): EnvPackagesFragment {
      return EnvPackagesFragment().apply {
        arguments = Bundle().apply {
          putCharSequence(KEY_ONBOARDING_TITLE,
            context.getString(R.string.title_env_packages))
          putCharSequence(KEY_ONBOARDING_SUBTITLE,
            context.getString(R.string.subtitle_env_packages))
        }
      }
    }
  }

  override fun createContentView(parent: ViewGroup, attachToParent: Boolean) {
    _content = LayoutEnvPackagesBinding.inflate(layoutInflater, parent, attachToParent)

    val existing =
      childFragmentManager.findFragmentByTag(SDK_MANAGER_TAG) as? SdkManagerFragment
    sdkManager = existing ?: SdkManagerFragment.newInstance(compact = true).also {
      childFragmentManager.beginTransaction()
        .add(content.sdkManagerContainer.id, it, SDK_MANAGER_TAG)
        .commit()
    }

    sdkManager?.onStateChanged = {
      activity?.runOnUiThread {
        if (isAdded && _content != null) {
          updateButtons()
        }
      }
    }

    val missing = EnvPackages.missingEnvPackages()
    envReady = missing.isEmpty()
    content.tvEnvSummary.setText(
      if (missing.isEmpty()) {
        R.string.msg_env_installed
      } else {
        getString(R.string.msg_env_missing, missing.joinToString(", "))
      })

    content.btnInstall.setOnClickListener { installSelected() }
    content.btnSkip.setOnClickListener { skipSetup() }
  }

  private fun updateButtons() {
    val installing = sdkManager?.isInstalling == true
    content.btnInstall.isEnabled = !installing
    content.btnSkip.isEnabled = !installing
  }

  private fun installSelected() {
    val sdk = sdkManager ?: return
    if (sdk.isInstalling) {
      return
    }
    content.btnInstall.isEnabled = false
    content.btnSkip.isEnabled = false
    sdk.installToolchain(onComplete = { onSetupFinished() })
  }

  private fun skipSetup() {
    if (sdkManager?.isInstalling == true) {
      return
    }
    envReady = true
    (activity as? OnboardingActivity)?.advanceToNextSlide()
  }

  private fun onSetupFinished() {
    activity?.runOnUiThread {
      envReady = true
      if (isAdded && _content != null) {
        updateButtons()
      }
      (activity as? OnboardingActivity)?.advanceToNextSlide()
    }
  }

  override val isPolicyRespected: Boolean
    get() = envReady && sdkManager?.isInstalling != true

  override fun onUserIllegallyRequestedNextPage() {
    flashInfo(
      if (sdkManager?.isInstalling == true) {
        R.string.msg_sdk_manager_installing
      } else {
        R.string.msg_env_installing
      })
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _content = null
  }
}