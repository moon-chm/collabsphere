package com.example.rohit_project_challlange.view.UserUI

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.rohit_project_challlange.ui.theme.*
import com.example.rohit_project_challlange.viewmodel.UserSearchViewModel

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
                    .drawWithCache {
                        onDrawBehind {
                        drawRect(color = SurfaceRaised)
                        drawRect(color = Color.White.copy(alpha = 0.85f), topLeft = Offset(0f, 0f), size = Size(size.width, 1.dp.toPx()))
                        }
                    }
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = CoralStart, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = viewModel::onQueryChanged,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                        cursorBrush = SolidColor(CoralStart),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Search by username or email",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Ink.copy(alpha = 0.5f)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CoralStart)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when {
                errorMessage.isNotEmpty() -> {
                    Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = Destructive)
                }
                query.trim().length in 1..1 -> {
                    Text("Keep typing — at least 2 characters", style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                query.isNotBlank() && results.isEmpty() && !isSearching -> {
                    Text("No people found", style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(results, key = { it.id }) { user ->
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
