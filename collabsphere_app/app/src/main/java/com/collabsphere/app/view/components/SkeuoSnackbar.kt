package com.collabsphere.app.view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.collabsphere.app.ui.theme.CollabSphereTypography
import com.collabsphere.app.ui.theme.DestructiveStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.SkeuoTokens
import com.collabsphere.app.ui.theme.SurfaceRaised
import com.collabsphere.app.ui.theme.skeuoFloatingCard

import com.collabsphere.app.ui.theme.skeuoBounceClick

@Composable
fun SkeuoSnackbar(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .skeuoFloatingCard(cornerRadius = SkeuoTokens.RadiusMedium, surfaceColor = SurfaceRaised)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = message,
            style = CollabSphereTypography.bodyMedium,
            color = if (isError) DestructiveStart else Ink,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = actionLabel,
                style = CollabSphereTypography.labelLarge,
                color = Muted,
                modifier = Modifier.skeuoBounceClick(onClick = onAction)
            )
        }
    }
}
