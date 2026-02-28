package com.watchlauncher

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
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    private var filteredApps: List<AppInfo> = apps.toList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val label: TextView = view.findViewById(R.id.appLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = filteredApps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.itemView.setOnClickListener { onAppClick(app) }
        holder.itemView.setOnLongClickListener {
            onAppLongClick(app)
            true
        }
    }

    override fun getItemCount() = filteredApps.size

    /** Filter by search query */
    fun filter(query: String) {
        filteredApps = if (query.isBlank()) {
            apps.toList()
        } else {
            apps.filter { it.label.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }

    /** Update the full app list (after install/uninstall) */
    fun updateApps(newApps: List<AppInfo>) {
        apps = newApps
        filteredApps = newApps.toList()
        notifyDataSetChanged()
    }
}
