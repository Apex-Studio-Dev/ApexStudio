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
package dev.apexstudio.ide.activities

import android.os.Bundle
import android.view.View
import androidx.core.graphics.Insets
import dev.apexstudio.ide.R
import dev.apexstudio.ide.app.EdgeToEdgeIDEActivity
import dev.apexstudio.ide.databinding.ActivitySdkManagerBinding
import dev.apexstudio.ide.fragments.SdkManagerFragment
import dev.apexstudio.ide.utils.flashInfo
import dev.apexstudio.ide.utils.flashSuccess

/**
 * Standalone Android SDK manager screen.
 *
 * Lets the user pick which JDK, Android platforms, build-tools, NDK and CMake
 * versions to install outside of the onboarding flow.
 *
 * @author Apex Studio Dev
 */
class SdkManagerActivity : EdgeToEdgeIDEActivity() {

  private var _binding: ActivitySdkManagerBinding? = null

  private val binding: ActivitySdkManagerBinding
    get() = checkNotNull(_binding) {
      "Activity has been destroyed"
    }

  private lateinit var sdkManagerFragment: SdkManagerFragment

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.setTitle(R.string.title_sdk_manager)
    binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

    sdkManagerFragment =
      supportFragmentManager.findFragmentByTag(SDK_MANAGER_FRAGMENT_TAG) as? SdkManagerFragment
        ?: SdkManagerFragment.newInstance()

    if (sdkManagerFragment.isAdded.not()) {
      supportFragmentManager.beginTransaction()
        .replace(binding.fragmentContainer.id, sdkManagerFragment, SDK_MANAGER_FRAGMENT_TAG)
        .commit()
    }

    sdkManagerFragment.onComplete = {
      runOnUiThread {
        if (isFinishing || isDestroyed) {
          return@runOnUiThread
        }
        binding.btnSdkInstall.isEnabled = true
        binding.btnSdkInstall.visibility = View.VISIBLE
        flashSuccess(R.string.msg_sdk_manager_installed)
      }
    }

    binding.btnSdkInstall.setOnClickListener {
      if (sdkManagerFragment.isInstalling) {
        return@setOnClickListener
      }
      binding.btnSdkInstall.isEnabled = false
      sdkManagerFragment.installToolchain(sdkManagerFragment.onComplete!!)
    }

    binding.btnSdkInstall.post {
      if (sdkManagerFragment.needsInstall()) {
        binding.btnSdkInstall.visibility = View.VISIBLE
      } else {
        binding.btnSdkInstall.visibility = View.GONE
        flashInfo(R.string.msg_sdk_manager_up_to_date)
      }
    }
  }

  override fun onApplySystemBarInsets(insets: Insets) {
    binding.root.setPadding(insets.left, insets.top, insets.right, insets.bottom)
  }

  override fun bindLayout(): View {
    _binding = ActivitySdkManagerBinding.inflate(layoutInflater)
    return _binding!!.root
  }

  override fun onDestroy() {
    _binding = null
    super.onDestroy()
  }

  companion object {
    private const val SDK_MANAGER_FRAGMENT_TAG = "ide.sdk.manager.fragment"
  }
}