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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.blankj.utilcode.util.ResourceUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.termux.app.TermuxInstaller
import dev.apexstudio.ide.R
import dev.apexstudio.ide.databinding.LayoutIdeSdkManagerBinding
import dev.apexstudio.ide.resources.R.string
import dev.apexstudio.ide.utils.ConnectionInfo
import dev.apexstudio.ide.utils.Environment
import dev.apexstudio.ide.utils.flashError
import dev.apexstudio.ide.utils.flashSuccess
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

  /**
   * Called whenever the set of installed/selected components changes, so the
   * host can re-evaluate e.g. the visibility of the install button.
   */
  var onStateChanged: (() -> Unit)? = null

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
    }

    refreshComponentLists()

    updateConnectionStatus()
    return content.root
  }

  private fun refreshComponentLists() {
    content.apply {
      val platformValues = readToolchainManifest().getJSONArray("platforms").toStringList()
      if (selectedPlatforms.isEmpty()) selectedPlatforms += platformValues.firstOrNull().orEmpty()
      populateCheckboxList(llPlatforms, platformValues.map { "API $it" to it },
        selectedPlatforms, "platform") { platformInstalled(it) }

      val buildTools = readToolchainManifest().getJSONArray("build_tools").toStringList()
      if (selectedBuildTools.isEmpty()) selectedBuildTools += buildTools.firstOrNull().orEmpty()
      populateCheckboxList(llBuildTools, buildTools.map { "Build-tools $it" to it },
        selectedBuildTools, "build-tools") { buildToolsInstalled(it) }

      val ndks = readToolchainManifest().getJSONArray("ndk").toObjectList().map {
        it.getString("display") to it.getString("version")
      }
      populateCheckboxList(llNdk, ndks, selectedNdkVersions, "ndk") {
        File(Environment.ANDROID_HOME, "ndk/$it").exists()
      }

      val cmakes = readToolchainManifest().getJSONArray("cmake").toObjectList().map {
        it.getString("display") to it.getString("version")
      }
      populateCheckboxList(llCmake, cmakes, selectedCmakeVersions, "cmake") {
        File(Environment.ANDROID_HOME, "cmake/$it").isDirectory
      }
    }
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
        env["ANDROID_HOME"] = Environment.ANDROID_HOME.absolutePath
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
            refreshComponentLists()
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
    selected: MutableSet<String>,
    typeToken: String,
    isInstalled: (String) -> Boolean
  ) {
    container.removeAllViews()
    items.forEach { (label, value) ->
      val installed = isInstalled(value)

      val row = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
      }

      val checkBox = MaterialCheckBox(requireContext()).apply {
        text = if (installed) {
          "$label  (${getString(R.string.msg_sdk_component_installed)})"
        } else {
          label
        }
        isChecked = selected.contains(value)
        isEnabled = !installed
        minHeight = 0
        setTextColor(MaterialColors.getColor(requireContext(),
          com.google.android.material.R.attr.colorOnSurfaceVariant, 0))
        if (!installed) {
          setTextColor(MaterialColors.getColor(requireContext(),
            com.google.android.material.R.attr.colorOnSurface, 0))
        }
        setOnCheckedChangeListener { _, isChecked ->
          if (isChecked) {
            selected += value
          } else {
            selected -= value
          }
          onStateChanged?.invoke()
        }
      }
      row.addView(checkBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

      if (installed) {
        val uninstall = MaterialButton(
          requireContext(),
          null,
          com.google.android.material.R.attr.borderlessButtonStyle).apply {
          text = getString(R.string.action_uninstall)
          isAllCaps = false
          minWidth = 0
          minHeight = 0
          insetTop = 0
          insetBottom = 0
          textSize = 12f
          setTextColor(MaterialColors.getColor(
            requireContext(), com.google.android.material.R.attr.colorPrimary, 0))
          setOnClickListener {
            uninstallComponent(typeToken, value)
          }
        }
        row.addView(uninstall, LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
      }

      container.addView(row)
    }
  }

  private fun uninstallComponent(typeToken: String, value: String) {
    if (installingToolchain) {
      return
    }

    if (!TermuxInstaller.isBootstrapInstalled()) {
      flashError(R.string.msg_setup_bootstrap_wait)
      return
    }

    installingToolchain = true
    setUiEnabled(false)
    content.tvInstallStatus.text =
      getString(R.string.msg_sdk_manager_uninstalling, labelOf(typeToken, value)) + "\n"
    content.tvInstallStatus.isVisible = true

    Thread {
      try {
        val scriptDir = File(Environment.PREFIX, "etc/apexstudio")
        scriptDir.mkdirs()
        val script = File(scriptDir, "uninstall-toolchain.sh")
        if (!ResourceUtils.copyFileFromAssets(
            "data/common/uninstall-toolchain.sh", script.absolutePath)) {
          throw IllegalStateException("asset copy failed: install-toolchain.sh")
        }
        script.setExecutable(true)

        val env = HashMap<String, String>()
        Environment.putEnvironment(env, false)
        env["PREFIX"] = Environment.PREFIX.absolutePath
        env["TMPDIR"] = Environment.TMP_DIR.absolutePath
        env["ANDROID_HOME"] = Environment.ANDROID_HOME.absolutePath
        env["PATH"] = Environment.BIN_DIR.absolutePath + ":" + System.getenv("PATH")

        val process = ProcessBuilder(
          Environment.BASH_SHELL.absolutePath,
          script.absolutePath,
          "--$typeToken", value
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
          if (isAdded) {
            if (code == 0) {
              when (typeToken) {
                "platform" -> selectedPlatforms -= value
                "build-tools" -> selectedBuildTools -= value
                "ndk" -> selectedNdkVersions -= value
                "cmake" -> selectedCmakeVersions -= value
              }
              refreshComponentLists()
              flashSuccess(R.string.msg_sdk_manager_uninstalled)
            } else {
              appendInstallLine(getString(R.string.msg_sdk_manager_uninstall_failed, code))
            }
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
          installingToolchain = false
          if (isAdded) {
            setUiEnabled(true)
          }
        }
      }
    }.apply {
      isDaemon = true
      start()
    }
  }

  private fun labelOf(typeToken: String, value: String): String {
    val configurable = when (typeToken) {
      "platform" -> getString(R.string.label_platforms)
      "build-tools" -> getString(R.string.label_build_tools)
      "ndk" -> getString(R.string.label_ndk)
      "cmake" -> getString(R.string.label_cmake)
      else -> typeToken
    }
    return "$configurable $value"
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
        val host = activity
        if (host != null) {
          host.runOnUiThread {
            if (isAdded) {
              updateConnectionStatus(networkCapabilities)
            }
          }
        }
      }

      override fun onLost(network: Network) {
        val host = activity
        if (host != null) {
          host.runOnUiThread {
            if (isAdded) {
              updateConnectionStatus(ConnectionInfo.UNKNOWN)
            }
          }
        }
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
