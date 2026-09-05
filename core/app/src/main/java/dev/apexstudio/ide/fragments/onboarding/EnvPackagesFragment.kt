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
import androidx.core.view.isVisible
import com.blankj.utilcode.util.ResourceUtils
import com.github.appintro.SlidePolicy
import com.termux.app.TermuxInstaller
import dev.apexstudio.ide.R
import dev.apexstudio.ide.activities.OnboardingActivity
import dev.apexstudio.ide.databinding.LayoutEnvPackagesBinding
import dev.apexstudio.ide.utils.EnvPackages
import dev.apexstudio.ide.utils.Environment
import dev.apexstudio.ide.utils.flashInfo
import java.io.File

/**
 * Environment packages slide of the onboarding flow.
 *
 * Lets the user install the base toolchain from the Apex apt repository
 * (JDK, aapt2 and a few command-line utilities) before continuing to the
 * SDK manager. This step is skippable.
 *
 * @author Apex Studio Dev
 */
class EnvPackagesFragment : OnboardingFragment(), SlidePolicy {

  private var _content: LayoutEnvPackagesBinding? = null
  private val content: LayoutEnvPackagesBinding
    get() = checkNotNull(_content) { "Fragment has been destroyed" }

  @Volatile
  private var installingEnv = false

  @Volatile
  private var envReady = false

  companion object {
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

  private fun appendEnvLine(line: String) {
    val status = content.tvEnvStatus
    val current = status.text?.toString().orEmpty()
    val prefix = if (current.isNotEmpty() && !current.endsWith("\n")) "\n" else ""
    status.append("$prefix$line")
  }

  override fun createContentView(parent: ViewGroup, attachToParent: Boolean) {
    _content = LayoutEnvPackagesBinding.inflate(layoutInflater, parent, attachToParent)

    val missing = EnvPackages.missingEnvPackages()
    if (missing.isEmpty()) {
      content.tvEnvSummary.setText(R.string.msg_env_installed)
      content.btnInstall.isVisible = false
      content.btnSkip.isVisible = false
      envReady = true
    } else {
      content.tvEnvSummary.setText(
        getString(R.string.msg_env_missing, missing.joinToString(", ")))
    }

    content.btnInstall.setOnClickListener { installEnv() }
    content.btnSkip.setOnClickListener {
      envReady = true
      (activity as? OnboardingActivity)?.advanceToNextSlide()
    }
  }

  private fun installEnv() {
    if (installingEnv) {
      return
    }

    if (!TermuxInstaller.isBootstrapInstalled()) {
      flashInfo(R.string.msg_setup_bootstrap_wait)
      return
    }

    installingEnv = true
    content.tvEnvStatus.text = getString(R.string.msg_env_installing)
    content.tvEnvStatus.isVisible = true
    content.btnInstall.isEnabled = false
    content.btnSkip.isEnabled = false

    Thread {
      try {
        val scriptDir = File(Environment.PREFIX, "etc/apexstudio")
        scriptDir.mkdirs()
        val script = File(scriptDir, "install-toolchain.sh")
        val manifest = File(scriptDir, "toolchain-manifest.json")
        val scriptOk = ResourceUtils.copyFileFromAssets(
          "data/common/install-toolchain.sh", script.absolutePath)
        val manifestOk = ResourceUtils.copyFileFromAssets(
          "data/common/toolchain-manifest.json", manifest.absolutePath)
        if (!scriptOk || !manifestOk) {
          throw IllegalStateException("asset copy failed (script=$scriptOk, manifest=$manifestOk)")
        }
        script.setExecutable(true)

        val env = HashMap<String, String>()
        Environment.putEnvironment(env, false)
        env["PREFIX"] = Environment.PREFIX.absolutePath
        env["TMPDIR"] = Environment.TMP_DIR.absolutePath
        env["PATH"] = Environment.BIN_DIR.absolutePath + ":" + System.getenv("PATH")

        val process = ProcessBuilder(
          Environment.BASH_SHELL.absolutePath,
          script.absolutePath,
          "--env-only",
          "--jdk",
          "21"
        ).redirectErrorStream(true)
          .apply { environment().putAll(env) }
          .start()

        process.inputStream.bufferedReader().forEachLine { line ->
          requireActivity().runOnUiThread {
            if (isAdded) {
              appendEnvLine(line)
            }
          }
        }
        val code = process.waitFor()
        requireActivity().runOnUiThread {
          if (!isAdded) {
            return@runOnUiThread
          }
          if (code == 0) {
            envReady = true
            content.tvEnvSummary.setText(R.string.msg_env_installed)
            content.btnInstall.isVisible = false
            content.btnSkip.isVisible = false
            (activity as? OnboardingActivity)?.advanceToNextSlide()
          } else {
            appendEnvLine(getString(R.string.msg_setup_toolchain_failed, code))
          }
        }
      } catch (e: Exception) {
        requireActivity().runOnUiThread {
          if (isAdded) {
            appendEnvLine(getString(R.string.msg_setup_toolchain_error, e.message))
          }
        }
      } finally {
        requireActivity().runOnUiThread {
          installingEnv = false
          if (isAdded) {
            content.btnInstall.isEnabled = true
            content.btnSkip.isEnabled = true
          }
        }
      }
    }.apply {
      isDaemon = true
      start()
    }
  }

  override val isPolicyRespected: Boolean
    get() = envReady && !installingEnv

  override fun onUserIllegallyRequestedNextPage() {
    flashInfo(R.string.msg_env_installing)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _content = null
  }
}