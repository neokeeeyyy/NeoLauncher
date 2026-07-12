package com.neolauncher.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.neolauncher.R

class TaskSwitcherActivity : AppCompatActivity() {

    private lateinit var tasksContainer: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var clearAllButton: TextView
    private var recentTasks: MutableList<TaskInfo> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_switcher)

        tasksContainer = findViewById(R.id.tasksContainer)
        emptyView = findViewById(R.id.emptyView)
        clearAllButton = findViewById(R.id.clearAllButton)

        tasksContainer.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        loadRecentTasks()

        clearAllButton.setOnClickListener { clearAllTasks() }
    }

    private fun loadRecentTasks() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val recentTasks = activityManager.getRecentTasks(20, ActivityManager.RECENT_WITH_EXCLUDED)

        this.recentTasks.clear()

        recentTasks.forEachIndexed { index, task ->
            val intent = task.baseIntent
            val componentName = intent.component ?: return@forEachIndexed
            val packageName = componentName.packageName

            if (packageName == this.packageName) return@forEachIndexed

            val appInfo = try {
                packageManager.getApplicationInfo(packageName, 0)
            } catch (e: Exception) {
                return@forEachIndexed
            }

            val taskInfo = TaskInfo(
                packageName = packageName,
                label = packageManager.getApplicationLabel(appInfo).toString(),
                icon = packageManager.getApplicationIcon(appInfo),
                taskId = task.id,
                taskAffinity = task.taskAffinity ?: ""
            )

            if (this.recentTasks.none { it.packageName == packageName }) {
                this.recentTasks.add(taskInfo)
            }
        }

        if (this.recentTasks.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            tasksContainer.visibility = View.GONE
            clearAllButton.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            tasksContainer.visibility = View.VISIBLE
            clearAllButton.visibility = View.VISIBLE

            val adapter = TaskAdapter(
                this.recentTasks,
                onTaskClick = { task -> openTask(task) },
                onTaskClose = { task, position -> closeTask(task, position) }
            )
            tasksContainer.adapter = adapter
        }
    }

    private fun openTask(task: TaskInfo) {
        val intent = packageManager.getLaunchIntentForPackage(task.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            overridePendingTransition(R.anim.scale_in, android.R.anim.fade_out)
        }
        finish()
    }

    private fun closeTask(task: TaskInfo, position: Int) {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.removeTask(task.taskId)

        recentTasks.removeAt(position)
        tasksContainer.adapter?.notifyItemRemoved(position)
        tasksContainer.adapter?.notifyItemRangeChanged(position, recentTasks.size - position)

        if (recentTasks.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            tasksContainer.visibility = View.GONE
            clearAllButton.visibility = View.GONE
        }
    }

    private fun clearAllTasks() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        recentTasks.forEach { task ->
            activityManager.removeTask(task.taskId)
        }
        recentTasks.clear()
        tasksContainer.adapter?.notifyDataSetChanged()
        emptyView.visibility = View.VISIBLE
        tasksContainer.visibility = View.GONE
        clearAllButton.visibility = View.GONE
    }

    override fun onBackPressed() {
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    data class TaskInfo(
        val packageName: String,
        val label: String,
        val icon: Drawable,
        val taskId: Int,
        val taskAffinity: String
    )

    class TaskAdapter(
        private val tasks: List<TaskInfo>,
        private val onTaskClick: (TaskInfo) -> Unit,
        private val onTaskClose: (TaskInfo, Int) -> Unit
    ) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): TaskViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_task_card, parent, false)
            return TaskViewHolder(view)
        }

        override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
            val task = tasks[position]
            holder.bind(task, position)
        }

        override fun getItemCount() = tasks.size

        inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val taskIcon: ImageView = itemView.findViewById(R.id.taskIcon)
            private val taskLabel: TextView = itemView.findViewById(R.id.taskLabel)
            private val taskClose: ImageView = itemView.findViewById(R.id.taskClose)
            private val taskCard: View = itemView.findViewById(R.id.taskCard)

            fun bind(task: TaskInfo, position: Int) {
                taskIcon.setImageDrawable(task.icon)
                taskLabel.text = task.label

                taskCard.setOnClickListener { onTaskClick(task) }
                taskClose.setOnClickListener { onTaskClose(task, adapterPosition) }

                // Entry animation
                itemView.alpha = 0f
                itemView.translationX = 100f
                itemView.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(300)
                    .setStartDelay((position * 50).toLong())
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }
}
