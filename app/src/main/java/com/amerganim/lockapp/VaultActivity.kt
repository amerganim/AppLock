package com.amerganim.lockapp

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amerganim.lockapp.databinding.ActivityVaultBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Encrypted photo/video vault: import from the gallery, view, export, delete. */
class VaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultBinding
    private val items = mutableListOf<VaultDisplayItem>()
    private lateinit var adapter: VaultAdapter

    private lateinit var prefs: LockPrefs

    private val picker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(30)
    ) { uris -> if (uris.isNotEmpty()) importAll(uris) }

    private val readPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.isNotEmpty() && result.values.all { it }
        prefs.vaultRemoveOriginal = granted
        if (!granted) Toast.makeText(this, R.string.vault_remove_needs_perm, Toast.LENGTH_SHORT).show()
        updateRemoveOriginalMenu()
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, R.string.vault_originals_removed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Vault contents must not leak into screenshots / recents.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = LockPrefs(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_remove_original) { onRemoveOriginalToggled(!it.isChecked); true }
            else false
        }
        updateRemoveOriginalMenu()
        adapter = VaultAdapter(items, ::onItemClick, ::onItemLongClick)
        binding.list.layoutManager = GridLayoutManager(this, 3)
        binding.list.adapter = adapter
        binding.fabAdd.setOnClickListener {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }
        load()
    }

    private fun importAll(uris: List<Uri>) {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val isVideo = contentResolver.getType(uri)?.startsWith("video") == true
                    if (VaultManager.importMedia(this@VaultActivity, uri) != null) uri to isVideo else null
                }
            }
            load()
            if (imported.isNotEmpty() && prefs.vaultRemoveOriginal &&
                VaultManager.canDeleteOriginals && VaultManager.hasReadMedia(this@VaultActivity)
            ) {
                deleteOriginals(imported)
            }
        }
    }

    private fun deleteOriginals(imported: List<Pair<Uri, Boolean>>) {
        lifecycleScope.launch {
            val mediaStoreUris = withContext(Dispatchers.IO) {
                imported.mapNotNull { VaultManager.resolveMediaStoreUri(this@VaultActivity, it.first, it.second) }
            }
            if (mediaStoreUris.isEmpty()) return@launch
            val sender = VaultManager.createDeleteIntentSender(this@VaultActivity, mediaStoreUris)
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    private fun onRemoveOriginalToggled(enable: Boolean) {
        if (enable && !VaultManager.canDeleteOriginals) {
            Toast.makeText(this, R.string.vault_remove_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        if (enable && !VaultManager.hasReadMedia(this)) {
            readPermLauncher.launch(VaultManager.readMediaPermissions())
            return
        }
        prefs.vaultRemoveOriginal = enable
        updateRemoveOriginalMenu()
    }

    private fun updateRemoveOriginalMenu() {
        binding.toolbar.menu.findItem(R.id.action_remove_original)?.isChecked = prefs.vaultRemoveOriginal
    }

    private fun load() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                VaultManager.list(this@VaultActivity).map {
                    VaultDisplayItem(it, VaultManager.thumbnail(this@VaultActivity, it))
                }
            }
            items.clear()
            items.addAll(loaded)
            adapter.notifyDataSetChanged()
            binding.progress.visibility = View.GONE
            binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun onItemClick(entry: VaultEntry) {
        if (entry.isVideo) {
            lifecycleScope.launch {
                val file = withContext(Dispatchers.IO) { VaultManager.decryptToCache(this@VaultActivity, entry) }
                val uri = FileProvider.getUriForFile(this@VaultActivity, "$packageName.fileprovider", file)
                startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "video/mp4")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }
        } else {
            startActivity(
                Intent(this, VaultViewerActivity::class.java)
                    .putExtra(VaultViewerActivity.EXTRA_ID, entry.id)
            )
        }
    }

    private fun onItemLongClick(entry: VaultEntry) {
        MaterialAlertDialogBuilder(this)
            .setItems(
                arrayOf(getString(R.string.vault_export), getString(R.string.vault_delete))
            ) { _, which ->
                when (which) {
                    0 -> exportEntry(entry)
                    1 -> deleteEntry(entry)
                }
            }
            .show()
    }

    private fun exportEntry(entry: VaultEntry) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { VaultManager.exportToGallery(this@VaultActivity, entry) }
            toast(if (ok) R.string.vault_exported else R.string.vault_export_failed)
        }
    }

    private fun deleteEntry(entry: VaultEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vault_delete_confirm)
            .setPositiveButton(R.string.vault_delete) { _, _ ->
                VaultManager.delete(entry)
                load()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(resId: Int) =
        android.widget.Toast.makeText(this, resId, android.widget.Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        VaultManager.clearViewCache(this)
    }
}

data class VaultDisplayItem(val entry: VaultEntry, val bitmap: Bitmap?)

class VaultAdapter(
    private val items: List<VaultDisplayItem>,
    private val onClick: (VaultEntry) -> Unit,
    private val onLongClick: (VaultEntry) -> Unit
) : RecyclerView.Adapter<VaultAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val photo: ImageView = view.findViewById(R.id.photo)
        val playBadge: ImageView = view.findViewById(R.id.playBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_vault, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.photo.setImageBitmap(item.bitmap)
        holder.playBadge.visibility = if (item.entry.isVideo) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onClick(item.entry) }
        holder.itemView.setOnLongClickListener { onLongClick(item.entry); true }
    }

    override fun getItemCount(): Int = items.size
}
