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

package dev.apexstudio.ide.plugins

import dev.apexstudio.ide.plugins.util.DownloadUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem
import java.io.File

/**
 * Gradle plugin which downloads the bootstrap packages for the terminal.
 *
 * @author Akash Yadav
 */
class TerminalBootstrapPackagesPlugin : Plugin<Project> {

  companion object {

    /**
     * The bootstrap packages, mapped with the CPU ABI as the key and the ZIP file's sha256sum as the value.
     */
    private val BOOTSTRAP_PACKAGES = mapOf(
      "aarch64" to "473abdd3d5ec637277a98e32754fc58454d78fdf2a1859a9cf2a9b97bf15b8eb",
      "arm" to "42fc10fe13f729b43e41193a7702157fbc4e8a4055a310c9c5820d6b26819c8a"
    )

    /**
     * The bootstrap packages version, basically the tag name of the GitHub release.
     */
    private const val BOOTSTRAP_PACKAGES_VERSION = "2026.09.06-r1+apt.apexstudio-7"

    private const val PACKAGES_DOWNLOAD_URL =
      "https://github.com/Apex-Studio-Dev/termux-packages/releases/download/bootstrap-%1\$s/bootstrap-%2\$s.zip"
    private const val PACKAGES_DOWNLOAD_URL_ARM =
      "https://github.com/Apex-Studio-Dev/termux-packages/releases/download/bootstrap-%1\$s/bootstrap-%2\$s.zip"

  }

  override fun apply(target: Project) {
    target.run {

      val bootstrapOut = project.layout.buildDirectory.dir("bootstrap-packages")
        .get().asFile

      val files = BOOTSTRAP_PACKAGES.map { (arch, sha256) ->
        val file = File(bootstrapOut, "bootstrap-${arch}.zip")
        file.parentFile.mkdirs()
        val downUrl = if (arch == "arm"){
          PACKAGES_DOWNLOAD_URL_ARM.format(BOOTSTRAP_PACKAGES_VERSION, arch)
        }else{
          PACKAGES_DOWNLOAD_URL.format(BOOTSTRAP_PACKAGES_VERSION, arch)
        }

        DownloadUtils.doDownload(
          file = file,
          remoteUrl = downUrl,
          expectedChecksum = sha256,
          logger = logger
        )

        return@map arch to file
      }.toMap()

      project.file("src/main/cpp/termux-bootstrap-zip.S").writeText(
        """
             .global blob
             .global blob_size
             .section .rodata
         blob:
        #if defined __aarch64__
             .incbin "${escapePathOnWindows(files["aarch64"]!!.absolutePath)}"
         #elif defined __arm__
             .incbin "${escapePathOnWindows(files["arm"]!!.absolutePath)}"
         #else
         # error Unsupported arch
         #endif
         1:
         blob_size:
             .int 1b - blob
         
      """.trimIndent()
      )
    }
  }

  private fun escapePathOnWindows(path: String): String {
    if (OperatingSystem.current().isWindows) {
      // escape backslashes when building on Windows
      return path.replace("\\", "\\\\")
    }

    return path
  }
}