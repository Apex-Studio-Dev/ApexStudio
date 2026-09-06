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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.SizeUtils
import dev.apexstudio.ide.BuildConfig
import dev.apexstudio.ide.R
import dev.apexstudio.ide.activities.AboutActivity
import dev.apexstudio.ide.activities.ContributorsActivity
import dev.apexstudio.ide.app.BaseApplication
import dev.apexstudio.ide.app.configuration.IDEBuildConfigProvider
import dev.apexstudio.ide.buildinfo.BuildInfo
import dev.apexstudio.ide.databinding.FragmentAboutPanelBinding
import dev.apexstudio.ide.models.IconTitleDescriptionItem
import dev.apexstudio.ide.models.SimpleIconTitleDescriptionItem
import dev.apexstudio.ide.utils.BuildInfoUtils
import dev.apexstudio.ide.utils.flashSuccess
import dev.apexstudio.ide.utils.resolveAttr

/**
 * "About" content rendered inside the main panel (embedded into MainActivity).
 *
 * Mirrors what [AboutActivity] shows but without its own toolbar/activity chrome.
 *
 * @author Apex Studio Dev
 */
class AboutPanelFragment : BaseFragment() {

  private var binding: FragmentAboutPanelBinding? = null

  private companion object {
    var id = 0
    val ACTION_WEBSITE = id++
    val ACTION_EMAIL = id++
    val ACTION_TG_CHANNEL = id++
    val ACTION_TG_GROUP = id++
    val ACTION_CONTRIBUTE = id++
    val ACTION_CONTRIBUTORS = id++
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = FragmentAboutPanelBinding.inflate(inflater, container, false)
    return binding!!.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val binding = binding ?: return

    binding.aboutHeader.apply {
      ideVersion.text = createVersionText()
      ideVersion.isClickable = true
      ideVersion.isFocusable = true
      ideVersion.setBackgroundResource(R.drawable.bg_ripple)
      ideVersion.setOnClickListener {
        ClipboardUtils.copyText(BuildInfoUtils.getBuildInfoHeader())
        flashSuccess(R.string.copied)
      }
    }

    binding.socials.apply {
      sectionTitle.setText(R.string.title_socials)
      sectionItems.adapter =
        AboutActivity.AboutSocialItemsAdapter(createSocialItems(), ::handleActionClick)
    }

    binding.misc.apply {
      sectionTitle.setText(R.string.title_misc)
      sectionItems.adapter =
        AboutActivity.AboutSocialItemsAdapter(createMiscItems(), ::handleActionClick)
    }
  }

  private fun handleActionClick(action: SimpleIconTitleDescriptionItem) {
    val app = BaseApplication.getBaseInstance()
    when (action.id) {
      ACTION_WEBSITE -> app.openWebsite()
      ACTION_EMAIL -> app.emailUs()
      ACTION_TG_GROUP -> app.openTelegramGroup()
      ACTION_TG_CHANNEL -> app.openTelegramChannel()
      ACTION_CONTRIBUTE -> app.openUrl(BaseApplication.CONTRIBUTOR_GUIDE_URL)
      ACTION_CONTRIBUTORS -> startActivity(
        Intent(requireActivity(), ContributorsActivity::class.java)
      )
    }
  }

  private fun createSocialItems(): List<IconTitleDescriptionItem> {
    return mutableListOf<IconTitleDescriptionItem>().apply {
      add(
        createSimpleIconTextItem(
          ACTION_WEBSITE,
          R.drawable.ic_website,
          R.string.about_option_website,
          BuildInfo.PROJECT_SITE
        )
      )
      add(
        createSimpleIconTextItem(
          ACTION_EMAIL,
          R.drawable.ic_email,
          R.string.about_option_email,
          BaseApplication.EMAIL
        )
      )
      add(
        createSimpleIconTextItem(
          ACTION_TG_GROUP,
          R.drawable.ic_telegram,
          R.string.discussions_on_telegram,
          BaseApplication.TELEGRAM_GROUP_URL
        )
      )
      add(
        createSimpleIconTextItem(
          ACTION_TG_CHANNEL,
          R.drawable.ic_telegram,
          R.string.official_tg_channel,
          BaseApplication.TELEGRAM_CHANNEL_URL
        )
      )
    }
  }

  private fun createMiscItems(): List<IconTitleDescriptionItem> {
    return mutableListOf<IconTitleDescriptionItem>().apply {
      add(
        SimpleIconTitleDescriptionItem.create(
          requireContext(),
          ACTION_CONTRIBUTE,
          R.drawable.ic_code,
          R.string.title_contribute,
          R.string.summary_contribute
        )
      )
      add(
        SimpleIconTitleDescriptionItem.create(
          requireContext(),
          ACTION_CONTRIBUTORS,
          R.drawable.ic_heart_outline,
          R.string.title_contributors,
          R.string.summary_contributors
        )
      )
    }
  }

  private fun createSimpleIconTextItem(
    id: Int,
    @DrawableRes icon: Int,
    @StringRes title: Int,
    description: CharSequence
  ): SimpleIconTitleDescriptionItem {
    return SimpleIconTitleDescriptionItem(
      id,
      ContextCompat.getDrawable(requireContext(), icon),
      ContextCompat.getString(requireContext(), title),
      description
    )
  }

  /**
   * Create the version name string displayed to the user.
   *
   * Format: `v[version-name]-[variant] ([build-type]/[[UN]OFFICIAL])`
   */
  @Suppress("KDocUnresolvedReference")
  private fun createVersionText(): CharSequence {
    val context: Context = requireContext()
    val builder = SpannableStringBuilder()
    builder.append("v")
    builder.append(BuildInfo.VERSION_NAME_SIMPLE)
    builder.append("-")
    builder.append(IDEBuildConfigProvider.getInstance().cpuAbiName)
    builder.append(" ")

    val colorPositive = ContextCompat.getColor(context, R.color.color_success)
    val colorNegative = ContextCompat.getColor(context, R.color.color_error)

    appendBuildType(builder, colorPositive, colorNegative, context)

    return builder
  }

  private fun appendBuildType(
    builder: SpannableStringBuilder,
    @ColorInt
    colorPositive: Int,
    @ColorInt
    colorNegative: Int,
    context: Context
  ) {
    @Suppress("KotlinConstantConditions")
    var color = if (BuildConfig.BUILD_TYPE != "release") {
      colorNegative
    } else {
      colorPositive
    }

    builder.append("(")
    appendForegroundSpan(builder, BuildConfig.BUILD_TYPE, color)

    val isOfficialBuild = BuildInfoUtils.isOfficialBuild(context)

    color = if (isOfficialBuild) {
      colorPositive
    } else {
      colorNegative
    }

    builder.append("/")
    appendForegroundSpan(
      builder,
      BuildInfoUtils.getBuildType(context).lowercase(),
      color
    )

    builder.append(")")
  }

  private fun appendForegroundSpan(
    builder: SpannableStringBuilder,
    text: CharSequence,
    color: Int
  ) {
    builder.append(
      text,
      ForegroundColorSpan(color),
      SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
    )
  }

  override fun onDestroyView() {
    super.onDestroyView()
    binding = null
  }
}