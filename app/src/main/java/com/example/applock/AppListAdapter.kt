package com.example.applock

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.applock.databinding.ItemAppBinding

/** A launchable app the user can choose to lock. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

class AppListAdapter(
    private val items: List<AppEntry>,
    private val isLocked: (String) -> Boolean,
    private val onToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    inner class AppViewHolder(val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            name.text = item.label
            icon.setImageDrawable(item.icon)
            // Detach listener before setting state so recycling doesn't fire it.
            lockSwitch.setOnCheckedChangeListener(null)
            lockSwitch.isChecked = isLocked(item.packageName)
            lockSwitch.setOnCheckedChangeListener { _, checked ->
                onToggle(item.packageName, checked)
            }
            root.setOnClickListener { lockSwitch.toggle() }
        }
    }

    override fun getItemCount(): Int = items.size
}
