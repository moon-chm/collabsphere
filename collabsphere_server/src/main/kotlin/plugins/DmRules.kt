package plugins

/** Pure permission rules for the DM WebSocket, kept free of DB access so they can be unit-tested. */
internal object DmRules {
    const val MAX_CONTENT_LENGTH = 10_000

    /** Why a new DM must be refused, or null if it may be sent. */
    fun sendRejection(
        senderIsMember: Boolean,
        receiverIsMember: Boolean,
        blockedEitherWay: Boolean,
        content: String,
        hasMedia: Boolean
    ): String? = when {
        !senderIsMember || !receiverIsMember -> "Both users must be members of the workspace"
        blockedEitherWay -> "Messaging is blocked between these users"
        content.isBlank() && !hasMedia -> "Message is empty"
        content.length > MAX_CONTENT_LENGTH -> "Message exceeds $MAX_CONTENT_LENGTH characters"
        else -> null
    }

    /** The other party of an existing DM when [actingUserId] took part in it, else null. */
    fun partnerOf(senderId: Int, receiverId: Int, actingUserId: Int): Int? = when (actingUserId) {
        senderId -> receiverId
        receiverId -> senderId
        else -> null
    }
}
