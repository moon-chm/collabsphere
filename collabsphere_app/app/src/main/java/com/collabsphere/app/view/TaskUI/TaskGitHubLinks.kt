package com.collabsphere.app.view.TaskUI

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.viewmodel.GitHubTaskLink
import com.collabsphere.app.viewmodel.GitHubViewModel

fun taskReferenceLabel(taskId: Int): String? = if (taskId > 0) "T-$taskId" else null

@Composable
fun TaskGitHubLinksSection(
    workspaceId: Int,
    taskId: Int,
    gitHubViewModel: GitHubViewModel
) {
    val reference = taskReferenceLabel(taskId) ?: return
    val context = LocalContext.current
    val links by produceState<List<GitHubTaskLink>?>(initialValue = null, workspaceId, taskId) {
        value = gitHubViewModel.loadTaskLinks(workspaceId, taskId)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "GitHub",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF1F1A17)
        )
        Text(
            text = "Mention $reference in a commit or PR to link it. \"fixes $reference\" marks it done when merged.",
            fontSize = 12.sp,
            color = Color(0xFF6E635C)
        )
        links?.forEach { link ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (link.kind == "pr") "PR" else link.ref.take(7),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6F42C1)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = link.title,
                    fontSize = 13.sp,
                    color = Color(0xFF1F1A17),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
