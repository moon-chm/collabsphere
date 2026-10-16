package com.collabsphere.app.remote.task

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.collabsphere.app.MyApplication
import com.collabsphere.app.model.task.TaskDao
import com.collabsphere.app.model.task.TaskRepo
import com.collabsphere.app.model.task.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TaskNotificationReceiver : BroadcastReceiver(), KoinComponent {

    private val taskDao: TaskDao by inject()
    private val taskRepo: TaskRepo by inject()

    companion object {
        const val ACTION_MARK_DONE = "com.collabsphere.app.ACTION_TASK_MARK_DONE"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_DONE) return
        val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (taskId <= 0) return
        val appContext = context.applicationContext
        val pending = goAsync()
        MyApplication.appScope.launch {
            try {
                val task = taskDao.getTaskById(taskId)
                val message = when {
                    task == null -> "Open CollabSphere to update this task"
                    task.status == TaskStatus.DONE -> "That task is already done"
                    else -> taskRepo.updateTask(task.copy(status = TaskStatus.DONE)).fold(
                        onSuccess = { "Marked “${task.taskName}” as done" },
                        onFailure = { "Couldn't update the task" }
                    )
                }
                if (task != null && notificationId != -1) NotificationManagerCompat.from(appContext).cancel(notificationId)
                withContext(Dispatchers.Main) { Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TaskNotificationReceiver", "Mark done failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
