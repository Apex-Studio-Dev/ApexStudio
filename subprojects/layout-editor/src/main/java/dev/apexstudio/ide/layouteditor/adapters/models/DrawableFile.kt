package dev.apexstudio.ide.layouteditor.adapters.models

import android.graphics.drawable.Drawable
import dev.apexstudio.ide.layouteditor.utils.FileUtil

data class DrawableFile(var versions: Int, var drawable: Drawable, var path: String) {
  var name: String = FileUtil.getLastSegmentFromPath(path)
}
