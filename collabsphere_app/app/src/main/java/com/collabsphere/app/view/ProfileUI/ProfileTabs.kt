package com.collabsphere.app.view.ProfileUI

import androidx.compose.foundation.border
import com.collabsphere.app.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.collabsphere.app.ui.theme.CoralStart
import com.collabsphere.app.ui.theme.Muted

/** The profile page's sections; only one is shown at a time so the page stays short. */
enum class ProfileTab(val label: String) {
    PROFILE("Profile"),
    CAREER("Career"),
    SETTINGS("Settings");

    companion object {
        /** Navigation argument value (e.g. "career") → tab; anything unknown opens the default tab. */
        fun fromArg(value: String?): ProfileTab =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PROFILE
    }
}

/** Segmented control under the profile header. */
@Composable
fun ProfileTabRow(selected: ProfileTab, onSelect: (ProfileTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PrintWhite)
            .border(1.5.dp, PrintInk, RoundedCornerShape(16.dp))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ProfileTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) PrintInk else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) PrintPaper else PrintInk
                )
            }
        }
    }
}
