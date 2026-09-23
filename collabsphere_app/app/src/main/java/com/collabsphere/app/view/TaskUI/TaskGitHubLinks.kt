package com.collabsphere.app.view.TaskUI

import com.collabsphere.app.view.openInBrowser
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
import android.widget.Toast
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    var reloadKey by remember { mutableIntStateOf(0) }
    var isCreatingIssue by remember { mutableStateOf(false) }
    val links by produceState<List<GitHubTaskLink>?>(initialValue = null, workspaceId, taskId, reloadKey) {
        value = gitHubViewModel.loadTaskLinks(workspaceId, taskId)
    }
    val hasIssue = links?.any { it.kind == "issue" } == true

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
                    .clickable { openInBrowser(context, link.url) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (link.kind) {
                        "pr" -> "PR"
                        "issue" -> "Issue"
                        else -> link.ref.take(7)
                    },
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
        if (links != null && !hasIssue) {
            TextButton(
                enabled = !isCreatingIssue,
                onClick = {
                    isCreatingIssue = true
                    scope.launch {
                        gitHubViewModel.createIssueForTask(workspaceId, taskId)
                            .onSuccess {
                                Toast.makeText(context, "Created GitHub issue ${it.title}", Toast.LENGTH_SHORT).show()
                                reloadKey++
                            }
                            .onFailure {
                                Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                            }
                        isCreatingIssue = false
                    }
                }
            ) {
                Text(if (isCreatingIssue) "Creating issue…" else "Create GitHub issue", color = Color(0xFF6F42C1))
            }
        }
    }
}
