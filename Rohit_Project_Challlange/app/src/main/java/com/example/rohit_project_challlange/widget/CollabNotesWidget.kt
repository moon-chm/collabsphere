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
import com.example.rohit_project_challlange.model.notes.NotesEntity

// ─────────────────────────────────────────────────────────────────
// CollabNotesWidget — shows up to 5 recent notes on the home screen
// ─────────────────────────────────────────────────────────────────

private val KEY_DESTINATION = ActionParameters.Key<String>("widget_destination")

class CollabNotesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val userId = WidgetDataProvider.getSavedUserId(context)
        val notes = WidgetDataProvider.getRecentNotes(context, userId, limit = 5)

        provideContent {
            NotesWidgetContent(notes = notes, isLoggedIn = userId != -1)
        }
    }
}

class CollabNotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CollabNotesWidget()
}

// ── Colors ───────────────────────────────────────────────────────
private val WarmIvory  = Color(0xFFFAF8F5)
private val WarmBrown  = Color(0xFF1F1A17)
private val MutedBrown = Color(0xFF70625E)
private val Terracotta = Color(0xFFBA5330)
private val ShadowCard = Color(0xFFEDE8DF)
private val NoteAccent = Color(0xFF4A6FA5)

@Composable
private fun NotesWidgetContent(
    notes: List<NotesEntity>,
    isLoggedIn: Boolean
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WarmIvory)
            .clickable(actionStartActivity<MainActivity>(
                actionParametersOf(KEY_DESTINATION to "notes")
            ))
            .padding(0.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // ── Header ─────────────────────────────────────────
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📝  Notes",
                    style = TextStyle(
                        color = ColorProvider(Terracotta),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.width(8.dp))
                if (notes.isNotEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .background(ShadowCard)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${notes.size}",
                            style = TextStyle(
                                color = ColorProvider(Terracotta),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            // ── Content ────────────────────────────────────────
            if (!isLoggedIn) {
                Text(
                    text = "Open CollabSphere to sign in",
                    style = TextStyle(color = ColorProvider(MutedBrown), fontSize = 12.sp)
                )
            } else if (notes.isEmpty()) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = "No notes yet",
                    style = TextStyle(color = ColorProvider(MutedBrown), fontSize = 13.sp)
                )
                Text(
                    text = "Tap to create your first note.",
                    style = TextStyle(color = ColorProvider(MutedBrown), fontSize = 11.sp)
                )
            } else {
                notes.forEach { note ->
                    NoteRow(note = note)
                    Spacer(GlanceModifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: NotesEntity) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(14.dp)
                .background(NoteAccent)
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = note.notesName,
            style = TextStyle(
                color = ColorProvider(WarmBrown),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}
