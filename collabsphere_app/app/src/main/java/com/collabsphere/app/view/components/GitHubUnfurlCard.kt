package com.collabsphere.app.view.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.viewmodel.GitHubPreviewItem
import com.collabsphere.app.viewmodel.GitHubActionClientState
import com.collabsphere.app.ui.theme.*

@Composable
fun GitHubUnfurlCard(
    preview: GitHubPreviewItem,
    actionState: GitHubActionClientState? = null,
    actionMessage: String? = null,
    onActionClick: ((String, String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkThemeWrapper() // We usually handle this with MaterialTheme
    
    var showComposer by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }

    val cardColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
    val contentColor = if (isDark) Color.White else Color.Black

    val icon = when (preview.type) {
        "PULL_REQUEST" -> Icons.Default.CallSplit
        "ISSUE" -> Icons.Default.Info
        "COMMIT" -> Icons.Default.Commit
        else -> Icons.Default.Source
    }

    val statusColor = when (preview.state?.lowercase()) {
        "open" -> Color(0xFF238636)
        "closed" -> Color(0xFF8957E5)
        "merged" -> Color(0xFF8957E5)
        else -> Color.Gray
    }

    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardColor)
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(preview.url))
                context.startActivity(intent)
            }
            .padding(12.dp)
    ) {
        Column {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = preview.type,
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = preview.repoFullName,
                    color = contentColor.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            
            // Title
            Text(
                text = preview.title,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))
            
            // Status and Meta
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (preview.state != null) {
                    val displayState = if (preview.type == "PULL_REQUEST" && preview.merged == true) "Merged" else preview.state.replaceFirstChar { it.uppercase() }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = displayState,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                if (preview.ciStatus != null) {
                    val (ciColor, ciText) = when (preview.ciStatus) {
                        "success" -> Color(0xFF238636) to "✅ CI Passed"
                        "failure" -> Color(0xFFD32F2F) to "❌ CI Failed"
                        "pending" -> Color(0xFFE3B341) to "⏳ CI Pending"
                        else -> Color.Gray to "CI Unknown"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ciColor.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = ciText,
                            color = ciColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                val idText = preview.number?.let { "#$it" } ?: preview.shortSha
                if (idText != null) {
                    Text(
                        text = idText,
                        color = contentColor.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                if (preview.author != null) {
                    Text(
                        text = "by ${preview.author}",
                        color = contentColor.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Action Banner
            if (actionState != null && actionState != GitHubActionClientState.Idle && actionState != GitHubActionClientState.Succeeded) {
                val isExecuting = actionState == GitHubActionClientState.Executing
                val bannerColor = if (isExecuting) Color(0xFF0256D4) else Color(0xFFD32F2F)
                val text = if (isExecuting) "Executing..." else actionMessage ?: "Action failed"
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(bannerColor.copy(alpha = 0.1f)).padding(8.dp)) {
                    Text(text, color = bannerColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Action Buttons
            if (onActionClick != null) {
                val isExecuting = actionState == GitHubActionClientState.Executing
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (preview.type == "ISSUE") {
                        if (preview.state == "open") {
                            ActionButton("Close", isExecuting) { onActionClick("CLOSE_ISSUE", null) }
                        } else if (preview.state == "closed") {
                            ActionButton("Reopen", isExecuting) { onActionClick("REOPEN_ISSUE", null) }
                        }
                        ActionButton("Comment", isExecuting, Color.DarkGray) { showComposer = !showComposer }
                    } else if (preview.type == "PULL_REQUEST") {
                        if (preview.state == "open") {
                            ActionButton("Approve", isExecuting, Color(0xFF238636)) { onActionClick("APPROVE_PR", null) }
                            ActionButton("Merge", isExecuting, Color(0xFF8957E5)) { onActionClick("MERGE_PR", null) }
                            ActionButton("Close", isExecuting) { onActionClick("CLOSE_PR", null) }
                        } else if (preview.state == "closed" && preview.merged != true) {
                            ActionButton("Reopen", isExecuting) { onActionClick("REOPEN_PR", null) }
                        }
                        ActionButton("Comment", isExecuting, Color.DarkGray) { showComposer = !showComposer }
                    }
                }
                
                if (showComposer) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Leave a comment...", fontSize = 12.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { 
                            showComposer = false
                            commentText = ""
                        }) {
                            Text("Cancel", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                val action = if (preview.type == "ISSUE") "COMMENT_ISSUE" else "COMMENT_PR"
                                onActionClick(action, commentText)
                                showComposer = false
                                commentText = ""
                            },
                            enabled = commentText.isNotBlank() && !isExecuting,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(if (isExecuting) "Posting..." else "Comment", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    disabled: Boolean,
    color: Color = Color.Gray,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !disabled,
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = color.copy(alpha=0.5f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun isSystemInDarkThemeWrapper(): Boolean {
    return androidx.compose.foundation.isSystemInDarkTheme()
}