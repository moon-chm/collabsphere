package com.collabsphere.app

import com.collabsphere.app.dto.message.ChannelReactionSummary
import com.collabsphere.app.dto.message.ChannelReadState
import com.collabsphere.app.dto.message.ChannelTypingEvent
import com.collabsphere.app.dto.message.MessageSyncDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ChannelMessageCenter {
    private val _incoming = MutableSharedFlow<MessageSyncDto>(extraBufferCapacity = 64)
    val incoming: SharedFlow<MessageSyncDto> = _incoming.asSharedFlow()

    suspend fun push(message: MessageSyncDto) {
        _incoming.emit(message)
    }

    private val _reactions = MutableSharedFlow<ChannelReactionSummary>(extraBufferCapacity = 64)
    val reactions: SharedFlow<ChannelReactionSummary> = _reactions.asSharedFlow()

    suspend fun pushReaction(summary: ChannelReactionSummary) {
        _reactions.emit(summary)
    }

    private val _typing = MutableSharedFlow<ChannelTypingEvent>(extraBufferCapacity = 64)
    val typing: SharedFlow<ChannelTypingEvent> = _typing.asSharedFlow()

    suspend fun pushTyping(event: ChannelTypingEvent) {
        _typing.emit(event)
    }

    private val _reads = MutableSharedFlow<ChannelReadState>(extraBufferCapacity = 64)
    val reads: SharedFlow<ChannelReadState> = _reads.asSharedFlow()

    suspend fun pushRead(state: ChannelReadState) {
        _reads.emit(state)
    }
}
