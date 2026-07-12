package com.neolauncher.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.neolauncher.R
import java.text.SimpleDateFormat
import java.util.*

class LauncherActivity : AppCompatActivity() {

    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private lateinit var focusIndicator: TextView
    private lateinit var focusTimer: TextView
    private lateinit var musicPlayer: LinearLayout
    private lateinit var tvTrackTitle: TextView
    private lateinit var tvTrackArtist: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnPrev: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var pinnedApps: LinearLayout
    private lateinit var allAppsOverlay: FrameLayout
    private lateinit var etSearch: EditText
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var btnCloseAllApps: ImageView
    private lateinit var focusOverlay: FrameLayout
    private lateinit var btnCloseFocus: ImageView
    private lateinit var tvFocusCountdown: TextView
    private lateinit var btnStartFocus: TextView
    private lateinit var btnResetFocus: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var currentController: MediaController? = null
    private var isFocusMode = false
    private var focusDuration = 25 * 60 * 1000L
    private var focusTimeRemaining = focusDuration
    private var isFocusRunning = false
    private var focusTab = 0

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_launcher)

        initViews()
        setupClock()
        setupPinnedApps()
        setupBottomActions()
        setupAllAppsOverlay()
        setupFocusOverlay()
        setupMediaSession()
    }

    override fun onResume() {
        super.onResume()
        handler.post(clockRunnable)
        updateMusicInfo()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable)
    }

    private fun initViews() {
        tvClock = findViewById(R.id.tvClock)
        tvDate = findViewById(R.id.tvDate)
        focusIndicator = findViewById(R.id.focusIndicator)
        focusTimer = findViewById(R.id.focusTimer)
        musicPlayer = findViewById(R.id.musicPlayer)
        tvTrackTitle = findViewById(R.id.tvTrackTitle)
        tvTrackArtist = findViewById(R.id.tvTrackArtist)
        seekBar = findViewById(R.id.seekBar)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        pinnedApps = findViewById(R.id.pinnedApps)
        allAppsOverlay = findViewById(R.id.allAppsOverlay)
        etSearch = findViewById(R.id.etSearch)
        appsRecyclerView = findViewById(R.id.appsRecyclerView)
        btnCloseAllApps = findViewById(R.id.btnCloseAllApps)
        focusOverlay = findViewById(R.id.focusOverlay)
        btnCloseFocus = findViewById(R.id.btnCloseFocus)
        tvFocusCountdown = findViewById(R.id.tvFocusCountdown)
        btnStartFocus = findViewById(R.id.btnStartFocus)
        btnResetFocus = findViewById(R.id.btnResetFocus)
    }

    private fun setupClock() {
        val clockRunnable = object : Runnable {
            override fun run() {
                updateClock()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(clockRunnable)
    }

    private fun updateClock() {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvClock.text = sdf.format(Date())

        val dateSdf = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es"))
        tvDate.text = dateSdf.format(Date())
    }

    private fun setupPinnedApps() {
        val apps = listOf(
            "com.whatsapp" to "whatsapp",
            "com.instagram.lite" to "instagram lite",
            "org.schabi.newpipe" to "newpipe",
            "com.shazam.android" to "shazam",
            "com.x8bit.bitwarden" to "bitwarden",
            "com.android.settings" to "settings"
        )

        pinnedApps.removeAllViews()

        apps.forEach { (packageName, label) ->
            val appInfo = try {
                packageManager.getApplicationInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }

            if (appInfo) {
                val tv = TextView(this).apply {
                    text = label
                    setTextColor(getColor(R.color.text_tertiary))
                    textSize = 16f
                    setPadding(0, 12, 0, 12)
                    setOnClickListener {
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        if (intent != null) {
                            startActivity(intent)
                            overridePendingTransition(R.anim.scale_in, android.R.anim.fade_out)
                        }
                    }
                }
                pinnedApps.addView(tv)
            }
        }
    }

    private fun setupBottomActions() {
        findViewById<TextView>(R.id.btnAllApps).setOnClickListener { showAllApps() }
        findViewById<TextView>(R.id.btnFocus).setOnClickListener { showFocusMode() }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            val intent = Intent("com.neoconfigurator.MAIN")
            intent.setPackage("com.neoconfigurator")
            try {
                startActivity(intent)
            } catch (e: Exception) {
                val intent2 = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent2.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent2)
            }
            overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out)
        }
    }

    private fun setupAllAppsOverlay() {
        // Setup is done in showAllApps()
    }

    private fun showAllApps() {
        allAppsOverlay.visibility = View.VISIBLE
        allAppsOverlay.alpha = 0f
        allAppsOverlay.animate().alpha(1f).setDuration(200).start()

        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { packageManager.getApplicationLabel(it).toString() }

        val adapter = AllAppsAdapter(apps) { appInfo ->
            val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (intent != null) {
                startActivity(intent)
                overridePendingTransition(R.anim.scale_in, android.R.anim.fade_out)
            }
        }

        appsRecyclerView.layoutManager = LinearLayoutManager(this)
        appsRecyclerView.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString() ?: "")
            }
        })

        btnCloseAllApps.setOnClickListener { hideAllApps() }
    }

    private fun hideAllApps() {
        allAppsOverlay.animate().alpha(0f).setDuration(150).withEndAction {
            allAppsOverlay.visibility = View.GONE
        }.start()
    }

    private fun showFocusMode() {
        focusOverlay.visibility = View.VISIBLE
        focusOverlay.alpha = 0f
        focusOverlay.animate().alpha(1f).setDuration(200).start()
        updateFocusUI()
    }

    private fun setupFocusOverlay() {
        findViewById<TextView>(R.id.btnPomodoro).setOnClickListener {
            focusTab = 0; focusDuration = 25 * 60 * 1000L; resetFocus()
        }
        findViewById<TextView>(R.id.btnShortBreak).setOnClickListener {
            focusTab = 1; focusDuration = 5 * 60 * 1000L; resetFocus()
        }
        findViewById<TextView>(R.id.btnLongBreak).setOnClickListener {
            focusTab = 2; focusDuration = 15 * 60 * 1000L; resetFocus()
        }

        btnStartFocus.setOnClickListener { toggleFocus() }
        btnResetFocus.setOnClickListener { resetFocus() }
        btnCloseFocus.setOnClickListener { hideFocusMode() }
    }

    private fun toggleFocus() {
        if (isFocusRunning) {
            isFocusRunning = false
            btnStartFocus.text = getString(R.string.start)
        } else {
            isFocusRunning = true
            isFocusMode = true
            btnStartFocus.text = "pause"
            focusIndicator.visibility = View.VISIBLE
            runFocusTimer()
        }
    }

    private fun runFocusTimer() {
        val focusHandler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isFocusRunning && focusTimeRemaining > 0) {
                    focusTimeRemaining -= 1000
                    updateFocusUI()
                    focusHandler.postDelayed(this, 1000)
                } else if (focusTimeRemaining <= 0) {
                    isFocusRunning = false
                    isFocusMode = false
                    focusIndicator.visibility = View.GONE
                    btnStartFocus.text = getString(R.string.start)
                }
            }
        }
        focusHandler.post(runnable)
    }

    private fun resetFocus() {
        focusTimeRemaining = focusDuration
        isFocusRunning = false
        btnStartFocus.text = getString(R.string.start)
        updateFocusUI()
    }

    private fun updateFocusUI() {
        val minutes = (focusTimeRemaining / 1000) / 60
        val seconds = (focusTimeRemaining / 1000) % 60
        tvFocusCountdown.text = String.format("%02d:%02d", minutes, seconds)
        focusTimer.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun hideFocusMode() {
        focusOverlay.animate().alpha(0f).setDuration(150).withEndAction {
            focusOverlay.visibility = View.GONE
        }.start()
    }

    private fun setupMediaSession() {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = msm.getActiveSessions(null)
            if (controllers.isNotEmpty()) {
                currentController = controllers[0]
                updateMusicInfo()
            }
        } catch (e: Exception) {
            musicPlayer.visibility = View.GONE
        }

        btnPlayPause.setOnClickListener { currentController?.transportControls?.play() }
        btnPrev.setOnClickListener { currentController?.transportControls?.skipToPrevious() }
        btnNext.setOnClickListener { currentController?.transportControls?.skipToNext() }
    }

    private fun updateMusicInfo() {
        val controller = currentController ?: run {
            musicPlayer.visibility = View.GONE
            return
        }
        val metadata = controller.metadata
        if (metadata != null) {
            musicPlayer.visibility = View.VISIBLE
            tvTrackTitle.text = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
            tvTrackArtist.text = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        } else {
            musicPlayer.visibility = View.GONE
        }
    }

    override fun onBackPressed() {
        when {
            allAppsOverlay.visibility == View.VISIBLE -> hideAllApps()
            focusOverlay.visibility == View.VISIBLE -> hideFocusMode()
            else -> {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                startActivity(intent)
            }
        }
    }

    class AllAppsAdapter(
        private val apps: List<android.content.pm.ApplicationInfo>,
        private val onItemClick: (android.content.pm.ApplicationInfo) -> Unit
    ) : RecyclerView.Adapter<AllAppsAdapter.ViewHolder>() {

        private var filteredApps = apps.toList()
        private val packageManager = com.neolauncher.NeoLauncherApp.instance.packageManager

        fun filter(query: String) {
            filteredApps = if (query.isEmpty()) {
                apps
            } else {
                apps.filter {
                    packageManager.getApplicationLabel(it).toString()
                        .contains(query, ignoreCase = true)
                }
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                setPadding(32, 24, 32, 24)
                setTextColor(parent.context.getColor(R.color.text_secondary))
                textSize = 16f
            }
            return ViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = filteredApps[position]
            holder.textView.text = packageManager.getApplicationLabel(app)
            holder.itemView.setOnClickListener { onItemClick(app) }
        }

        override fun getItemCount() = filteredApps.size

        class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    }
}
