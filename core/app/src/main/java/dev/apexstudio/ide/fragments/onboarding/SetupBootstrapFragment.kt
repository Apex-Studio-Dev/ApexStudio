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
import android.view.View
import androidx.core.view.isVisible
import com.github.appintro.SlidePolicy
import com.termux.app.TermuxInstaller
import dev.apexstudio.ide.R
import dev.apexstudio.ide.activities.OnboardingActivity
import dev.apexstudio.ide.databinding.LayoutSetupBootstrapBinding
import dev.apexstudio.ide.utils.flashInfo

class SetupBootstrapFragment : OnboardingFragment(), SlidePolicy {

  private var _content: LayoutSetupBootstrapBinding? = null
  private val content: LayoutSetupBootstrapBinding
    get() = checkNotNull(_content) { "Fragment has been destroyed" }

  @Volatile
  private var bootstrapReady = false

  companion object {

    @JvmStatic
    fun newInstance(context: Context): SetupBootstrapFragment {
      return SetupBootstrapFragment().apply {
        arguments = Bundle().apply {
          putCharSequence(KEY_ONBOARDING_TITLE, context.getString(R.string.title_setup_bootstrap))
          putCharSequence(KEY_ONBOARDING_SUBTITLE,
            context.getString(R.string.subtitle_setup_bootstrap))
        }
      }
    }
  }

  override fun createContentView(parent: ViewGroup, attachToParent: Boolean) {
    _content = LayoutSetupBootstrapBinding.inflate(layoutInflater, parent, attachToParent)

    if (TermuxInstaller.isBootstrapInstalled()) {
      bootstrapReady = true
      appendLine("[setup] Bootstrap is already installed.")
      setStatus(getString(R.string.msg_setup_bootstrap_installed), done = true)
      return
    }

    appendLine("[setup] Installing bootstrap packages...")
    val activity = requireActivity()
    TermuxInstaller.setupBootstrapIfNeeded(
      activity,
      { line ->
        activity.runOnUiThread { if (isAdded) appendLine(line) }
      },
      Runnable {
        if (!isAdded) return@Runnable
        appendLine("[setup] Bootstrap installed.")
        setStatus(getString(R.string.msg_setup_bootstrap_installed), done = true)
        bootstrapReady = true
        (activity as? OnboardingActivity)?.advanceToNextSlide()
      }
    )
  }

  private fun appendLine(line: String) {
    content.tvTerminal.append(line)
    content.tvTerminal.append("\n")
    content.terminalScroll.post { content.terminalScroll.fullScroll(View.FOCUS_DOWN) }
  }

  private fun setStatus(text: String, done: Boolean) {
    content.tvStatus.text = text
    content.tvStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(
      0, 0, if (done) R.drawable.ic_check else 0, 0
    )
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _content = null
  }

  override val isPolicyRespected: Boolean
    get() = bootstrapReady

  override fun onUserIllegallyRequestedNextPage() {
    flashInfo(R.string.msg_setup_bootstrap_wait)
  }
}