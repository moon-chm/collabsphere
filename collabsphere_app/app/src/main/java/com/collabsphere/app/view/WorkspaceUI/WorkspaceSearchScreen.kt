package com.collabsphere.app.view.WorkspaceUI

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.lazy.rememberLazyListState
import com.collabsphere.app.ui.theme.*
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
import com.collabsphere.app.view.components.CollabSpinner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
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
            SkeuoActionIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                size = 44.dp,
                iconSize = 22.dp,
                tint = Ink
            )
            var isSearchFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .skeuoFocusRing(isSearchFocused, cornerRadius = 26.dp)
                    .skeuoInset(cornerRadius = 26.dp, depth = 3.dp, symmetric = true, shadowStrength = com.collabsphere.app.view.components.FieldInsetShadow)
                    .clip(RoundedCornerShape(26.dp))
                    .padding(start = 18.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = PrintInk, modifier = Modifier.size(20.dp))
                BasicTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { isSearchFocused = it.isFocused },
                    singleLine = true,
                    textStyle = PrintType.Body.copy(color = PrintInk),
                    cursorBrush = SolidColor(PrintInk),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search $workspaceName",
                                    style = PrintType.Body,
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
                        tint = PrintInk,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { viewModel.onQueryChange("") }
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }
            }
        }

        androidx.compose.animation.Crossfade(
            targetState = state,
            label = "searchStateCrossfade"
        ) { current ->
            when (current) {
                SearchUiState.Idle -> {
                    com.collabsphere.app.view.components.SkeuoEmptyState(
                        icon = Icons.Default.Search,
                        title = "Search this workspace",
                        description = if (query.trim().isEmpty()) "Search messages, DMs, tasks, notes and files in this workspace."
                            else "Type at least ${WorkspaceSearchViewModel.MIN_QUERY_LENGTH} characters.",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                SearchUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CollabSpinner(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
                is SearchUiState.Error -> {
                    com.collabsphere.app.view.components.SkeuoEmptyState(
                        icon = Icons.Default.Search, // Or warning icon
                        title = "Search failed",
                        description = current.message,
                        actionLabel = "Try again",
                        onAction = { viewModel.retry() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is SearchUiState.Results -> if (current.response.isEmpty) {
                    com.collabsphere.app.view.components.SkeuoEmptyState(
                        icon = Icons.Default.Search,
                        title = "No results found",
                        description = "No results for \"${current.response.query}\". Try adjusting your search terms.",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SearchResultsList(current.response, onMessageClick, onDmClick, onTaskClick, onNoteClick, onFileClick)
                }
            }
        }
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
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .fadingScrollEdges(listState = listState, top = 20.dp, bottom = 48.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        section("Channel messages", "messages", response.messages, { it.id.toString() }) { hit ->
            ResultRow(Icons.Outlined.Tag, "#${hit.channelName} · ${hit.userName}", hit.content, PrintSky) { onMessageClick(hit) }
        }
        section("Direct messages", "dms", response.directMessages, { it.id.toString() }) { hit ->
            ResultRow(Icons.Outlined.ChatBubbleOutline, hit.partnerName, hit.content, PrintCoral) { onDmClick(hit) }
        }
        section("Tasks", "tasks", response.tasks, { it.id.toString() }) { hit ->
            ResultRow(Icons.Outlined.CheckBox, hit.taskName, taskStatusLabel(hit.status), PrintLeaf) { onTaskClick(hit) }
        }
        section("Notes", "notes", response.notes, { it.id.toString() }) { hit ->
            ResultRow(Icons.Outlined.Description, hit.notesName, hit.snippet, PrintSky) { onNoteClick(hit) }
        }
        section("Files", "files", response.files, { it.id.toString() }) { hit ->
            ResultRow(Icons.Outlined.Folder, hit.fileName, hit.mimeType, PrintCoral) { onFileClick(hit) }
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
            text = "${title.uppercase()} · ${hits.size}",
            style = PrintType.Tiny,
            color = PrintInk,
            modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)
        )
    }
    items(hits, key = { "${keyPrefix}_${idOf(it)}" }) { row(it) }
}

@Composable
private fun ResultRow(icon: ImageVector, title: String, subtitle: String, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val rowShape = RoundedCornerShape(PrintTokens.RadiusSmall)
    val tagShape = remember(title) { BlobShape(seed = title.hashCode(), wobble = 0.14f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(PrintWhite)
            .border(1.5.dp, PrintInk, rowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .printSurface(shape = tagShape, fill = accent, shadow = null, outline = 1.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = PrintInk, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = PrintType.Body.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = PrintInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = PrintType.Caption,
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
