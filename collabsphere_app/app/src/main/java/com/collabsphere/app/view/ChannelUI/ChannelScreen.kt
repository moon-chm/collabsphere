package com.collabsphere.app.view.channel

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.collabsphere.app.view.components.ChannelSkeletonList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.model.channels.ChannelEntity
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.viewmodel.channel.ChannelViewModel

@Composable
fun ChannelScreen(
    viewModel: ChannelViewModel,
    onChannelClick: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val channels by viewModel.userChannels.collectAsStateWithLifecycle()
    val unreadCounts by viewModel.unreadCounts.collectAsStateWithLifecycle()
    // Coming back from a channel (or the app) re-reads the badges, so a channel just read clears.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        // Brief delay: leaving a channel saves its read position just as this screen resumes.
        viewModel.refreshUnreadCounts(debounceMs = 400L)
        onPauseOrDispose { }
    }
    val channelStatus by viewModel.channelStatus.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var channelToDelete by remember { mutableStateOf<ChannelEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(channelStatus) {
        channelStatus?.let { status ->
            snackbarHostState.showSnackbar(status)
            viewModel.clearStatus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PrintPaper)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MisregisteredTitle(text = "Channels", style = PrintType.H2)
                    PrintChip(text = "${channels?.size ?: "…"} active", fill = PrintLeaf)
                }
                Spacer(Modifier.width(12.dp))
                SkeuoHeaderActionButton(
                    text = "New channel",
                    icon = Icons.Default.Add,
                    onClick = { showCreateDialog = true }
                )
            }

            androidx.compose.animation.Crossfade(
                targetState = channels,
                label = "channelsCrossfade",
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { state ->
                when {
                    state == null -> {
                        ChannelSkeletonList(modifier = Modifier.fillMaxSize())
                    }
                    state.isEmpty() -> {
                        com.collabsphere.app.view.components.SkeuoEmptyState(
                            icon = Icons.Default.Tag,
                            title = "No channels yet",
                            description = "Tap “New channel” to organise conversations around topics and projects.",
                            actionLabel = null,
                            onAction = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        val listState = rememberLazyListState()
                        val entranceRegistry = rememberEntranceRegistry()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingScrollEdges(listState = listState, top = 20.dp, bottom = 48.dp),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 6.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(state, key = { _, it -> it.id }) { index, channel ->
                                SkeuoChannelItem(
                                    channel = channel,
                                    unreadCount = unreadCounts[channel.id] ?: 0,
                                    onChannelClick = {
                                        viewModel.onChannelOpened(channel.id)
                                        onChannelClick(channel)
                                    },
                                    onDeleteClick = { channelToDelete = channel },
                                    modifier = Modifier
                                        .animateItem()
                                        .printListEntrance(channel.id, index, entranceRegistry)
                                )
                            }
                        }
                    }
                }
            }
        }


        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp)
        )

        if (showCreateDialog) {
            CreateChannelDialog(
                viewModel = viewModel,
                onDismiss = { showCreateDialog = false }
            )
        }

        channelToDelete?.let { channel ->
            DeleteChannelConfirmationDialog(
                channelName = channel.channelName,
                onConfirm = {
                    viewModel.onDeleteChannel(channel)
                    channelToDelete = null
                },
                onDismiss = { channelToDelete = null }
            )
        }
    }
}

