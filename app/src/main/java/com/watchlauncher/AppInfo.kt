package com.watchlauncher

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    var isPinned: Boolean = false   // true = show in dock
)
