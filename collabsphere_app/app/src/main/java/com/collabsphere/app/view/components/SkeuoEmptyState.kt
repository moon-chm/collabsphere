package com.collabsphere.app.view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.collabsphere.app.ui.theme.CollabSphereTypography
import com.collabsphere.app.ui.theme.CoralStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.SkeuoDebossedIconWell
import androidx.compose.foundation.clickable
import com.collabsphere.app.ui.theme.skeuoBounceClick
import com.collabsphere.app.ui.theme.skeuoRaised
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.collabsphere.app.ui.theme.SkeuoTokens
import com.collabsphere.app.ui.theme.SurfaceRaised
import com.collabsphere.app.ui.theme.skeuoFloatingCard

@Composable
fun SkeuoEmptyState(
    icon: ImageVector? = null,
    title: String,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp)
            .skeuoFloatingCard(cornerRadius = SkeuoTokens.RadiusLarge, surfaceColor = SurfaceRaised)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            SkeuoDebossedIconWell(wellSize = 64.dp) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text(
            text = title,
            style = CollabSphereTypography.headlineMedium,
            color = Ink,
            textAlign = TextAlign.Center
        )

        if (description != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = CollabSphereTypography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center
            )
        }

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp)
                    .skeuoBounceClick(onClick = onAction)
                    .skeuoRaised(cornerRadius = SkeuoTokens.RadiusMedium)
                    .clip(RoundedCornerShape(SkeuoTokens.RadiusMedium)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    style = CollabSphereTypography.labelLarge,
                    color = Ink
                )
            }
        }
    }
}
