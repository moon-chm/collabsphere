package com.collabsphere.app.view.UserUI

import androidx.compose.foundation.border
import com.collabsphere.app.view.components.chatHeaderBar
import com.collabsphere.app.view.components.CollabSpinner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.viewmodel.UserSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSearchScreen(
    viewModel: UserSearchViewModel,
    onBack: () -> Unit,
    onUserClick: (Int) -> Unit
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .chatHeaderBar()
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Find people",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp)) {
            var isSearchFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .skeuoFocusRing(isSearchFocused, cornerRadius = 27.dp)
                    .skeuoInset(cornerRadius = 27.dp, depth = 3.dp, symmetric = true, shadowStrength = com.collabsphere.app.view.components.FieldInsetShadow)
                    .clip(RoundedCornerShape(27.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = PrintInk, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = viewModel::onQueryChanged,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isSearchFocused = it.isFocused },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                        cursorBrush = SolidColor(PrintInk),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Search by username or email",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Ink.copy(alpha = 0.40f)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    if (isSearching) {
                        CollabSpinner(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PrintInk)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            androidx.compose.animation.Crossfade(
                targetState = Triple(query, results, errorMessage),
                label = "userSearchCrossfade",
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { (currentQuery, currentResults, currentError) ->
                when {
                    currentError.isNotEmpty() -> {
                        com.collabsphere.app.view.components.SkeuoEmptyState(
                            icon = Icons.Default.Search,
                            title = "Search failed",
                            description = currentError,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    currentQuery.trim().length in 1..1 -> {
                        com.collabsphere.app.view.components.SkeuoEmptyState(
                            icon = Icons.Default.Search,
                            title = "Keep typing",
                            description = "Type at least 2 characters to search for users.",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    currentQuery.isNotBlank() && currentResults.isEmpty() && !isSearching -> {
                        com.collabsphere.app.view.components.SkeuoEmptyState(
                            icon = Icons.Default.Search,
                            title = "No people found",
                            description = "No users match your search query.",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    currentQuery.isBlank() && currentResults.isEmpty() -> {
                        com.collabsphere.app.view.components.SkeuoEmptyState(
                            icon = Icons.Default.Search,
                            title = "Find People",
                            description = "Search by username or email to find teammates.",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingScrollEdges(listState = listState, top = 20.dp, bottom = 48.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(currentResults, key = { it.id }) { user ->
                                UserRow(
                                    avatarUrl = user.avatarUrl,
                                    displayName = user.username,
                                    subtitle = user.email,
                                    onClick = { onUserClick(user.id) }
                                )
                            }
                        }
                    }
                }
            }
    }
}
}
