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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.provider.Settings
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import com.blankj.utilcode.util.ResourceUtils
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.termux.app.TermuxInstaller
import dev.apexstudio.ide.R
import dev.apexstudio.ide.databinding.LayoutIdeSdkManagerBinding
import dev.apexstudio.ide.resources.R.string
import dev.apexstudio.ide.utils.ConnectionInfo
import dev.apexstudio.ide.utils.Environment
import dev.apexstudio.ide.utils.flashError
import dev.apexstudio.ide.utils.getConnectionInfo
import org.json.JSONObject
import java.io.File

/**
 * Android SDK manager.
 *
 * Lets the user pick which JDK, Android platforms, build-tools, NDK and CMake
 * versions to install (multi-version capable, driven by the bundled
 * `data/common/toolchain-manifest.json` catalog). The selection is applied by
 * `install-toolchain.sh`. This fragment is reusable: it can be hosted inside an
 * activity without being tied to the onboarding flow; the host decides what
 * happens when installation completes via [onComplete].
 *
 * @author Apex Studio Dev
 */
class SdkManagerFragment : Fragment() {

  private var _content: LayoutIdeSdkManagerBinding? = null
  private val content: LayoutIdeSdkManagerBinding
    get() = checkNotNull(_content) { "Fragment has been destroyed" }

  private var backgroundDataRestrictionReceiver: BroadcastReceiver? = null
  private var networkStateChangeCallback: NetworkCallback? = null

  private val selectedPlatforms = LinkedHashSet<String>()
  private val selectedBuildTools = LinkedHashSet<String>()
  private val selectedNdkVersions = LinkedHashSet<String>()
  private val selectedCmakeVersions = LinkedHashSet<String>()

  @Volatile
  private var installingToolchain = false

  /**
   * Called (on the main thread) when the toolchain installation completes successfully.
   */
  var onComplete: (() -> Unit)? = null

  val isInstalling: Boolean
    get() = installingToolchain

  companion object {

    @JvmStatic
    fun newInstance(): SdkManagerFragment {
      return SdkManagerFragment()
    }
  }

  @SuppressLint("PrivateResource")
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _content = LayoutIdeSdkManagerBinding.inflate(inflater, container, false)

    content.apply {
      noConnection.root.setText(R.string.msg_no_internet)
      cellularConnection.root.setText(R.string.msg_connected_to_cellular)
      meteredConnection.root.setText(R.string.msg_connected_to_metered_connection)
      backgroundDataRestricted.root.setText(R.string.msg_disable_background_data_restriction)

      val manifest = readToolchainManifest()

      val jdks = manifest.getJSONArray("jdk").toStringList()
      val jdkDisplayNames = jdks.map { "JDK $it" }
      val defaultJdk = jdks.firstOrNull { it == "21" } ?: jdks.firstOrNull().orEmpty()
      jdkVersion.setText(
        jdkDisplayNames.firstOrNull { it == "JDK $defaultJdk" } ?: jdkDisplayNames.firstOrNull())
      jdkVersion.setAdapter(ArrayAdapter(
        requireContext(),
        com.google.android.material.R.layout.m3_auto_complete_simple_item,
        jdkDisplayNames)
      )

      val platformValues = manifest.getJSONArray("platforms").toStringList()
      selectedPlatforms += platformValues.firstOrNull() ?: ""
      populateCheckboxList(llPlatforms, platformValues.map { "API $it" to it },
        selectedPlatforms)

      val buildTools = manifest.getJSONArray("build_tools").toStringList()
      selectedBuildTools += buildTools.firstOrNull() ?: ""
      populateCheckboxList(llBuildTools, buildTools.map { "Build-tools $it" to it },
        selectedBuildTools)

      val ndks = manifest.getJSONArray("ndk").toObjectList().map {
        it.getString("display") to it.getString("version")
      }
      populateCheckboxList(llNdk, ndks, selectedNdkVersions)

      val cmakes = manifest.getJSONArray("cmake").toObjectList().map {
        it.getString("display") to it.getString("version")
      }
      populateCheckboxList(llCmake, cmakes, selectedCmakeVersions)
    }

