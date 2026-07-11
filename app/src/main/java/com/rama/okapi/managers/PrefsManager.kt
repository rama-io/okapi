package com.rama.okapi.managers

import android.content.Context
import android.content.SharedPreferences
import com.rama.bohio.objects.PrefTheme
import com.rama.bohio.objects.PrefKeys
import com.rama.bohio.managers.PrefsManager as BohioPrefsManager

class PrefsManager private constructor(context: Context) : BohioPrefsManager(context) {

    override val defaultTheme: String = PrefTheme.MELANGE

    object FileKeys {
        const val PREF_DARK_THEME = "app:pref:dark_theme"
        const val PREF_LIGHT_THEME = "app:pref:light_theme"
        const val PREF_LIGHT_MODE = "app:pref:light_mode"
        const val PREF_QUICK_ERASE = "app:pref:quick_erase"
    }

    fun getPreferredDarkTheme(): String =
        getString(FileKeys.PREF_DARK_THEME, PrefTheme.DRACULA)

    fun setPreferredDarkTheme(theme: String) {
        setString(FileKeys.PREF_DARK_THEME, theme)
        if (!isLightMode()) applyActiveTheme()
    }

    fun getPreferredLightTheme(): String =
        getString(FileKeys.PREF_LIGHT_THEME, PrefTheme.CATPPUCCIN_LATTE)

    fun setPreferredLightTheme(theme: String) {
        setString(FileKeys.PREF_LIGHT_THEME, theme)
        if (isLightMode()) applyActiveTheme()
    }

    fun isLightMode(): Boolean = getBoolean(FileKeys.PREF_LIGHT_MODE, false)

    /** Flips between the preferred dark and light theme and returns the newly active one. */
    fun toggleThemeMode(): String {
        setBoolean(FileKeys.PREF_LIGHT_MODE, !isLightMode())
        return applyActiveTheme()
    }

    /** Applies whichever theme matches the current light/dark mode and returns it. */
    fun applyActiveTheme(): String {
        val theme = if (isLightMode()) getPreferredLightTheme() else getPreferredDarkTheme()
        setTheme(theme)
        return theme
    }

    override fun applyAppDefaults(editor: SharedPreferences.Editor) {
        editor
            .putString(FileKeys.PREF_DARK_THEME, PrefTheme.MELANGE)
            .putString(FileKeys.PREF_LIGHT_THEME, PrefTheme.CATPPUCCIN_LATTE)
            .putBoolean(FileKeys.PREF_LIGHT_MODE, false)
            .putBoolean(FileKeys.PREF_QUICK_ERASE, true)
            .putBoolean(PrefKeys.SYSTEM_PREVENT_SLEEP, true)
    }

    override fun initPrefs(sync: Boolean) {
        super.initPrefs(sync)
        applyActiveTheme()
    }

    companion object {
        @Volatile
        private var instance: PrefsManager? = null

        fun getInstance(context: Context): PrefsManager {
            return instance ?: synchronized(this) {
                instance ?: PrefsManager(context.applicationContext).also {
                    instance = it
                    register(it)
                }
            }
        }
    }
}