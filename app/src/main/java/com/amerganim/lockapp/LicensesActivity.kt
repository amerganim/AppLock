package com.amerganim.lockapp

import android.os.Bundle
import com.amerganim.lockapp.databinding.ActivityLicensesBinding

/**
 * The licences of the third-party work bundled in the app.
 *
 * The fonts ship under the SIL Open Font License, which asks that the licence travel
 * with them, so it has to be readable from inside the installed app and not only from
 * the repository.
 */
class LicensesActivity : SecureActivity() {

    private lateinit var binding: ActivityLicensesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLicensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.licenseText.text = resources.openRawResource(R.raw.open_source_licenses)
            .bufferedReader()
            .use { it.readText() }
    }
}
