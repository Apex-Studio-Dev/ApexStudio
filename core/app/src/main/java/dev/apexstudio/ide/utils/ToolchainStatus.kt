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

package dev.apexstudio.ide.utils

import dev.apexstudio.ide.utils.Environment
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files

/**
 * Strong, marker-based checks for the presence of toolchain components.
 *
 * Merely checking that a directory exists is not enough: a failed or
 * interrupted install can leave an empty/partial directory behind that would
 * be reported as "installed". Every component here is instead considered
 * installed only when its real content is present, mirroring the checks the
 * install scripts themselves use:
 *
 * - JDK: `bin/java` present and executable
 * - cmdline-tools: `bin/sdkmanager` present and executable
 * - platform: `android.jar` present
 * - build-tools: `aapt2` present and executable
 * - NDK: `source.properties` present (symlink tokens resolve through the OS)
 * - CMake: `bin/cmake` present and executable
 * - env packages: executable file in `$PREFIX/bin`
 *
 * @author Apex Studio Dev
 */
/**
 * ZIP local-file-header magic: `PK\x03\x04`.
 * Shared by ZIP, JAR, APK, AAR, WAR — all are ZIP containers.
 */
private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

/**
 * Returns `true` when [file] is a readable regular file whose first four
 * bytes are the ZIP local-file-header magic (`PK\x03\x04`). This is stronger
 * than a mere `.exists()` / extension check: a truncated, empty or
 * misnamed file will not match.
 *
 * Works for APK, JAR, AAR, WAR and plain `.zip` files.
 */
fun isZipFile(file: File): Boolean {
  if (!file.isFile || file.length() < ZIP_MAGIC.size) {
    return false
  }
  return try {
    FileInputStream(file).use { input ->
      val header = ByteArray(ZIP_MAGIC.size)
      val read = input.read(header)
      read == ZIP_MAGIC.size && header.contentEquals(ZIP_MAGIC)
    }
  } catch (e: Exception) {
    false
  }
}

object ToolchainStatus {

  private fun File.isExecutableFile(): Boolean = isFile && canExecute()

  private fun jvmDir(version: String): File =
    File(File(Environment.PREFIX, "lib/jvm"), "java-$version-openjdk")

  private fun sdkmanagerFile(): File =
    File(Environment.ANDROID_HOME, "cmdline-tools/latest/bin/sdkmanager")

  /** True when the given JDK is actually installed (has an executable `java`). */
  fun jdkInstalled(version: String): Boolean =
    File(jvmDir(version), "bin/java").isExecutableFile()

  /** True when the resolved [Environment.JAVA_HOME] JDK is installed. */
  fun jdkAtHomeInstalled(): Boolean =
    File(Environment.JAVA_HOME, "bin/java").isExecutableFile()

  /** True when cmdline-tools (sdkmanager) is installed and runnable. */
  fun cmdlineToolsInstalled(): Boolean = sdkmanagerFile().isExecutableFile()

  /** True when an Android platform is fully downloaded (ships `android.jar`). */
  fun platformInstalled(api: String): Boolean =
    File(Environment.ANDROID_HOME, "platforms/android-$api/android.jar").isFile

  /** True when build-tools are fully installed (ships the `aapt2` binary). */
  fun buildToolsInstalled(version: String): Boolean =
    File(Environment.ANDROID_HOME, "build-tools/$version/aapt2").isExecutableFile()

  /**
   * True when an NDK is fully installed. The short token (e.g. `r27d`) is usually
   * a symlink to the canonical Pkg.Revision dir; a relative path through the
   * token resolves via the OS, so this works for both layouts.
   */
  fun ndkInstalled(token: String): Boolean =
    File(Environment.ANDROID_HOME, "ndk/$token/source.properties").isFile

  /** True when CMake is fully installed (ships an executable `bin/cmake`). */
  fun cmakeInstalled(version: String): Boolean =
    File(Environment.ANDROID_HOME, "cmake/$version/bin/cmake").isExecutableFile()

  /** True when an env package is present and executable in `$PREFIX/bin`. */
  fun envPackageInstalled(name: String): Boolean =
    File(Environment.BIN_DIR, name).isExecutableFile()

  /**
   * Scans the SDK/prefix component directories once and removes any leftover
   * entry from a failed or interrupted install: a directory is purged when it
   * does not pass its strong marker check. Only entries matching the expected
   * naming patterns are touched; valid installs are never removed.
   *
   * @return the absolute paths of the removed entries.
   */
  fun purgeStaleComponents(): List<String> {
    val removed = mutableListOf<String>()

    fun purgeSubdirs(
      parent: File,
      isValid: (File) -> Boolean,
      matchesPattern: (name: String) -> Boolean = { true }
    ) {
      if (!parent.isDirectory) {
        return
      }
      parent.listFiles()?.forEach { dir ->
        if (dir.isDirectory && matchesPattern(dir.name) && !isValid(dir)) {
          // A symlink token (e.g. ndk/r27d) must be unlinked, not followed and
          // deleted recursively into the canonical revision directory.
          val deleted = if (Files.isSymbolicLink(dir.toPath())) {
            dir.delete()
          } else {
            dir.deleteRecursively()
          }
          if (deleted) {
            removed += dir.absolutePath
          }
        }
      }
    }

    val sdkHome = Environment.ANDROID_HOME

    purgeSubdirs(File(sdkHome, "platforms"), { it.resolve("android.jar").isFile }) {
      it.startsWith("android-")
    }
    purgeSubdirs(File(sdkHome, "build-tools"), { dir ->
      dir.resolve("aapt2").isFile
    })
    purgeSubdirs(File(sdkHome, "ndk"), { dir ->
      dir.resolve("source.properties").isFile
    })
    purgeSubdirs(File(sdkHome, "cmake"), { dir ->
      dir.resolve("bin/cmake").isFile
    })
    purgeSubdirs(File(sdkHome, "cmdline-tools"), { dir ->
      dir.name == "latest" && dir.resolve("bin/sdkmanager").isFile
    }) { it == "latest" }
    purgeSubdirs(File(Environment.PREFIX, "lib/jvm"), { dir ->
      dir.resolve("bin/java").isFile
    }) { it.startsWith("java-") && it.endsWith("-openjdk") }

    return removed
  }
}