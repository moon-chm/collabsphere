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

// ─────────────────────────────────────────────────────────────────
// CollabQuickActionsWidget — 4×1 horizontal action bar
//
// Four independently-tappable raised skeuomorphic squircle buttons.
// Each shows a proper vector icon + label text.
// No emoji — professional icon-first design.
//
// SizeMode.Exact: safe on all API 24+ OEM launchers.
// ─────────────────────────────────────────────────────────────────

private val KEY_DESTINATION = ActionParameters.Key<String>("widget_destination")

class CollabQuickActionsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { QuickActionsContent() }
    }
}

class CollabQuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CollabQuickActionsWidget()
}

// ── Design tokens ────────────────────────────────────────────────
private val Ink   = Color(0xFF1F1A17)
private val Muted = Color(0xFF8A7B74)
private val Coral = Color(0xFFBA5330)

// Action definitions — no emoji, each has a vector drawable icon
private data class QuickAction(
    val iconRes   : Int,
    val label     : String,
    val destination: String
)

private val quickActions = listOf(
    QuickAction(R.drawable.ic_widget_tasks,      "Tasks",  "tasks"),
    QuickAction(R.drawable.ic_widget_notes,      "Notes",  "notes"),
    QuickAction(R.drawable.ic_widget_chat,       "Chat",   "dm"),
    QuickAction(R.drawable.ic_widget_files,      "Files",  "files")
)

@Composable
private fun QuickActionsContent() {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_card_bg))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        quickActions.forEachIndexed { index, action ->
            ActionButton(
                action   = action,
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .defaultWeight()
            )
            if (index < quickActions.lastIndex) {
                Spacer(GlanceModifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun ActionButton(action: QuickAction, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier
            .background(ImageProvider(R.drawable.widget_action_btn_bg))
            .clickable(
                actionStartActivity<MainActivity>(
                    actionParametersOf(KEY_DESTINATION to action.destination)
                )
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = GlanceModifier.fillMaxWidth(),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Vector icon — clean and professional
            Image(
                provider           = ImageProvider(action.iconRes),
                contentDescription = action.label,
                modifier           = GlanceModifier.size(22.dp)
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text  = action.label,
                style = TextStyle(
                    color      = ColorProvider(Ink),
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}
