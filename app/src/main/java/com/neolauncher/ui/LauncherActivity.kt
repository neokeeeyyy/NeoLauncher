package com.neolauncher.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.neolauncher.NeoLauncherApp
import com.neolauncher.R
import java.text.SimpleDateFormat
import java.util.*

class LauncherActivity : AppCompatActivity() {

    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private lateinit var musicPlayer: LinearLayout
    private lateinit var tvTrackTitle: TextView
    private lateinit var tvTrackArtist: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnPrev: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var appsList: RecyclerView
    private lateinit var focusOverlay: FrameLayout
    private lateinit var btnCloseFocus: ImageView
    private lateinit var tvFocusCountdown: TextView
    private lateinit var btnPomodoro: TextView
    private lateinit var btnShortBreak: TextView
    private lateinit var btnLongBreak: TextView
    private lateinit var btnStartFocus: TextView
    private lateinit var btnResetFocus: TextView
    private lateinit var btnOpenFocus: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var currentController: MediaController? = null
    private var isMusicPlaying = false
    private var clockSmall = false
    private var activeSessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    // Focus mode
    private var focusDuration = 25 * 60 * 1000L
    private var focusTimeRemaining = focusDuration
    private var isFocusRunning = false
    private var focusTab = 0
    private var mediaPlayer: MediaPlayer? = null

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
        setupAppList()
        setupMediaSession()
        setupFocusMode()
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
        musicPlayer = findViewById(R.id.musicPlayer)
        tvTrackTitle = findViewById(R.id.tvTrackTitle)
        tvTrackArtist = findViewById(R.id.tvTrackArtist)
        seekBar = findViewById(R.id.seekBar)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        appsList = findViewById(R.id.appsList)
        focusOverlay = findViewById(R.id.focusOverlay)
        btnCloseFocus = findViewById(R.id.btnCloseFocus)
        tvFocusCountdown = findViewById(R.id.tvFocusCountdown)
        btnPomodoro = findViewById(R.id.btnPomodoro)
        btnShortBreak = findViewById(R.id.btnShortBreak)
        btnLongBreak = findViewById(R.id.btnLongBreak)
        btnStartFocus = findViewById(R.id.btnStartFocus)
        btnResetFocus = findViewById(R.id.btnResetFocus)
        btnOpenFocus = findViewById(R.id.btnOpenFocus)
    }

    private fun setupClock() {
        handler.post(clockRunnable)
    }

    private fun updateClock() {
        val showSeconds = isMusicPlaying
        val format = if (showSeconds) "HH:mm:ss" else "HH:mm"
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        tvClock.text = sdf.format(Date())

        if (showSeconds != clockSmall) {
            clockSmall = showSeconds
            tvClock.animate().scaleX(if (showSeconds) 0.5f else 1f)
                .scaleY(if (showSeconds) 0.5f else 1f).setDuration(300).start()
        }

        val dateSdf = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es"))
        tvDate.text = dateSdf.format(Date())
    }

    private fun setupAppList() {
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }

        val adapter = AppsAdapter(apps) { appInfo ->
            val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (intent != null) {
                startActivity(intent)
                overridePendingTransition(R.anim.scale_in, android.R.anim.fade_out)
            }
        }

        appsList.layoutManager = LinearLayoutManager(this)
        appsList.adapter = adapter
    }

    private fun setController(controllers: List<MediaController>) {
        val ctrl = controllers.firstOrNull()
        if (ctrl != currentController) {
            currentController?.unregisterCallback(mediaCallback)
            currentController = ctrl
            currentController?.registerCallback(mediaCallback)
            updateMusicInfo()
        }
    }

    private fun refreshMediaController() {
        try {
            val msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cpn = ComponentName(this, MediaNotificationListenerService::class.java)
            var controllers = msm.getActiveSessions(cpn)
            if (controllers.isEmpty()) {
                controllers = msm.getActiveSessions(null)
            }
            setController(controllers)
        } catch (_: Exception) { }
    }

    private fun registerSessionListener() {
        try {
            val msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                setController(controllers)
            }
            msm.addOnActiveSessionsChangedListener(activeSessionsListener, null)
        } catch (_: Exception) { }
    }

    private fun setupMediaSession() {
        registerSessionListener()
        refreshMediaController()
        updateMusicInfo()

        btnPlayPause.setOnClickListener {
            val controller = currentController ?: return@setOnClickListener
            if (isMusicPlaying) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        }
        btnPrev.setOnClickListener { currentController?.transportControls?.skipToPrevious() }
        btnNext.setOnClickListener { currentController?.transportControls?.skipToNext() }
    }

    private fun updateMusicInfo() {
        val controller = currentController ?: run {
            musicPlayer.visibility = View.GONE
            isMusicPlaying = false
            return
        }
        val metadata = controller.metadata
        if (metadata != null) {
            musicPlayer.visibility = View.VISIBLE
            tvTrackTitle.text = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
            tvTrackArtist.text = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            val state = controller.playbackState
            isMusicPlaying = state?.state == PlaybackState.STATE_PLAYING
            val icon = if (isMusicPlaying) R.drawable.ic_pause else R.drawable.ic_play
            btnPlayPause.setImageResource(icon)
        } else {
            musicPlayer.visibility = View.GONE
            isMusicPlaying = false
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

        btnOpenFocus.setOnClickListener { showFocusMode() }
        btnStartFocus.setOnClickListener { toggleFocus() }
        btnResetFocus.setOnClickListener { resetFocus() }
        btnCloseFocus.setOnClickListener { hideFocusMode() }
    }

    fun showFocusMode() {
        stopAlarm()
        focusOverlay.visibility = View.VISIBLE
        resetFocus()
        updateFocusTabUI()
    }

    private fun hideFocusMode() {
        stopAlarm()
        isFocusRunning = false
        focusOverlay.visibility = View.GONE
    }

    private fun toggleFocus() {
        if (isFocusRunning) {
            isFocusRunning = false
            btnStartFocus.text = getString(R.string.start)
        } else {
            isFocusRunning = true
            btnStartFocus.text = getString(R.string.pause)
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

    override fun onBackPressed() {
        if (focusOverlay.visibility == View.VISIBLE) {
            hideFocusMode()
            return
        }
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        currentController?.unregisterCallback(mediaCallback)
        try {
            val msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            activeSessionsListener?.let { msm.removeOnActiveSessionsChangedListener(it) }
        } catch (_: Exception) { }
        stopAlarm()
    }

    class AppsAdapter(
        private val apps: List<android.content.pm.ApplicationInfo>,
        private val onItemClick: (android.content.pm.ApplicationInfo) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 20, 0, 20)
                setTextColor(parent.context.getColor(R.color.text_tertiary))
                textSize = 18f
            }
            return ViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.textView.text = NeoLauncherApp.instance.packageManager.getApplicationLabel(app)
            holder.itemView.setOnClickListener { onItemClick(app) }
        }

        override fun getItemCount() = apps.size

        class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    }
}
