package com.collabsphere.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.collabsphere.app.MainActivity
import com.collabsphere.app.R
import com.collabsphere.app.model.task.TaskEntity
import com.collabsphere.app.model.task.TaskStatus

// ─────────────────────────────────────────────────────────────────
// CollabTaskWidget
// Home screen widget — up to 5 active tasks.
// Surface: skeuomorphic raised warm parchment card.
// SizeMode.Exact: safe on all OEM launchers (Samsung, MIUI, ColorOS, Pixel).
// ─────────────────────────────────────────────────────────────────

private val KEY_DESTINATION = ActionParameters.Key<String>("widget_destination")

class CollabTaskWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val userId = WidgetDataProvider.getSavedUserId(context)
        val tasks  = WidgetDataProvider.getActiveTasks(context, userId, limit = 5)
        provideContent {
            TaskWidgetContent(tasks = tasks, isLoggedIn = userId != -1)
        }
    }
}

class CollabTaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CollabTaskWidget()
}

// ── Design tokens ────────────────────────────────────────────────
private val Ink        = Color(0xFF1F1A17)   // Deep warm charcoal
private val Muted      = Color(0xFF8A7B74)   // Warm mid-tone muted
private val Coral      = Color(0xFFBA5330)   // Brand coral-terracotta
private val Divider    = Color(0xFFDDD5C8)   // Warm neutral divider
private val AmberFill  = Color(0xFFF5A623)   // To-do status
private val IndigoFill = Color(0xFF5B6ECD)   // In-progress status
private val MintFill   = Color(0xFF4CAF82)   // Done status

@Composable
private fun TaskWidgetContent(
    tasks: List<TaskEntity>,
    isLoggedIn: Boolean
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_card_bg))
            .clickable(
                actionStartActivity<MainActivity>(
                    actionParametersOf(KEY_DESTINATION to "tasks")
                )
            )
            .padding(0.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {

            // ── Header ─────────────────────────────────────────
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vector icon replacing emoji
                Image(
                    provider           = ImageProvider(R.drawable.ic_widget_tasks),
                    contentDescription = "Tasks",
                    modifier           = GlanceModifier.size(18.dp)
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text  = "Tasks",
                    style = TextStyle(
                        color      = ColorProvider(Coral),
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.defaultWeight())
                // Count badge
                if (tasks.isNotEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.widget_pill_bg))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = "${tasks.size}",
                            style = TextStyle(
                                color      = ColorProvider(Coral),
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(GlanceModifier.height(2.dp))

            // Divider line under header
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ColorProvider(Divider))
            ) {}

            Spacer(GlanceModifier.height(10.dp))

            // ── Content ────────────────────────────────────────
            when {
                !isLoggedIn -> {
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text  = "Not signed in",
                        style = TextStyle(
                            color      = ColorProvider(Ink),
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    Text(
                        text  = "Open CollabSphere to continue.",
                        style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp)
                    )
                }
                tasks.isEmpty() -> {
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text  = "All caught up",
                        style = TextStyle(
                            color      = ColorProvider(Ink),
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    Text(
                        text  = "No active tasks at the moment.",
                        style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp)
                    )
                }
                else -> {
                    tasks.forEachIndexed { index, task ->
                        TaskRow(task = task)
                        if (index < tasks.lastIndex) {
                            Spacer(GlanceModifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity) {
    val dotDrawable = when (task.status) {
        TaskStatus.TO_DO       -> R.drawable.widget_dot_amber
        TaskStatus.IN_PROGRESS -> R.drawable.widget_dot_indigo
        TaskStatus.DONE        -> R.drawable.widget_dot_mint
    }
    val statusLabel = when (task.status) {
        TaskStatus.TO_DO       -> "To do"
        TaskStatus.IN_PROGRESS -> "In progress"
        TaskStatus.DONE        -> "Done"
    }

    Row(
        modifier          = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colored status indicator dot
        Image(
            provider           = ImageProvider(dotDrawable),
            contentDescription = statusLabel,
            modifier           = GlanceModifier.size(8.dp)
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text  = task.taskName,
                style = TextStyle(
                    color      = ColorProvider(Ink),
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            if (task.taskDescription.isNotBlank()) {
                Spacer(GlanceModifier.height(1.dp))
                Text(
                    text  = task.taskDescription,
                    style = TextStyle(
                        color    = ColorProvider(Muted),
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
