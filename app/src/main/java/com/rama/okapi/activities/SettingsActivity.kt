package com.rama.okapi.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.rama.okapi.CsActivity
import com.rama.okapi.R
import com.rama.okapi.activities.settings.SettingsAppearanceController
import com.rama.okapi.activities.settings.SettingsBasicController
import com.rama.okapi.activities.settings.SettingsCheckboxController
import com.rama.okapi.activities.settings.SettingsLanguageController

class SettingsActivity : CsActivity() {
    private lateinit var appearanceController: SettingsAppearanceController
    private lateinit var settingsRootView: View
    val FONT_PICK_REQUEST = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_settings)

        settingsRootView = findViewById(R.id.settings_root)
        applyEdgeToEdgePadding(settingsRootView)
        applyCurrentTheme(settingsRootView)

        SettingsBasicController(this).setup()
        appearanceController = SettingsAppearanceController(this).also { it.setup() }
        SettingsLanguageController(this).setup()
        SettingsCheckboxController(this).setup()
    }

    override fun onResume() {
        super.onResume()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        appearanceController.onActivityResult(requestCode, resultCode, data)
    }
}