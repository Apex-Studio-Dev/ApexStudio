package dev.apexstudio.ide.layouteditor.activities

import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.appcompat.app.AlertDialog
import dev.apexstudio.ide.layouteditor.BaseActivity
import dev.apexstudio.ide.layouteditor.LayoutFile
import dev.apexstudio.ide.layouteditor.R
import dev.apexstudio.ide.resources.R.string
import dev.apexstudio.ide.layouteditor.databinding.ActivityPreviewLayoutBinding
import dev.apexstudio.ide.layouteditor.tools.XmlLayoutParser
import dev.apexstudio.ide.layouteditor.utils.Constants
import dev.apexstudio.ide.ui.themes.IThemeManager

class PreviewLayoutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityPreviewLayoutBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())
        IThemeManager.getInstance().applyTheme(this)
        @Suppress("DEPRECATION")
        val layoutFile = intent.extras?.getParcelable<LayoutFile>(Constants.EXTRA_KEY_LAYOUT)
        val basePath = layoutFile?.path?.let { java.io.File(it).parent }
        val parser = XmlLayoutParser(this, basePath)
        layoutFile?.readDesignFile()?.let { parser.processXml(it, this) }

        val previewContainer = binding.root.findViewById<ViewGroup>(R.id.preview_container)

        val layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )

        parser.root?.let { rootView ->
            (rootView.parent as? ViewGroup)?.removeView(rootView)
            previewContainer.addView(rootView, layoutParams)
        } ?: run {
            showErrorDialog()
        }
    }

    private fun showErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(string.preview_render_error_title))
            .setMessage(getString(string.preview_render_error_message))
            .setPositiveButton(getString(string.msg_ok)) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }
}