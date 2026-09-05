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

package dev.apexstudio.ide.templates.impl.basicActivity

import dev.apexstudio.ide.templates.ProjectTemplate
import dev.apexstudio.ide.templates.base.AndroidModuleTemplateBuilder
import dev.apexstudio.ide.templates.base.modules.android.defaultAppModule
import dev.apexstudio.ide.templates.base.util.AndroidModuleResManager.ResourceType.LAYOUT
import dev.apexstudio.ide.templates.base.util.SourceWriter
import dev.apexstudio.ide.templates.impl.R
import dev.apexstudio.ide.templates.impl.base.createRecipe
import dev.apexstudio.ide.templates.impl.base.emptyThemesAndColors
import dev.apexstudio.ide.templates.impl.base.writeMainActivity
import dev.apexstudio.ide.templates.impl.baseProjectImpl

fun basicActivityProject(): ProjectTemplate {
  return baseProjectImpl {
    templateName = R.string.template_basic
    thumb = R.drawable.template_basic_activity
    defaultAppModule {
      recipe = createRecipe {
        sources {
          writeBasicActivitySrc(this)
        }

        res {
          writeBasicActivityLayout()
          emptyThemesAndColors()
        }
      }
    }
  }
}

private fun AndroidModuleTemplateBuilder.writeBasicActivitySrc(
  writer: SourceWriter
) {
  writeMainActivity(writer = writer, ktSrc = ::basicActivitySrcKt,
    javaSrc = ::basicActivitySrcJava)
}

internal fun AndroidModuleTemplateBuilder.writeBasicActivityLayout() {
  res.apply {
    writeXmlResource("activity_main", LAYOUT, source = ::basicActivityLayout)
    writeXmlResource("content_main", LAYOUT, source = ::basicActivityContent)
  }
}