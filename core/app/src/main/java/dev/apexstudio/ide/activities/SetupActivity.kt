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
import com.termux.app.TermuxInstaller
import dev.apexstudio.ide.R
import dev.apexstudio.ide.app.EdgeToEdgeIDEActivity
import dev.apexstudio.ide.databinding.ActivitySetupBinding

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
    appendLine("[setup] Bootstrap installed.")
    onSetupFinished()
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