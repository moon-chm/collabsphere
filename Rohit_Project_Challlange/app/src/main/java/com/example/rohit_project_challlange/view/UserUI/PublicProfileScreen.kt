package com.example.rohit_project_challlange.view.UserUI

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.rohit_project_challlange.ui.theme.*
import com.example.rohit_project_challlange.viewmodel.PublicProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    viewModel: PublicProfileViewModel,
    onBack: () -> Unit
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isBlocked by viewModel.isBlocked.collectAsStateWithLifecycle()
    val isBlockActionInFlight by viewModel.isBlockActionInFlight.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val notFound by viewModel.notFound.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(statusMessage) {
        if (statusMessage.isNotEmpty()) {
            snackbarHostState.showSnackbar(statusMessage)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .drawBehind {
                        drawRect(color = SurfaceRaised)
                        drawRect(
                            color = Color.White.copy(alpha = 0.85f),
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, 1.dp.toPx())
                        )
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
                        text = "Profile",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading && profile == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = CoralStart)
                }
                notFound -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Muted, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "This profile isn't available",
                            style = MaterialTheme.typography.titleMedium,
                            color = Muted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                profile != null -> {
                    val p = profile!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(brush = Brush.linearGradient(listOf(SurfaceRaised, Surface))),
                            contentAlignment = Alignment.Center
                        ) {
                            val initialsFallback: @Composable () -> Unit = {
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(CircleShape)
                                        .background(brush = Brush.linearGradient(listOf(CoralLight, CoralStart))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = p.username.trim().take(1).uppercase().ifEmpty { "U" },
                                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp),
                                        color = Color.White
                                    )
                                }
                            }
                            if (p.avatarUrl.isEmpty()) {
                                initialsFallback()
                            } else {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context).data(resolveAvatarUrl(p.avatarUrl)).crossfade(true)
                                        .size(with(LocalDensity.current) { 82.dp.roundToPx() }).build(),
                                    contentDescription = p.username,
                                    modifier = Modifier.size(82.dp).clip(CircleShape),
                                    loading = { initialsFallback() },
                                    error = { initialsFallback() }
                                )
                            }
                        }

                        Text(
                            text = p.username,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Ink
                        )

                        if (!p.statusMessage.isNullOrBlank()) {
                            Text(p.statusMessage!!, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
                        }

                        if (p.isOnline != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (p.isOnline) Mint else Muted)
                                )
                                Text(
                                    text = if (p.isOnline) "Online now" else "Offline",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Muted
                                )
                            }
                        }

                        if (!p.bio.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .skeuoFloatingCard(cornerRadius = 18.dp)
                                    .padding(18.dp)
                            ) {
                                Text(p.bio!!, style = MaterialTheme.typography.bodyLarge, color = Ink)
                            }
                        }

                        if (!p.email.isNullOrBlank()) {
                            Text("Email: ${p.email}", style = MaterialTheme.typography.bodyMedium, color = Muted)
                        }

                        Spacer(Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .drawBehind {
                                    val color = if (isBlocked) Mint else Destructive
                                    drawRoundRect(color = ShadowDark.copy(alpha = 0.18f), topLeft = Offset(0f, 3.dp.toPx()), size = Size(size.width, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
                                    drawRoundRect(color = SurfaceRaised, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
                                    drawRoundRect(color = color.copy(alpha = 0.35f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx()))
                                }
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = !isBlockActionInFlight
                                ) { viewModel.onToggleBlock() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = if (isBlocked) Mint else Destructive,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isBlocked) "Unblock" else "Block",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isBlocked) Mint else Destructive
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
