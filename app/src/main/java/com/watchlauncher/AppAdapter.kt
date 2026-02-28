package com.watchlauncher

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private var apps: List<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var filtered: List<AppInfo> = apps.toList()

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.appIcon)
        val label: TextView = v.findViewById(R.id.appLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun getItemCount() = filtered.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val app = filtered[position]

        // Try retro pixel icon first, fall back to real icon
        val retroBmp: Bitmap? = RetroIconPainter.getBitmap(app.packageName,
            h.itemView.context.resources.getDimensionPixelSize(R.dimen.icon_size))
        if (retroBmp != null) {
            h.icon.setImageBitmap(retroBmp)
        } else {
            h.icon.setImageDrawable(app.icon)
        }

        h.label.text = app.label
        h.itemView.setOnClickListener { onAppClick(app) }
        h.itemView.setOnLongClickListener { onAppLongClick(app); true }
    }

    fun filter(q: String) {
        filtered = if (q.isBlank()) apps.toList()
                   else apps.filter { it.label.contains(q, ignoreCase = true) }
        notifyDataSetChanged()
    }

    fun updateApps(newApps: List<AppInfo>) {
        apps = newApps; filtered = newApps.toList(); notifyDataSetChanged()
    }
}
