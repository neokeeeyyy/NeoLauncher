package com.neolauncher.ui

import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.neolauncher.NeoLauncherApp
import com.neolauncher.R
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashSet

class LauncherActivity : AppCompatActivity() {

    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private lateinit var clockFlipper: ViewFlipper
    private lateinit var musicPlayer: LinearLayout
    private lateinit var tvTrackTitle: TextView
    private lateinit var tvTrackArtist: TextView
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnPrev: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var ivAlbumArt: ImageView
    private lateinit var appsList: RecyclerView
    private lateinit var focusOverlay: FrameLayout
    private lateinit var btnCloseFocus: ImageView
    private lateinit var tvFocusCountdown: TextView
    private lateinit var btnPomodoro: TextView
    private lateinit var btnShortBreak: TextView
    private lateinit var btnLongBreak: TextView
    private lateinit var btnStartFocus: TextView
    private lateinit var btnResetFocus: TextView
    private lateinit var taskSwitcherOverlay: FrameLayout
    private lateinit var taskList: RecyclerView
    private var showingHidden = false
    private var permissionStep = 0

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        when (permissionStep) {
            1 -> requestAccessibilityService()
            2 -> requestUsageStats()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentController: MediaController? = null
    private var isMusicPlaying = false
    private var hasMusic = false
    private var activeSessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var notificationRedirected = false
    private var didFirstLaunch = false

    // Focus mode
    private var focusDuration = 25 * 60 * 1000L
    private var focusTimeRemaining = focusDuration
    private var isFocusRunning = false
    private var focusTab = 0
    private var mediaPlayer: MediaPlayer? = null
    private var dndActive = false

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    private val mediaPollRunnable = object : Runnable {
        override fun run() {
            refreshMediaController()
            handler.postDelayed(this, 2000)
        }
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            updateMusicInfo()
        }
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            isMusicPlaying = state?.state == PlaybackState.STATE_PLAYING
            updateMusicInfo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_launcher)

        initViews()
        setupClock()
        setupSwipe()
        setupAppList()
        setupMediaSession()
        setupFocusMode()
        setupTaskSwitcher()
        checkFirstLaunch()

