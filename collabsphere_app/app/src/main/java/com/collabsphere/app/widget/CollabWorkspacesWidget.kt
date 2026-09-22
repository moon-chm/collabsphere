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
import com.collabsphere.app.model.workspace.WorkspaceEntity

// ─────────────────────────────────────────────────────────────────
// CollabWorkspacesWidget — 2×2 home screen widget
//
// Shows up to 3 active workspaces from Room DB.
// Each workspace row: debossed letter monogram well + name + owner.
// No emoji — vector icon header, clean typographic hierarchy.
//
// SizeMode.Exact: safe on all API 24+ OEM launchers.
// ─────────────────────────────────────────────────────────────────

private val KEY_DESTINATION = ActionParameters.Key<String>("widget_destination")

class CollabWorkspacesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val userId     = WidgetDataProvider.getSavedUserId(context)
        val workspaces = WidgetDataProvider.getActiveWorkspaces(context, userId, limit = 3)
        provideContent {
            WorkspacesWidgetContent(workspaces = workspaces, isLoggedIn = userId != -1)
        }
    }
}

class CollabWorkspacesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CollabWorkspacesWidget()
}

// ── Design tokens ────────────────────────────────────────────────
private val Ink        = Color(0xFF1F1A17)
private val Muted      = Color(0xFF8A7B74)
private val Coral      = Color(0xFFBA5330)
private val Divider    = Color(0xFFDDD5C8)
private val WellText   = Color(0xFFBA5330)   // Monogram letter color

@Composable
private fun WorkspacesWidgetContent(
    workspaces: List<WorkspaceEntity>,
    isLoggedIn: Boolean
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_card_bg))
            .clickable(
                actionStartActivity<MainActivity>(
                    actionParametersOf(KEY_DESTINATION to "workspaces")
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
                modifier          = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider           = ImageProvider(R.drawable.ic_widget_workspaces),
                    contentDescription = "Workspaces",
                    modifier           = GlanceModifier.size(18.dp)
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text  = "Workspaces",
                    style = TextStyle(
                        color      = ColorProvider(Coral),
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.defaultWeight())
                if (workspaces.isNotEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.widget_pill_bg))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = "${workspaces.size}",
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
                workspaces.isEmpty() -> {
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text  = "No workspaces",
                        style = TextStyle(
                            color      = ColorProvider(Ink),
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    Text(
                        text  = "Tap to create or join one.",
                        style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp)
                    )
                }
                else -> {
                    workspaces.forEachIndexed { index, workspace ->
                        WorkspaceRow(workspace = workspace)
                        if (index < workspaces.lastIndex) {
                            Spacer(GlanceModifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceRow(workspace: WorkspaceEntity) {
    // Derive single-letter monogram from workspace name
    val initial = workspace.workspaceName
        .trim()
        .firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        ?: "W"

    Row(
        modifier          = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Debossed monogram well — carved squircle socket showing initial letter
        Box(
            modifier = GlanceModifier
                .size(34.dp)
                .background(ImageProvider(R.drawable.widget_initial_well_bg)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = initial,
                style = TextStyle(
                    color      = ColorProvider(WellText),
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text  = workspace.workspaceName,
                style = TextStyle(
                    color      = ColorProvider(Ink),
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(1.dp))
            Text(
                text  = workspace.workspaceOwner,
                style = TextStyle(
                    color    = ColorProvider(Muted),
                    fontSize = 10.sp
                ),
                maxLines = 1
            )
        }
    }
}
