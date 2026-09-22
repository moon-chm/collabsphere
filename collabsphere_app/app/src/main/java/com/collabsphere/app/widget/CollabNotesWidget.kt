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
import com.collabsphere.app.model.notes.NotesEntity

// ─────────────────────────────────────────────────────────────────
// CollabNotesWidget
// Home screen widget — up to 5 recent notes.
// Surface: skeuomorphic raised warm parchment card.
// SizeMode.Exact: safe on all OEM launchers (Samsung, MIUI, ColorOS, Pixel).
// ─────────────────────────────────────────────────────────────────

private val KEY_DESTINATION = ActionParameters.Key<String>("widget_destination")

class CollabNotesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val userId = WidgetDataProvider.getSavedUserId(context)
        val notes  = WidgetDataProvider.getRecentNotes(context, userId, limit = 5)
        provideContent {
            NotesWidgetContent(notes = notes, isLoggedIn = userId != -1)
        }
    }
}

class CollabNotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CollabNotesWidget()
}

// ── Design tokens ────────────────────────────────────────────────
private val Ink     = Color(0xFF1F1A17)
private val Muted   = Color(0xFF8A7B74)
private val Coral   = Color(0xFFBA5330)
private val Divider = Color(0xFFDDD5C8)
private val Stripe  = Color(0xFF5B6ECD)   // Indigo accent left stripe

@Composable
private fun NotesWidgetContent(
    notes: List<NotesEntity>,
    isLoggedIn: Boolean
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_card_bg))
            .clickable(
                actionStartActivity<MainActivity>(
                    actionParametersOf(KEY_DESTINATION to "notes")
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
                    provider           = ImageProvider(R.drawable.ic_widget_notes),
                    contentDescription = "Notes",
                    modifier           = GlanceModifier.size(18.dp)
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text  = "Notes",
                    style = TextStyle(
                        color      = ColorProvider(Coral),
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.defaultWeight())
                if (notes.isNotEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.widget_pill_bg))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = "${notes.size}",
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
                notes.isEmpty() -> {
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text  = "No notes yet",
                        style = TextStyle(
                            color      = ColorProvider(Ink),
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    Text(
                        text  = "Tap to create your first note.",
                        style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp)
                    )
                }
                else -> {
                    notes.forEachIndexed { index, note ->
                        NoteRow(note = note)
                        if (index < notes.lastIndex) {
                            Spacer(GlanceModifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: NotesEntity) {
    Row(
        modifier          = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indigo left accent stripe — tactile depth indicator
        Image(
            provider           = ImageProvider(R.drawable.widget_note_stripe),
            contentDescription = null,
            modifier           = GlanceModifier.width(3.dp).height(32.dp)
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text  = note.notesName,
                style = TextStyle(
                    color      = ColorProvider(Ink),
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            if (note.description.isNotBlank()) {
                Spacer(GlanceModifier.height(1.dp))
                Text(
                    text  = note.description,
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