        if (intent?.action == TaskSwitcherService.ACTION_SHOW_TASKS) {
            showTaskSwitcher()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == TaskSwitcherService.ACTION_SHOW_TASKS) {
            showTaskSwitcher()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(clockRunnable)
        handler.post(mediaPollRunnable)
        refreshMediaController()
        updateMusicInfo()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable)
        handler.removeCallbacks(mediaPollRunnable)
    }

    private fun initViews() {
        tvClock = findViewById(R.id.tvClock)
        tvDate = findViewById(R.id.tvDate)
        clockFlipper = findViewById(R.id.clockFlipper)
        musicPlayer = findViewById(R.id.musicPlayer)
        tvTrackTitle = findViewById(R.id.tvTrackTitle)
        tvTrackArtist = findViewById(R.id.tvTrackArtist)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        ivAlbumArt = findViewById(R.id.ivAlbumArt)
        appsList = findViewById(R.id.appsList)
        focusOverlay = findViewById(R.id.focusOverlay)
        btnCloseFocus = findViewById(R.id.btnCloseFocus)
        tvFocusCountdown = findViewById(R.id.tvFocusCountdown)
        btnPomodoro = findViewById(R.id.btnPomodoro)
        btnShortBreak = findViewById(R.id.btnShortBreak)
        btnLongBreak = findViewById(R.id.btnLongBreak)
        btnStartFocus = findViewById(R.id.btnStartFocus)
        btnResetFocus = findViewById(R.id.btnResetFocus)
        taskSwitcherOverlay = findViewById(R.id.taskSwitcherOverlay)
        taskList = findViewById(R.id.taskList)
    }

    private fun checkFirstLaunch() {
        val prefs = getSharedPreferences("launcher", Context.MODE_PRIVATE)
        if (prefs.getBoolean("first_launch_done", false)) return
        prefs.edit().putBoolean("first_launch_done", true).apply()
        didFirstLaunch = true
        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        } else {
            requestNotificationListenerAccess()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0) {
            requestNotificationListenerAccess()
        }
    }

    private fun requestNotificationListenerAccess() {
        val enabled = NotificationManager.getEnabledListenerPackages().contains(packageName)
        if (enabled) {
            requestAccessibilityService()
            return
        }
        permissionStep = 1
        settingsLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun requestAccessibilityService() {
        if (isAccessibilityServiceEnabled()) {
            requestUsageStats()
            return
        }
        permissionStep = 2
        settingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestUsageStats() {
        if (isUsageStatsGranted()) return
        permissionStep = 3
        settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val serviceStr = "$packageName/${TaskSwitcherService::class.java.name}"
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            return enabled.split(':').any { it.equals(serviceStr, ignoreCase = true) }
        } catch (_: Exception) {
            return false
        }
    }

    private fun isUsageStatsGranted(): Boolean {
        try {
            val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            return mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            return false
        }
    }

    private fun setupClock() {
        handler.post(clockRunnable)
        tvDate.setOnClickListener {
            showingHidden = !showingHidden
            refreshAppList()
            Toast.makeText(this, if (showingHidden) "Mostrando ocultas" else "Oculto ocultas", Toast.LENGTH_SHORT).show()
        }
    }

    private var touchStartX = 0f
    private var touchStartY = 0f

    private fun setupSwipe() {
        clockFlipper.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - touchStartX
                    val dy = event.y - touchStartY
                    if (kotlin.math.abs(dx) > 120 && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 2) {
                        if (dx < 0) {
                            clockFlipper.setInAnimation(this, R.anim.slide_in_right)
                            clockFlipper.setOutAnimation(this, R.anim.slide_out_left)
                            if (clockFlipper.displayedChild == 0 && hasMusic) {
                                clockFlipper.showNext()
                            } else if (clockFlipper.displayedChild == 1) {
                                clockFlipper.showNext()
                                clockFlipper.displayedChild = 0
                            }
                        } else {
                            clockFlipper.setInAnimation(this, R.anim.slide_in_left)
                            clockFlipper.setOutAnimation(this, R.anim.slide_out_right)
                            if (clockFlipper.displayedChild == 1) {
                                clockFlipper.showPrevious()
                            } else if (clockFlipper.displayedChild == 0 && hasMusic) {
                                clockFlipper.showPrevious()
                                clockFlipper.displayedChild = 1
                            }
                        }
                    } else if (kotlin.math.abs(dx) < 30 && kotlin.math.abs(dy) < 30 && clockFlipper.displayedChild == 0) {
                        showFocusMode()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun updateClock() {
        val sdf = SimpleDateFormat("h:mm a", Locale("es"))
        tvClock.text = sdf.format(Date())
        val dateSdf = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es"))
        tvDate.text = dateSdf.format(Date())
    }

    private fun getHiddenApps(): Set<String> {
        return getSharedPreferences("launcher", Context.MODE_PRIVATE)
            .getStringSet("hidden_apps", emptySet()) ?: emptySet()
    }

    private fun toggleHiddenApp(packageName: String) {
        val prefs = getSharedPreferences("launcher", Context.MODE_PRIVATE)
        val hidden = HashSet(getHiddenApps())
        if (hidden.contains(packageName)) hidden.remove(packageName) else hidden.add(packageName)
        prefs.edit().putStringSet("hidden_apps", hidden).apply()
        refreshAppList()
        val msg = if (hidden.contains(packageName)) "Oculta" else "Visible"
        Toast.makeText(this, "$msg: ${packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))}", Toast.LENGTH_SHORT).show()
    }

    private fun refreshAppList() {
        val hidden = getHiddenApps()
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }

        val filtered = if (showingHidden) apps else apps.filter { it.packageName !in hidden }

        val adapter = AppsAdapter(filtered, hidden) { appInfo ->
            val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (intent != null) {
                startActivity(intent)
                overridePendingTransition(R.anim.scale_in, android.R.anim.fade_out)
            }
        }

        appsList.adapter = adapter
    }

    private fun setupAppList() {
        appsList.layoutManager = LinearLayoutManager(this)
        refreshAppList()
    }

    private fun setController(controllers: List<MediaController>?) {
        val ctrl = controllers?.firstOrNull()
        if (ctrl != currentController) {
            currentController?.unregisterCallback(mediaCallback)
            currentController = ctrl
            currentController?.registerCallback(mediaCallback)
            updateMusicInfo()
        }
    }

    private fun refreshMediaController() {
        var found = false
        try {
            val msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = msm.getActiveSessions(null)
            if (controllers.isNotEmpty()) {
                setController(controllers)
                found = true
            }
        } catch (_: Exception) { }
        if (!found) {
            try {
                val msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
                val cpn = ComponentName(this, MediaNotificationListenerService::class.java)
                val controllers = msm.getActiveSessions(cpn)
                if (controllers.isNotEmpty()) {
                    setController(controllers)
                    found = true
                }
            } catch (_: Exception) { }
        }
        if (!found) {
            val notifCtrl = MediaNotificationListenerService.activeController
            if (notifCtrl != null && notifCtrl != currentController) {
                currentController?.unregisterCallback(mediaCallback)
                currentController = notifCtrl
                currentController?.registerCallback(mediaCallback)
                updateMusicInfo()
            }
        }
    }

    private fun registerSessionListener() {
        try {
            val msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                setController(controllers)
            }
            activeSessionsListener = listener
            msm.addOnActiveSessionsChangedListener(listener, null)
        } catch (_: Exception) { }
    }

    private fun setupMediaSession() {
        registerSessionListener()
        refreshMediaController()
        updateMusicInfo()

        btnPlayPause.setOnClickListener {
            val controller = currentController ?: return@setOnClickListener
            if (isMusicPlaying) controller.transportControls.pause()
            else controller.transportControls.play()
        }
        btnPrev.setOnClickListener { currentController?.transportControls?.skipToPrevious() }
        btnNext.setOnClickListener { currentController?.transportControls?.skipToNext() }
    }

    private fun updateMusicInfo() {
        val controller = currentController

        if (controller != null) {
            val metadata = controller.metadata
            val state = controller.playbackState
            if (metadata != null || state != null) {
                hasMusic = true
                tvTrackTitle.text = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Sin informaci\u00f3n"
                tvTrackArtist.text = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                isMusicPlaying = state?.state == PlaybackState.STATE_PLAYING
                btnPlayPause.setImageResource(if (isMusicPlaying) R.drawable.ic_pause else R.drawable.ic_play)

                val art = metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                if (art != null) {
                    ivAlbumArt.setImageBitmap(art)
                    ivAlbumArt.visibility = View.VISIBLE
                } else {
                    ivAlbumArt.visibility = View.GONE
                }
                return
            }
        }

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        if (am.isMusicActive) {
            hasMusic = true
            tvTrackTitle.text = "Reproduciendo..."
            tvTrackArtist.text = ""
            isMusicPlaying = true
            btnPlayPause.setImageResource(R.drawable.ic_pause)
            ivAlbumArt.visibility = View.GONE
        } else {
            hasMusic = false
            isMusicPlaying = false
            ivAlbumArt.visibility = View.GONE
            tvTrackTitle.text = ""
            tvTrackArtist.text = ""
            if (clockFlipper.displayedChild == 1) {
                clockFlipper.setInAnimation(this, R.anim.slide_in_left)
                clockFlipper.setOutAnimation(this, R.anim.slide_out_right)
                clockFlipper.showPrevious()
            }
        }
    }

    // ── Focus Mode ──

    private fun setupFocusMode() {
        btnPomodoro.setOnClickListener {
            focusTab = 0; focusDuration = 25 * 60 * 1000L; resetFocus()
            btnPomodoro.setBackgroundResource(R.drawable.bg_outline)
            btnShortBreak.background = null
            btnLongBreak.background = null
        }
        btnShortBreak.setOnClickListener {
            focusTab = 1; focusDuration = 5 * 60 * 1000L; resetFocus()
            btnShortBreak.setBackgroundResource(R.drawable.bg_outline)
            btnPomodoro.background = null
            btnLongBreak.background = null
        }
        btnLongBreak.setOnClickListener {
            focusTab = 2; focusDuration = 15 * 60 * 1000L; resetFocus()
            btnLongBreak.setBackgroundResource(R.drawable.bg_outline)
            btnPomodoro.background = null
            btnShortBreak.background = null
        }

        btnStartFocus.setOnClickListener { toggleFocus() }
        btnResetFocus.setOnClickListener { resetFocus() }
        btnCloseFocus.setOnClickListener { hideFocusMode() }
    }

    fun showFocusMode() {
        stopAlarm()
        focusOverlay.scaleX = 0f
        focusOverlay.scaleY = 0f
        focusOverlay.visibility = View.VISIBLE
        focusOverlay.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(2f))
            .start()
        resetFocus()
        updateFocusTabUI()
    }

    private fun hideFocusMode() {
        stopAlarm()
        isFocusRunning = false
        disableDnd()
        focusOverlay.animate().scaleX(0f).scaleY(0f).setDuration(200).withEndAction {
            focusOverlay.visibility = View.GONE
        }.start()
    }

    private fun toggleFocus() {
        if (isFocusRunning) {
            isFocusRunning = false
            btnStartFocus.text = getString(R.string.start)
            disableDnd()
        } else {
            isFocusRunning = true
            btnStartFocus.text = getString(R.string.pause)
            enableDnd()
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
                    btnStartFocus.text = getString(R.string.start)
                    disableDnd()
                    playAlarm()
                }
            }
        }
        focusHandler.post(runnable)
    }

    private fun resetFocus() {
        stopAlarm()
        focusTimeRemaining = focusDuration
        isFocusRunning = false
        btnStartFocus.text = getString(R.string.start)
        updateFocusUI()
    }

    private fun updateFocusUI() {
        val totalSecs = focusTimeRemaining / 1000
        val minutes = totalSecs / 60
        val seconds = totalSecs % 60
        tvFocusCountdown.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateFocusTabUI() {
        btnPomodoro.setBackgroundResource(if (focusTab == 0) R.drawable.bg_outline else 0)
        btnShortBreak.setBackgroundResource(if (focusTab == 1) R.drawable.bg_outline else 0)
        btnLongBreak.setBackgroundResource(if (focusTab == 2) R.drawable.bg_outline else 0)
    }

    private fun enableDnd() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            dndActive = true
        } else {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private fun disableDnd() {
        if (dndActive) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            dndActive = false
        }
    }

    private fun playAlarm() {
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@LauncherActivity, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) { }
    }

    private fun stopAlarm() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    // ── Task Switcher ──

    private fun setupTaskSwitcher() {
        taskSwitcherOverlay.setOnClickListener { hideTaskSwitcher() }
        taskList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun getRecentApps(): List<android.content.pm.ApplicationInfo> {
        val recent = mutableListOf<android.content.pm.ApplicationInfo>()
        try {
            val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val cal = Calendar.getInstance()
            cal.add(Calendar.HOUR_OF_DAY, -24)
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
            val sorted = stats.filter { it.lastTimeUsed > 0 }
                .sortedByDescending { it.lastTimeUsed }
                .map { it.packageName }
                .distinct()
                .filter { it != packageName }
            for (pkg in sorted) {
                try {
                    recent.add(packageManager.getApplicationInfo(pkg, 0))
                    if (recent.size >= 8) break
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return recent
    }

    private fun showTaskSwitcher() {
        val recent = getRecentApps()
        if (recent.isEmpty()) {
            Toast.makeText(this, "Sin apps recientes", Toast.LENGTH_SHORT).show()
            return
        }
        val adapter = RecentAppsAdapter(recent) { info ->
            hideTaskSwitcher()
            val intent = packageManager.getLaunchIntentForPackage(info.packageName)
            if (intent != null) startActivity(intent)
        }
        taskList.adapter = adapter
        taskSwitcherOverlay.visibility = View.VISIBLE
        taskSwitcherOverlay.bringToFront()
    }

    private fun hideTaskSwitcher() {
        taskSwitcherOverlay.visibility = View.GONE
    }

    class RecentAppsAdapter(
        private val apps: List<android.content.pm.ApplicationInfo>,
        private val onItemClick: (android.content.pm.ApplicationInfo) -> Unit
    ) : RecyclerView.Adapter<RecentAppsAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val card = LinearLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(200, RecyclerView.LayoutParams.WRAP_CONTENT)
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8, 16, 8, 16)
                setBackgroundColor(0x33FFFFFF.toInt())
            }
            val icon = ImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val name = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 11f
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            card.addView(icon)
            card.addView(name)
            return ViewHolder(card, icon, name)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            val pm = NeoLauncherApp.instance.packageManager
            holder.icon.setImageDrawable(pm.getApplicationIcon(app))
            holder.name.text = pm.getApplicationLabel(app)
            holder.itemView.setOnClickListener { onItemClick(app) }
        }

        override fun getItemCount() = apps.size

        class ViewHolder(itemView: View, val icon: ImageView, val name: TextView) : RecyclerView.ViewHolder(itemView)
    }

    override fun onBackPressed() {
        if (taskSwitcherOverlay.visibility == View.VISIBLE) {
            hideTaskSwitcher()
            return
        }
        if (focusOverlay.visibility == View.VISIBLE) {
            hideFocusMode()
            return
        }
        if (clockFlipper.displayedChild == 1) {
            clockFlipper.setInAnimation(this, R.anim.slide_in_left)
            clockFlipper.setOutAnimation(this, R.anim.slide_out_right)
            clockFlipper.showPrevious()
            return
        }
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        currentController?.unregisterCallback(mediaCallback)
        val listener = activeSessionsListener
        if (listener != null) {
            try {
                val msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
                msm.removeOnActiveSessionsChangedListener(listener)
            } catch (_: Exception) { }
        }
        stopAlarm()
        disableDnd()
    }

    class AppsAdapter(
        private val apps: List<android.content.pm.ApplicationInfo>,
        private val hidden: Set<String>,
        private val onItemClick: (android.content.pm.ApplicationInfo) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val container = LinearLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12, 0, 12)
            }
            val nameTv = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 18f
            }
            val sourceTv = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setTextColor(0x66FFFFFF.toInt())
                textSize = 12f
            }
            container.addView(nameTv)
            container.addView(sourceTv)
            return ViewHolder(container, nameTv, sourceTv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            val pm = NeoLauncherApp.instance.packageManager
            holder.nameText.text = pm.getApplicationLabel(app)
            holder.sourceText.text = app.packageName
            holder.itemView.setOnClickListener { onItemClick(app) }
            holder.itemView.setOnLongClickListener {
                val activity = holder.itemView.context as LauncherActivity
                activity.toggleHiddenApp(app.packageName)
                true
            }
            if (app.packageName in hidden) {
                holder.nameText.alpha = 0.4f
                holder.sourceText.alpha = 0.4f
            } else {
                holder.nameText.alpha = 1f
                holder.sourceText.alpha = 1f
            }
        }

        override fun getItemCount() = apps.size

        class ViewHolder(
            itemView: View,
            val nameText: TextView,
            val sourceText: TextView
        ) : RecyclerView.ViewHolder(itemView)
    }
}
