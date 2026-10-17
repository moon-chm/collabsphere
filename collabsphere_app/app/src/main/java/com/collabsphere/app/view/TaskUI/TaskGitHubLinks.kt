package com.collabsphere.app.view.TaskUI

import com.collabsphere.app.view.openInBrowser
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.collabsphere.app.view.components.AppToast
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
            text = "GITHUB",
            style = com.collabsphere.app.ui.theme.PrintType.Tiny,
            color = com.collabsphere.app.ui.theme.PrintInk
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Mention $reference in a commit or PR to link it. \"fixes $reference\" marks it done when merged.",
            style = com.collabsphere.app.ui.theme.PrintType.Caption,
            color = com.collabsphere.app.ui.theme.Muted
        )
        links?.forEach { link ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openInBrowser(context, link.url) }
                    .heightIn(min = 44.dp)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.collabsphere.app.ui.theme.PrintChip(
                    text = when (link.kind) {
                        "pr" -> "PR"
                        "issue" -> "Issue"
                        else -> link.ref.take(7)
                    },
                    fill = when (link.kind) {
                        "pr" -> com.collabsphere.app.ui.theme.PrintSky
                        "issue" -> com.collabsphere.app.ui.theme.PrintLeaf
                        else -> com.collabsphere.app.ui.theme.PrintPaper
                    }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = link.title,
                    style = com.collabsphere.app.ui.theme.PrintType.Body.copy(fontSize = 14.sp),
                    color = com.collabsphere.app.ui.theme.PrintInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (links != null && !hasIssue) {
            Spacer(modifier = Modifier.height(8.dp))
            com.collabsphere.app.ui.theme.PrintButton(
                text = if (isCreatingIssue) "Creating issue…" else "Create GitHub issue",
                primary = false,
                enabled = !isCreatingIssue,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    isCreatingIssue = true
                    scope.launch {
                        gitHubViewModel.createIssueForTask(workspaceId, taskId)
                            .onSuccess {
                                AppToast.show("Created GitHub issue ${it.title}")
                                reloadKey++
                            }
                            .onFailure {
                                AppToast.error(it.message)
                            }
                        isCreatingIssue = false
                    }
                }
            )
        }
    }
}
