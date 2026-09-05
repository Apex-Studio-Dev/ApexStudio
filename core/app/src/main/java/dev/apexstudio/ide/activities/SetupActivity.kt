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

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.graphics.Insets
import com.blankj.utilcode.util.ResourceUtils
import com.termux.app.TermuxInstaller
import dev.apexstudio.ide.R
import dev.apexstudio.ide.app.EdgeToEdgeIDEActivity
import dev.apexstudio.ide.databinding.ActivitySetupBinding
import dev.apexstudio.ide.utils.Environment
import java.io.File

class SetupActivity : EdgeToEdgeIDEActivity() {

  private var _binding: ActivitySetupBinding? = null

  private val binding: ActivitySetupBinding
    get() = checkNotNull(_binding) {
      "Activity has been destroyed"
    }

  override var eteUpdateDecorViewPaddingInLandscape: Boolean = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding.btnContinue.isEnabled = false
    binding.btnContinue.setOnClickListener {
      startActivity(
        Intent(this, MainActivity::class.java)
          .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      )
      finish()
    }

    if (TermuxInstaller.isBootstrapInstalled()) {
      appendLine("[setup] Bootstrap is already installed.")
      onBootstrapDone()
    } else {
      appendLine("[setup] Installing bootstrap packages...")
      TermuxInstaller.setupBootstrapIfNeeded(
        this,
        { line -> runOnUiThread { appendLine(line) } },
        { onBootstrapDone() }
      )
    }
  }

  private fun onBootstrapDone() {
    val ideEnvFile = File(File(Environment.PREFIX, "etc"), "ide-environment.properties")
    if (ideEnvFile.isFile) {
      appendLine("[setup] Toolchain is already installed.")
      onSetupFinished()
      return
    }
    installToolchain()
  }

  private fun installToolchain() {
    appendLine("[setup] Installing toolchain (JDK, aapt2, Android SDK)...")
    Thread {
      try {
        val scriptDir = File(Environment.PREFIX, "etc/apexstudio")
        scriptDir.mkdirs()
        val script = File(scriptDir, "install-toolchain.sh")
        val manifest = File(scriptDir, "toolchain-manifest.json")
        try {
          val scriptOk = ResourceUtils.copyFileFromAssets("data/common/install-toolchain.sh", script.absolutePath)
          val manifestOk = ResourceUtils.copyFileFromAssets("data/common/toolchain-manifest.json", manifest.absolutePath)
          if (!scriptOk || !manifestOk) {
            throw IllegalStateException("asset copy failed (script=$scriptOk, manifest=$manifestOk)")
          }
        } catch (e: Exception) {
          runOnUiThread { appendLine("[setup] Failed to extract toolchain assets: ${e.message}") }
          return@Thread
        }
        script.setExecutable(true)

        val env = HashMap<String, String>()
        Environment.putEnvironment(env, false)
        env["PREFIX"] = Environment.PREFIX.absolutePath
        env["TMPDIR"] = Environment.TMP_DIR.absolutePath
        env["PATH"] = Environment.BIN_DIR.absolutePath + ":" + System.getenv("PATH")

        val process = ProcessBuilder(Environment.BASH_SHELL.absolutePath, script.absolutePath)
          .redirectErrorStream(true)
          .apply { environment().putAll(env) }
          .start()

        process.inputStream.bufferedReader().forEachLine { line ->
          runOnUiThread { appendLine(line) }
        }
        val code = process.waitFor()
        runOnUiThread {
          if (code == 0) {
            appendLine("[setup] Toolchain installed successfully.")
            onSetupFinished()
          } else {
            appendLine(
              "[setup] Toolchain install failed (exit code $code). " +
                "Retry when ready; Continue stays disabled until it succeeds."
            )
          }
        }
      } catch (e: Exception) {
        runOnUiThread { appendLine("[setup] Unexpected error: ${e.message}") }
      }
    }.apply {
      isDaemon = true
      start()
    }
  }

  private fun appendLine(line: String) {
    binding.tvTerminal.append(line)
    binding.tvTerminal.append("\n")
    binding.terminalScroll.post { binding.terminalScroll.fullScroll(View.FOCUS_DOWN) }
  }

  private fun onSetupFinished() {
    binding.tvStatus.setText(R.string.status_setup_done)
    binding.btnContinue.isEnabled = true
  }

  override fun onApplySystemBarInsets(insets: Insets) {
    binding.root.setPadding(insets.left, insets.top, insets.right, insets.bottom)
  }

  override fun bindLayout(): View {
    _binding = ActivitySetupBinding.inflate(layoutInflater)
    return _binding!!.root
  }

  override fun onDestroy() {
    super.onDestroy()
    _binding = null
  }
}