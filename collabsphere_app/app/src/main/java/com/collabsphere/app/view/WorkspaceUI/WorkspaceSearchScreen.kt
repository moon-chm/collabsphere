package com.collabsphere.app.view.WorkspaceUI

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.dto.search.SearchDmHit
import com.collabsphere.app.dto.search.SearchFileHit
import com.collabsphere.app.dto.search.SearchMessageHit
import com.collabsphere.app.dto.search.SearchNoteHit
import com.collabsphere.app.dto.search.SearchTaskHit
import com.collabsphere.app.dto.search.WorkspaceSearchResponse
import com.collabsphere.app.ui.theme.CoralStart
import com.collabsphere.app.ui.theme.IndigoStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.skeuoFloatingCard
import com.collabsphere.app.ui.theme.skeuoInset
import com.collabsphere.app.viewmodel.SearchUiState
import com.collabsphere.app.viewmodel.WorkspaceSearchViewModel

@Composable
fun WorkspaceSearchScreen(
    viewModel: WorkspaceSearchViewModel,
    workspaceName: String,
    onBack: () -> Unit,
    onMessageClick: (SearchMessageHit) -> Unit,
    onDmClick: (SearchDmHit) -> Unit,
    onTaskClick: (SearchTaskHit) -> Unit,
    onNoteClick: (SearchNoteHit) -> Unit,
    onFileClick: (SearchFileHit) -> Unit
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
                BasicTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                    cursorBrush = SolidColor(CoralStart),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search $workspaceName",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = Muted,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.onQueryChange("") }
                    )
                }
            }
        }

        when (val current = state) {
            SearchUiState.Idle -> CenteredMessage(
                if (query.trim().isEmpty()) "Search messages, DMs, tasks, notes and files in this workspace."
                else "Type at least ${WorkspaceSearchViewModel.MIN_QUERY_LENGTH} characters."
            )
            SearchUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp, color = CoralStart)
            }
            is SearchUiState.Error -> Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(current.message, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
                TextButton(onClick = viewModel::retry) {
                    Text("Try again", color = CoralStart, fontWeight = FontWeight.Bold)
                }
            }
            is SearchUiState.Results -> if (current.response.isEmpty) {
                CenteredMessage("No results for \"${current.response.query}\"")
            } else {
                SearchResultsList(current.response, onMessageClick, onDmClick, onTaskClick, onNoteClick, onFileClick)
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SearchResultsList(
    response: WorkspaceSearchResponse,
    onMessageClick: (SearchMessageHit) -> Unit,
    onDmClick: (SearchDmHit) -> Unit,
    onTaskClick: (SearchTaskHit) -> Unit,
    onNoteClick: (SearchNoteHit) -> Unit,
    onFileClick: (SearchFileHit) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        section("Channel messages", "messages", response.messages, { it.id.toString() }) { hit ->
            ResultRow(Icons.Outlined.Tag, "#${hit.channelName} · ${hit.userName}", hit.content) { onMessageClick(hit) }
        }
        section("Direct messages", "dms", response.directMessages, { it.id.toString() }) { hit ->
            ResultRow(Icons.Outlined.ChatBubbleOutline, hit.partnerName, hit.content) { onDmClick(hit) }
        }
        section("Tasks", "tasks", response.tasks, { it.id.toString() }) { hit ->
            ResultRow(Icons.Outlined.CheckBox, hit.taskName, taskStatusLabel(hit.status)) { onTaskClick(hit) }
        }
        section("Notes", "notes", response.notes, { it.id.toString() }) { hit ->
            ResultRow(Icons.Outlined.Description, hit.notesName, hit.snippet) { onNoteClick(hit) }
        }
        section("Files", "files", response.files, { it.id.toString() }) { hit ->
            ResultRow(Icons.Outlined.Folder, hit.fileName, hit.mimeType) { onFileClick(hit) }
        }
    }
}

private fun <T> LazyListScope.section(
    title: String,
    keyPrefix: String,
    hits: List<T>,
    idOf: (T) -> String,
    row: @Composable (T) -> Unit
) {
    if (hits.isEmpty()) return
    item(key = "header_$keyPrefix") {
        Text(
            text = "$title (${hits.size})",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Muted,
            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
        )
    }
    items(hits, key = { "${keyPrefix}_${idOf(it)}" }) { row(it) }
}

@Composable
private fun ResultRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = IndigoStart, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun taskStatusLabel(status: String): String = when (status) {
    "TO_DO" -> "To do"
    "IN_PROGRESS" -> "In progress"
    "DONE" -> "Done"
    else -> status
}
