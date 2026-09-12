package com.amerganim.lockapp

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.amerganim.lockapp.databinding.ActivityVaultViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen viewer for a single decrypted vault image. */
class VaultViewerActivity : SecureActivity() {

    private lateinit var binding: ActivityVaultViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // FLAG_SECURE is applied by SecureActivity.
        super.onCreate(savedInstanceState)
        binding = ActivityVaultViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra(EXTRA_ID)
        val entry = VaultManager.list(this).firstOrNull { it.id == id }
        if (entry == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                VaultManager.thumbnail(this@VaultViewerActivity, entry, reqWidth = 2000)
            }
            binding.fullPhoto.setImageBitmap(bitmap)
        }
    }

    companion object {
        const val EXTRA_ID = "extra_id"
    }
}
