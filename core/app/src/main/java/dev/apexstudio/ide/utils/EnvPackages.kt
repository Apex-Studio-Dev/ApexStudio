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

/**
 * Tracks the packages that make up the ApexStudio build environment.
 *
 * These are installed through the `install-toolchain.sh --env-only` script
 * (from the Apex apt repository) and are read from the app prefix at runtime.
 *
 * @author Apex Studio Dev
 */
object EnvPackages {

  /** The recommended command-line tools that should be present after setup. */
  val recommended: List<String> = listOf("aapt2", "git", "jq", "unzip")

  /**
   * The names of the environment packages that are missing on this device.
   *
   * JDK presence is checked through the resolved [Environment.JAVA_HOME].
   */
  fun missingEnvPackages(): List<String> = buildList {
    for (name in recommended) {
      if (!File(Environment.BIN_DIR, name).exists()) {
        add(name)
      }
    }
    if (!File(Environment.JAVA_HOME, "bin/java").exists() && "openjdk" !in this) {
      add("openjdk")
    }
  }

  /** Whether all required environment packages are installed. */
  fun areEnvPackagesInstalled(): Boolean = missingEnvPackages().isEmpty()
}