@Composable
fun SkeuoChannelItem(
    channel: ChannelEntity,
    unreadCount: Int = 0,
    onChannelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPrintPress(interactionSource)
    val tagShape = remember(channel.id) { BlobShape(seed = channel.id, wobble = 0.14f) }
    val tagFill = listOf(PrintSky, PrintLeaf, PrintCoral)[Math.floorMod(channel.id, 3)]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .printSurface(shape = RoundedCornerShape(PrintTokens.RadiusCard), pressProgress = press)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onChannelClick()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Sunken / Debossed squircle icon well
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .printSurface(shape = tagShape, fill = tagFill, shadow = null),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#",
                        style = PrintType.H2.copy(fontSize = 22.sp, lineHeight = 24.sp),
                        color = PrintInk
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.channelName,
                        style = PrintType.H3,
                        color = PrintInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channel.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = channel.description,
                            style = PrintType.Caption,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (unreadCount > 0) {
                    com.collabsphere.app.view.components.UnreadCountBadge(
                        count = unreadCount,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
                SkeuoActionIconButton(
                    onClick = onDeleteClick,
                    icon = Icons.Default.DeleteOutline,
                    contentDescription = "Delete channel ${channel.channelName}",
                    tint = DestructiveStart,
                    size = 40.dp,
                    iconSize = 20.dp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = PrintInk,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChannelBottomSheet(
    viewModel: ChannelViewModel,
    onDismiss: () -> Unit
) {
    val name by viewModel.channelName.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val isCreatingChannel by viewModel.isCreatingChannel.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PrintWhite,
        scrimColor = PrintInk.copy(alpha = 0.45f),
        dragHandle = { PrintSheetHandle() },
        shape = PrintSheetShape,
        modifier = Modifier.printSheetOutline()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .printSurface(shape = BlobShape(seed = 7, wobble = 0.14f), fill = PrintCoral, shadow = null),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = null,
                            tint = PrintInk,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Create channel",
                            style = PrintType.H2.copy(fontSize = 24.sp, lineHeight = 28.sp),
                            color = PrintInk
                        )
                        Text(
                            text = "Channels are where your team talks things through",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    }
                }

                PrintIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    fill = PrintPaper,
                    size = 40.dp,
                    iconSize = 18.dp,
                    shadow = false
                )
            }

            // SECTION: CHANNEL DETAILS
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CHANNEL DETAILS",
                    style = PrintType.Tiny,
                    color = PrintInk
                )

                com.collabsphere.app.view.components.SkeuoTextField(
                    value = name,
                    onValueChange = {
                        // Clean spaces into hyphens or keep clean channel name
                        viewModel.onChannelNamechange(it.lowercase().replace(" ", "-"))
                    },
                    label = "Channel name",
                    placeholder = "e.g. announcements, general",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = null,
                            tint = PrintInk,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )

                Spacer(Modifier.height(4.dp))

                com.collabsphere.app.view.components.SkeuoTextField(
                    value = description,
                    onValueChange = { viewModel.onChannelDescriptionChange(it) },
                    label = "Description",
                    placeholder = "What is this channel about? (optional)",
                    imeAction = ImeAction.Done,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3
                )
            }

            // INFO CARD: GUIDELINES
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PrintTokens.RadiusSmall))
                    .background(PrintPaper)
                    .border(1.5.dp, PrintInk, RoundedCornerShape(PrintTokens.RadiusSmall))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = PrintInk,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "All workspace members will automatically have access to this channel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            val canCreate = name.trim().isNotEmpty() && !isCreatingChannel
            com.collabsphere.app.view.components.WorkspaceDialogButton(
                text = "Create channel",
                loadingText = "Creating…",
                colors = listOf(PrintInk),
                enabled = canCreate,
                loading = isCreatingChannel,
                onClick = {
                    if (canCreate) {
                        viewModel.onCreateChannel()
                        onDismiss()
                    }
                }
            )
            PrintButton(
                text = "Cancel",
                onClick = onDismiss,
                primary = false,
                enabled = !isCreatingChannel,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Backward compatibility alias
@Composable
fun CreateChannelDialog(
    viewModel: ChannelViewModel,
    onDismiss: () -> Unit
) {
    CreateChannelBottomSheet(viewModel = viewModel, onDismiss = onDismiss)
}

@Composable
fun DeleteChannelConfirmationDialog(
    channelName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    com.collabsphere.app.view.components.PrintConfirmDialog(
        title = "Delete #$channelName?",
        message = "Every message in this channel will be gone for good.",
        confirmLabel = "Delete channel",
        icon = Icons.Default.WarningAmber,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
