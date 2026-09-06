package com.example.rohit_project_challlange.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.rohit_project_challlange.MainActivity

// ─────────────────────────────────────────────────────────────────
// CollabQuickActionsWidget — 4×1 horizontal action bar
// Four tap targets: Tasks | Notes | Chat | Files
// ─────────────────────────────────────────────────────────────────

private val KEY_DESTINATION = ActionParameters.Key<String>("widget_destination")

class CollabQuickActionsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            QuickActionsContent()
        }
    }
}

class CollabQuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CollabQuickActionsWidget()
}

// ── Colors ───────────────────────────────────────────────────────
private val WarmIvory   = Color(0xFFFAF8F5)
private val ShadowCard  = Color(0xFFEDE8DF)
private val MutedBrown  = Color(0xFF70625E)

// Quick action data
private data class QuickAction(
    val emoji: String,
    val label: String,
    val destination: String
)

private val quickActions = listOf(
    QuickAction("✅", "Tasks",  "tasks"),
    QuickAction("📝", "Notes",  "notes"),
    QuickAction("💬", "Chat",   "dm"),
    QuickAction("📂", "Files",  "files")
)

@Composable
private fun QuickActionsContent() {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WarmIvory)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        quickActions.forEachIndexed { index, action ->
            ActionPill(action = action, modifier = GlanceModifier.fillMaxHeight())
            if (index < quickActions.lastIndex) {
                Spacer(GlanceModifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun ActionPill(action: QuickAction, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier
            .width(72.dp)
            .background(ShadowCard)
            .clickable(actionStartActivity<MainActivity>(
                actionParametersOf(KEY_DESTINATION to action.destination)
            ))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = action.emoji,
                style = TextStyle(fontSize = 18.sp)
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = action.label,
                style = TextStyle(
                    color = ColorProvider(MutedBrown),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}
