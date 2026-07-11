package com.rama.okapi.activities.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import com.rama.okapi.R
import com.rama.bohio.R as BohioR
import com.rama.okapi.activities.SettingsActivity
import com.rama.bohio.managers.FontManager
import com.rama.bohio.objects.PrefFontStyle
import com.rama.bohio.objects.PrefTheme
import java.io.File
import java.io.FileOutputStream
import com.rama.bohio.widgets.WdRange

class SettingsAppearanceController(private val activity: SettingsActivity) {

    private val prefs get() = activity.prefs

    fun setup() {
        setupFontStyle()
        setupTheme()
        setupUiScale()
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == activity.FONT_PICK_REQUEST && resultCode == Activity.RESULT_OK) {
            val uri: Uri = data?.data ?: return
            val savedPath = copyFontToInternalStorage(uri)
            if (savedPath != null) {
                FontManager.clearCustomCache()
                prefs.setCustomFontPath(savedPath)
                prefs.setFontStyle(PrefFontStyle.CUSTOM)
                updateCustomFontLabel()
                activity.refreshFont()
            }
        }
    }

    private fun setupFontStyle() {
        val group = activity.findViewById<RadioGroup>(R.id.font_style_group)
        val customContainer = activity.findViewById<View>(R.id.custom_font_container)

        when (prefs.getFontStyle()) {
            PrefFontStyle.JERSEY_25 -> group.check(R.id.font_jersey)
            PrefFontStyle.CUSTOM -> group.check(R.id.font_custom)
            else -> group.check(R.id.font_default)
        }

        customContainer.visibility =
            if (prefs.getFontStyle() == PrefFontStyle.CUSTOM) View.VISIBLE else View.GONE

        group.setOnCheckedChangeListener { _, id ->
            when (id) {
                R.id.font_jersey -> {
                    customContainer.visibility = View.GONE
                    prefs.setFontStyle(PrefFontStyle.JERSEY_25)
                    activity.refreshFont()
                }

                R.id.font_default -> {
                    customContainer.visibility = View.GONE
                    prefs.setFontStyle(PrefFontStyle.DEFAULT)
                    activity.refreshFont()
                }

                R.id.font_custom -> {
                    customContainer.visibility = View.VISIBLE
                    prefs.setFontStyle(PrefFontStyle.CUSTOM)
                    activity.refreshFont()
                }
            }
        }

        activity.findViewById<View>(R.id.font_custom_pick_btn).setOnClickListener {
            openFontPicker()
        }

        updateCustomFontLabel()
    }

    private fun openFontPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES, arrayOf(
                    "font/ttf", "font/otf", "application/x-font-ttf",
                    "application/x-font-otf", "application/octet-stream"
                )
            )
        }
        activity.startActivityForResult(intent, activity.FONT_PICK_REQUEST)
    }

    private fun copyFontToInternalStorage(uri: Uri): String? {
        return runCatching {
            val inputStream = activity.contentResolver.openInputStream(uri) ?: return null
            val dir = File(activity.filesDir, "fonts").also { it.mkdirs() }
            // Preserve extension (.ttf / .otf) for Typeface.createFromFile
            val ext = activity.contentResolver.getType(uri)
                ?.let { if (it.contains("otf")) "otf" else "ttf" } ?: "ttf"
            val dest = File(dir, "custom_font.$ext")
            FileOutputStream(dest).use { out -> inputStream.copyTo(out) }
            dest.absolutePath
        }.getOrNull()
    }

    private fun updateCustomFontLabel() {
        val label = activity.findViewById<TextView>(R.id.font_custom_name_label)
        val path = prefs.getCustomFontPath()
        label.text =
            if (path.isNotBlank()) File(path).name else activity.getString(BohioR.string.filepicker_font_custom_none)
    }

    private fun setupTheme() {
        val darkGroup = activity.findViewById<RadioGroup>(R.id.themes_group)
        val lightGroup = activity.findViewById<RadioGroup>(R.id.themes_light_group)

        when (prefs.getPreferredDarkTheme()) {
            PrefTheme.TEYIN -> darkGroup.check(R.id.theme_teyin)
            PrefTheme.MAKO -> darkGroup.check(R.id.theme_mako)
            PrefTheme.RAMA -> darkGroup.check(R.id.theme_rama)
            PrefTheme.CATPPUCCIN_MOCHA -> darkGroup.check(R.id.theme_catppuccin_mocha)
            PrefTheme.DRACULA -> darkGroup.check(R.id.theme_dracula)
            PrefTheme.MELANGE -> darkGroup.check(R.id.theme_melange)
            PrefTheme.TOKYO_NIGHT -> darkGroup.check(R.id.theme_tokyo_night)
            PrefTheme.MONO_DARK -> darkGroup.check(R.id.theme_mono_dark)
            else -> darkGroup.check(R.id.theme_melange)
        }

        when (prefs.getPreferredLightTheme()) {
            PrefTheme.CATPPUCCIN_LATTE -> lightGroup.check(R.id.theme_catppuccin_latte)
            PrefTheme.MONO_LIGHT -> lightGroup.check(R.id.theme_mono_light)
            else -> lightGroup.check(R.id.theme_catppuccin_latte)
        }

        darkGroup.setOnCheckedChangeListener { _, id ->
            val theme = when (id) {
                R.id.theme_teyin -> PrefTheme.TEYIN
                R.id.theme_mako -> PrefTheme.MAKO
                R.id.theme_rama -> PrefTheme.RAMA
                R.id.theme_catppuccin_mocha -> PrefTheme.CATPPUCCIN_MOCHA
                R.id.theme_dracula -> PrefTheme.DRACULA
                R.id.theme_melange -> PrefTheme.MELANGE
                R.id.theme_tokyo_night -> PrefTheme.TOKYO_NIGHT
                R.id.theme_mono_dark -> PrefTheme.MONO_DARK
                else -> PrefTheme.MELANGE
            }
            prefs.setPreferredDarkTheme(theme)
            activity.applyCurrentTheme()
        }

        lightGroup.setOnCheckedChangeListener { _, id ->
            val theme = when (id) {
                R.id.theme_catppuccin_latte -> PrefTheme.CATPPUCCIN_LATTE
                R.id.theme_mono_light -> PrefTheme.MONO_LIGHT
                else -> PrefTheme.CATPPUCCIN_LATTE
            }
            prefs.setPreferredLightTheme(theme)
            activity.applyCurrentTheme()
        }
    }

    private fun setupUiScale() {
        val range = activity.findViewById<WdRange>(R.id.zoom)

        val savedScale = prefs.getUiScale()

        range.onValueChanged = { value ->
            val scale = value.toFloatOrNull() ?: 1f
            if (scale != prefs.getUiScale()) {
                prefs.setUiScale(scale)
                activity.recreate()
            }
        }

        val steps = activity.resources.getStringArray(BohioR.array.ui_scale_steps).toList()
        val matchIndex = steps.indexOfFirst { it.toFloatOrNull() == savedScale }
        if (matchIndex >= 0) {
            range.post {
                val container = range.findViewById<android.widget.LinearLayout>(BohioR.id.container)
                (container?.getChildAt(matchIndex) as? android.widget.Button)?.performClick()
            }
        }
    }
}