    updateConnectionStatus()
    return content.root
  }

  fun needsInstall(): Boolean {
    if (installingToolchain) {
      return false
    }

    val ideEnvFile = File(File(Environment.PREFIX, "etc"), "ide-environment.properties")
    if (!ideEnvFile.isFile) {
      return true
    }

    if (selectedPlatforms.any { !platformInstalled(it) }) {
      return true
    }

    if (selectedBuildTools.any { !buildToolsInstalled(it) }) {
      return true
    }

    if (selectedNdkVersions.any { !File(Environment.ANDROID_HOME, "ndk/$it").exists() }) {
      return true
    }

    if (selectedCmakeVersions.any {
        !File(Environment.ANDROID_HOME, "cmake/$it").isDirectory
      }) {
      return true
    }

    return false
  }

  fun installToolchain(onComplete: () -> Unit) {
    if (installingToolchain) {
      return
    }

    if (!TermuxInstaller.isBootstrapInstalled()) {
      flashError(R.string.msg_setup_bootstrap_wait)
      return
    }

    installingToolchain = true
    content.tvInstallStatus.text = getString(R.string.msg_sdk_manager_installing)
    content.tvInstallStatus.isVisible = true
    setUiEnabled(false)

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

        val args = buildToolchainArgs()

        val env = HashMap<String, String>()
        Environment.putEnvironment(env, false)
        env["PREFIX"] = Environment.PREFIX.absolutePath
        env["TMPDIR"] = Environment.TMP_DIR.absolutePath
        env["PATH"] = Environment.BIN_DIR.absolutePath + ":" + System.getenv("PATH")

        val process = ProcessBuilder(
          Environment.BASH_SHELL.absolutePath,
          script.absolutePath,
          *args
        ).redirectErrorStream(true)
          .apply { environment().putAll(env) }
          .start()

        process.inputStream.bufferedReader().forEachLine { line ->
          requireActivity().runOnUiThread {
            if (isAdded) {
              appendInstallLine(line)
            }
          }
        }
        val code = process.waitFor()
        requireActivity().runOnUiThread {
          if (code == 0) {
            onComplete()
          } else {
            appendInstallLine(getString(R.string.msg_setup_toolchain_failed, code))
          }
        }
      } catch (e: Exception) {
        requireActivity().runOnUiThread {
          if (isAdded) {
            appendInstallLine(getString(R.string.msg_setup_toolchain_error, e.message))
          }
        }
      } finally {
        requireActivity().runOnUiThread {
          if (isAdded) {
            installingToolchain = false
            setUiEnabled(true)
          } else {
            installingToolchain = false
          }
        }
      }
    }.apply {
      isDaemon = true
      start()
    }
  }

  private fun buildToolchainArgs(): Array<String> {
    val args = mutableListOf<String>()
    val jdk = content.jdkVersion.text?.toString()
      ?.removePrefix("JDK ")
      ?.replace(" ", "")
      ?: "21"
    args += listOf("--jdk", jdk)
    selectedPlatforms.filter { it.isNotBlank() }.forEach {
      args += listOf("--platform", it)
    }
    selectedBuildTools.filter { it.isNotBlank() }.forEach {
      args += listOf("--build-tools", it)
    }
    selectedNdkVersions.filter { it.isNotBlank() }.forEach {
      args += listOf("--ndk", it)
    }
    selectedCmakeVersions.filter { it.isNotBlank() }.forEach {
      args += listOf("--cmake", it)
    }
    return args.toTypedArray()
  }

  private fun populateCheckboxList(
    container: ViewGroup,
    items: List<Pair<String, String>>,
    selected: MutableSet<String>
  ) {
    container.removeAllViews()
    items.forEach { (label, value) ->
      val checkBox = MaterialCheckBox(requireContext())
      checkBox.text = label
      checkBox.isChecked = selected.contains(value)
      checkBox.minHeight = 0
      checkBox.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
          selected += value
        } else {
          selected -= value
        }
      }
      container.addView(checkBox)
    }
  }

  private fun platformInstalled(api: String): Boolean =
    File(Environment.ANDROID_HOME, "platforms/android-$api").isDirectory

  private fun buildToolsInstalled(version: String): Boolean =
    File(Environment.ANDROID_HOME, "build-tools/$version").isDirectory

  private fun setUiEnabled(enabled: Boolean) {
    content.jdkVersionLayout.isEnabled = enabled
    content.llPlatforms.forEachEnabled(enabled)
    content.llBuildTools.forEachEnabled(enabled)
    content.llNdk.forEachEnabled(enabled)
    content.llCmake.forEachEnabled(enabled)
  }

  private fun ViewGroup.forEachEnabled(enabled: Boolean) {
    for (i in 0 until childCount) {
      getChildAt(i).isEnabled = enabled
    }
  }

  private fun appendInstallLine(line: String) {
    val currentText = content.tvInstallStatus.text?.toString().orEmpty()
    content.tvInstallStatus.text =
      currentText + if (currentText.endsWith("\n") || currentText.isEmpty()) {
        line
      } else {
        "\n$line"
      }
  }

  private fun readToolchainManifest(): JSONObject {
    val reader = requireContext().assets.open("data/common/toolchain-manifest.json")
      .bufferedReader()
    return try {
      JSONObject(reader.readText())
    } finally {
      reader.close()
    }
  }

  private fun org.json.JSONArray.toStringList(): List<String> =
    (0 until length()).map { getString(it) }

  private fun org.json.JSONArray.toObjectList(): List<JSONObject> =
    (0 until length()).map { getJSONObject(it) }

  override fun onStart() {
    super.onStart()
    updateConnectionStatus()
    monitorNetworkState()
  }

  override fun onStop() {
    super.onStop()
    removeNetworkMonitors()
  }

  private fun updateConnectionStatus(networkCapabilities: NetworkCapabilities? = null) =
    updateConnectionStatus(getConnectionInfo(requireContext(), networkCapabilities))

  private fun updateConnectionStatus(connectionInfo: ConnectionInfo) {
    content.noConnection.root.isVisible = false
    content.cellularConnection.root.isVisible = false
    content.meteredConnection.root.isVisible = false
    content.backgroundDataRestricted.root.isVisible = false

    if (connectionInfo === ConnectionInfo.UNKNOWN || !connectionInfo.isConnected) {
      showNoConnectionWarning()
      return
    }

    if (connectionInfo.isCellularTransport) {
      addCellularTransportWarning()
    }

    if (connectionInfo.isMeteredConnection && !connectionInfo.isCellularTransport) {
      addMeteredConnectionWarning()
    }

    if (connectionInfo.isBackgroundDataRestricted) {
      addBackgroundDataRestrictedWarning()
    }
  }

  private fun addBackgroundDataRestrictedWarning() {
    content.backgroundDataRestricted.root.apply {
      setText(R.string.msg_disable_background_data_restriction)
      isVisible = true
    }
  }

  private fun addMeteredConnectionWarning() {
    content.meteredConnection.root.apply {
      setText(R.string.msg_connected_to_metered_connection)
      isVisible = true
    }
  }

  private fun addCellularTransportWarning() {
    content.cellularConnection.root.apply {
      setText(R.string.msg_connected_to_cellular)
      isVisible = true
    }
  }

  private fun showNoConnectionWarning() {
    val msg = SpannableStringBuilder(getString(R.string.msg_no_internet))
    msg.append(" ")
    msg.append(getString(R.string.action_open_settings), URLSpan(""),
      Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )

    content.noConnection.root.apply {
      isVisible = true
      setOnClickListener {
        it.context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    backgroundDataRestrictionReceiver = null
    networkStateChangeCallback = null
    _content = null
  }

  private fun monitorNetworkState() {
    val connectivityManager = requireContext().getSystemService<ConnectivityManager>() ?: return
    networkStateChangeCallback?.also {
      connectivityManager.registerDefaultNetworkCallback(it)
    }

    networkStateChangeCallback = object : NetworkCallback() {

      override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
      ) {
        updateConnectionStatus(networkCapabilities)
      }

      override fun onLost(network: Network) {
        updateConnectionStatus(ConnectionInfo.UNKNOWN)
      }
    }

    val networkRequest = NetworkRequest.Builder()
      .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
      .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
      .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
      .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
      .build()
    connectivityManager.registerNetworkCallback(networkRequest, networkStateChangeCallback!!)

    backgroundDataRestrictionReceiver?.also {
      try {
        requireContext().unregisterReceiver(it)
      } catch (err: Throwable) { /* ignored */
      }
    }

    backgroundDataRestrictionReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        updateConnectionStatus()
      }
    }

    requireContext().registerReceiver(backgroundDataRestrictionReceiver!!,
      IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED))
  }

  private fun removeNetworkMonitors() {
    networkStateChangeCallback?.also {
      requireContext().getSystemService<ConnectivityManager>()?.unregisterNetworkCallback(it)
      networkStateChangeCallback = null
    }

    backgroundDataRestrictionReceiver?.also {
      requireContext().unregisterReceiver(it)
      backgroundDataRestrictionReceiver = null
    }
  }

}
