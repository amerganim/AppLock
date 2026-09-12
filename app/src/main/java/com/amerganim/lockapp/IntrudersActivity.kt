package com.amerganim.lockapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amerganim.lockapp.databinding.ActivityIntrudersBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Gallery of captured intruder selfies, newest first. */
class IntrudersActivity : SecureActivity() {

    private lateinit var binding: ActivityIntrudersBinding
    private val items = mutableListOf<IntruderItem>()
    private val adapter = IntruderAdapter(items)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntrudersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_clear) { confirmClear(); true } else false
        }
        binding.list.layoutManager = GridLayoutManager(this, 2)
        binding.list.adapter = adapter
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                IntruderManager.list(this@IntrudersActivity).map { photo ->
                    IntruderItem(
                        bitmap = decodeScaled(photo.file, 500),
                        caption = "${labelOf(photo.packageName)}\n" +
                            DateUtils.getRelativeTimeSpanString(photo.timeMillis)
                    )
                }
            }
            items.clear()
            items.addAll(loaded)
            adapter.notifyDataSetChanged()
            binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmClear() {
        if (items.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.intruder_clear_confirm)
            .setPositiveButton(R.string.intruder_clear) { _, _ ->
                IntruderManager.clearAll(this)
                load()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun labelOf(pkg: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun decodeScaled(file: File, reqWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > reqWidth) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.path, opts)
    }
}

data class IntruderItem(val bitmap: Bitmap?, val caption: String)

class IntruderAdapter(private val items: List<IntruderItem>) :
    RecyclerView.Adapter<IntruderAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val photo: ImageView = view.findViewById(R.id.photo)
        val caption: TextView = view.findViewById(R.id.caption)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_intruder, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.photo.setImageBitmap(item.bitmap)
        holder.caption.text = item.caption
    }

    override fun getItemCount(): Int = items.size
}
