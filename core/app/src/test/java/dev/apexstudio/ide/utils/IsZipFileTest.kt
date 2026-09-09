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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tests for [isZipFile].
 *
 * @author Apex Studio Dev
 */
class IsZipFileTest {

  @Test
  fun `empty file is not a zip`() {
    val file = File.createTempFile("empty", ".zip")
    file.writeBytes(ByteArray(0))
    assertThat(isZipFile(file)).isFalse()
  }

  @Test
  fun `non-zip file is not a zip`() {
    val file = File.createTempFile("text", ".apk")
    file.writeText("hello world not a zip")
    assertThat(isZipFile(file)).isFalse()
  }

  @Test
  fun `valid zip file is detected`() {
    val file = File.createTempFile("valid", ".zip")
    ZipOutputStream(file.outputStream()).use { zip ->
      zip.putNextEntry(ZipEntry("test.txt"))
      zip.write("hello".toByteArray())
      zip.closeEntry()
    }
    assertThat(isZipFile(file)).isTrue()
  }

  @Test
  fun `nonexistent file is not a zip`() {
    val file = File("nonexistent_zip_test_file_1234567.zip")
    assertThat(isZipFile(file)).isFalse()
  }

  @Test
  fun `directory is not a zip`() {
    val dir = File.createTempFile("dir", "").apply { delete(); mkdirs() }
    assertThat(isZipFile(dir)).isFalse()
  }

  @Test
  fun `file with only pk prefix but too short is not a zip`() {
    val file = File.createTempFile("short", ".apk")
    file.writeBytes(byteArrayOf(0x50, 0x4B))
    assertThat(isZipFile(file)).isFalse()
  }
}
