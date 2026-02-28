package com.watchlauncher

import android.Manifest
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class WallpaperPickerActivity : AppCompatActivity() {

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { setWallpaperFromUri(it) }
    }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pickImage.launch("image/*")
        else Toast.makeText(this, "Permission needed to pick image", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpaper_picker)

        findViewById<Button>(R.id.btnPickGallery).setOnClickListener {
            checkPermissionAndPick()
        }

        // Preset gradient wallpapers
        findViewById<Button>(R.id.btnPreset1).setOnClickListener { applyPreset(R.drawable.wallpaper_preset_1) }
        findViewById<Button>(R.id.btnPreset2).setOnClickListener { applyPreset(R.drawable.wallpaper_preset_2) }
        findViewById<Button>(R.id.btnPreset3).setOnClickListener { applyPreset(R.drawable.wallpaper_preset_3) }

        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
    }

    private fun checkPermissionAndPick() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED ->
                pickImage.launch("image/*")
            else -> requestPermission.launch(permission)
        }
    }

    private fun setWallpaperFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            val wm = WallpaperManager.getInstance(this)
            wm.setBitmap(bitmap)
            Toast.makeText(this, "Wallpaper set!", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to set wallpaper: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyPreset(drawableRes: Int) {
        try {
            val bitmap = BitmapFactory.decodeResource(resources, drawableRes)
            val wm = WallpaperManager.getInstance(this)
            wm.setBitmap(bitmap)
            Toast.makeText(this, "Wallpaper applied!", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
