package com.amerganim.lockapp

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.amerganim.lockapp.databinding.ActivityVaultViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen viewer for a single decrypted vault image. */
class VaultViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
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
