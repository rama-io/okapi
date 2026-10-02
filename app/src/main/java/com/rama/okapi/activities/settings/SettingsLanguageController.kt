package com.rama.okapi.activities.settings

import com.rama.bohio.objects.PrefLanguage
import com.rama.bohio.widgets.WdRadioGroup
import com.rama.okapi.R
import com.rama.okapi.activities.SettingsActivity

class SettingsLanguageController(private val activity: SettingsActivity) {

    private val prefs get() = activity.prefs

    fun setup() {
        val group = activity.findViewById<WdRadioGroup>(R.id.language_group)
        val codes = activity.resources.getStringArray(R.array.supported_language_codes)
        val labels = activity.resources.getStringArray(R.array.supported_language_labels)
        require(codes.size == labels.size) {
            "supported_language_codes (${codes.size}) and supported_language_labels (${labels.size}) must have the same length"
        }
        val currentLanguage = prefs.getAppLanguage()

        val codeToId = mutableMapOf<String, Int>()

        codes.zip(labels).forEach { (code, label) ->
            val radio = group.addOption(label)
            codeToId[code] = radio.id
        }

        codeToId[currentLanguage]?.let { group.check(it) }

        group.setOnCheckedChangeListener(object : WdRadioGroup.OnCheckedChangeListener {
            override fun onCheckedChanged(group: WdRadioGroup, checkedId: Int) {
                val language = codeToId.entries
                    .firstOrNull { it.value == checkedId }?.key
                    ?: PrefLanguage.SYSTEM

                if (language == prefs.getAppLanguage()) return

                prefs.setAppLanguage(language)
                activity.recreate()
            }
        })
    }
}
