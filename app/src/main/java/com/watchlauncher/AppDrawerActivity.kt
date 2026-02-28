package com.watchlauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlin.math.abs

class AppDrawerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchBar: EditText
    private lateinit var adapter: AppAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var gestureDetector: GestureDetectorCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_drawer)
        recyclerView = findViewById(R.id.appRecyclerView)
        searchBar    = findViewById(R.id.searchBar)
        setupRecyclerView()
        setupSearch()
        setupSwipeDismiss()
        loadApps()
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    private fun setupRecyclerView() {
        adapter = AppAdapter(
            apps = emptyList(),
            onAppClick = { launchApp(it.packageName) },
            onAppLongClick = { Toast.makeText(this, it.label, Toast.LENGTH_SHORT).show() }
        )
        // 2 columns — large icons like Nothing OS launcher
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { adapter.filter(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupSwipeDismiss() {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dY = e2.y - (e1?.y ?: 0f)
                if (dY > 100 && dY > abs((e1?.x ?: 0f) - e2.x) && abs(vy) > 300) {
                    finish(); overridePendingTransition(R.anim.fade_in, R.anim.slide_down); return true
                }
                return false
            }
            override fun onDown(e: MotionEvent) = true
        }
        gestureDetector = GestureDetectorCompat(this, listener)
        findViewById<View>(R.id.drawerRoot).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event); false
        }
    }

    private fun loadApps() {
        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                val pm = packageManager
                pm.queryIntentActivities(intent, 0)
                    .map { ri -> AppInfo(ri.loadLabel(pm).toString(), ri.activityInfo.packageName, ri.loadIcon(pm)) }
                    .sortedBy { it.label.lowercase() }
                    .filter { it.packageName != packageName }
            }
            adapter.updateApps(apps)
        }
    }

    private fun launchApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
            ?: Toast.makeText(this, "Cannot open app", Toast.LENGTH_SHORT).show()
    }
}
