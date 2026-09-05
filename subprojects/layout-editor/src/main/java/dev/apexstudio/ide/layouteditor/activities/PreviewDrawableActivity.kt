package dev.apexstudio.ide.layouteditor.activities

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.ActionBar
import dev.apexstudio.ide.layouteditor.BaseActivity
import dev.apexstudio.ide.layouteditor.R
import dev.apexstudio.ide.layouteditor.databinding.ActivityPreviewDrawableBinding
import dev.apexstudio.ide.layouteditor.views.AlphaPatternDrawable
import dev.apexstudio.ide.ui.themes.IThemeManager

class PreviewDrawableActivity : BaseActivity() {
  private lateinit var binding: ActivityPreviewDrawableBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityPreviewDrawableBinding.inflate(layoutInflater)
    setContentView(binding.getRoot())
    IThemeManager.getInstance().applyTheme(this)    

    setSupportActionBar(binding.topAppBar)
    supportActionBar!!.setTitle(R.string.preview_drawable)

    binding.topAppBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    binding.background.setImageDrawable(AlphaPatternDrawable(24))

    onLoad(binding.mainImage, supportActionBar)
  }

  //todo remove and replace this with some reasonable replacement.
  companion object {
    @JvmStatic
    var onLoad: (ImageView, ActionBar?) -> Unit = { _, _ -> }
  }
}
