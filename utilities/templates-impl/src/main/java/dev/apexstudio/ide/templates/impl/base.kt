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

package dev.apexstudio.ide.templates.impl

import dev.apexstudio.ide.templates.BooleanParameter
import dev.apexstudio.ide.templates.EnumParameter
import dev.apexstudio.ide.templates.Language
import dev.apexstudio.ide.templates.ProjectTemplate
import dev.apexstudio.ide.templates.ProjectVersionData
import dev.apexstudio.ide.templates.Sdk
import dev.apexstudio.ide.templates.StringParameter
import dev.apexstudio.ide.templates.base.AndroidModuleTemplateBuilder
import dev.apexstudio.ide.templates.base.ProjectTemplateBuilder
import dev.apexstudio.ide.templates.base.baseProject
import dev.apexstudio.ide.templates.impl.base.createRecipe
import dev.apexstudio.ide.templates.minSdkParameter
import dev.apexstudio.ide.templates.packageNameParameter
import dev.apexstudio.ide.templates.projectLanguageParameter
import dev.apexstudio.ide.templates.projectNameParameter
import dev.apexstudio.ide.templates.useKtsParameter

/**
 * Indents the given string for the given [indentation level][level].
 */
fun String.indentToLevel(level: Int): String {
  val lines = split(Regex("[\r\n]"))
  return StringBuilder().apply {
    for (line in lines) {
      append(line)
      append(" ".repeat(level * 4))
    }
  }.toString()
}

@Suppress("UnusedReceiverParameter")
internal fun AndroidModuleTemplateBuilder.templateAsset(name: String,
                                                        path: String
): String {
  return "templates/${name}/${path}"
}

internal inline fun baseProjectImpl(
  projectName: StringParameter = projectNameParameter(),
  packageName: StringParameter = packageNameParameter(),
  useKts: BooleanParameter = useKtsParameter(),
  minSdk: EnumParameter<Sdk> = minSdkParameter(),
  language: EnumParameter<Language> = projectLanguageParameter(),
  projectVersionData: ProjectVersionData = ProjectVersionData(),
  crossinline block: ProjectTemplateBuilder.() -> Unit
): ProjectTemplate =
  baseProject(projectName = projectName, packageName = packageName,
    useKts = useKts, minSdk = minSdk, language = language,
    projectVersionData = projectVersionData) {
    block()

    // make sure we return a proper result
    if (!isRecipeSet) {
      recipe = createRecipe {}
    }
  }