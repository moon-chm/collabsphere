package dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class TaskRequest(
    val taskName: String,
    val taskDescription: String,
    val assignedToUserId: Int?,
    val workspaceId: Int,
    val status: String,
    val idempotencyKey: String? = null,
    val dueDate: Long? = null,
    val priority: String? = null,
    val checklist: List<ChecklistItem>? = null,
    val labels: List<String>? = null
)

@Serializable
data class ChecklistItem(
    val text: String,
    val done: Boolean = false
)

@Serializable
data class TaskResponse(
    val id: Int,
    val createdByUserId: Int,
    val assignedToUserId: Int?,
    val workspaceId: Int,
    val taskName: String,
    val taskDescription: String,
    val status: String,
    val dueDate: Long? = null,
    val priority: String = "MEDIUM",
    val checklist: List<ChecklistItem> = emptyList(),
    val labels: List<String> = emptyList()
)

@Serializable
data class TaskSyncResponse(
    val id: Int,
    val createdByUserId: Int,
    val assignedToUserId: Int?,
    val workspaceId: Int,
    val taskName: String,
    val taskDescription: String,
    val status: String,
    val isDeleted: Boolean,
    val updatedAt: Long,
    val dueDate: Long? = null,
    val priority: String = "MEDIUM",
    val checklist: List<ChecklistItem> = emptyList(),
    val labels: List<String> = emptyList()
)

object TaskPriorities {
    val allowed = setOf("LOW", "MEDIUM", "HIGH")

    fun normalize(value: String?): String? =
        value?.trim()?.uppercase()?.takeIf { it in allowed }
}

object TaskExtras {
    const val MAX_CHECKLIST_ITEMS = 30
    const val MAX_CHECKLIST_TEXT = 200
    const val MAX_LABELS = 5
    const val MAX_LABEL_LENGTH = 20

    private val json = Json { ignoreUnknownKeys = true }
    private val checklistSerializer = ListSerializer(ChecklistItem.serializer())
    private val labelsSerializer = ListSerializer(String.serializer())

    fun normalizeChecklist(items: List<ChecklistItem>): List<ChecklistItem> =
        items.mapNotNull { item ->
            item.text.trim().take(MAX_CHECKLIST_TEXT).takeIf { it.isNotEmpty() }?.let { ChecklistItem(it, item.done) }
        }.take(MAX_CHECKLIST_ITEMS)

    fun normalizeLabels(labels: List<String>): List<String> =
        labels.map { it.trim().take(MAX_LABEL_LENGTH) }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(MAX_LABELS)

    fun encodeChecklist(items: List<ChecklistItem>): String? =
        items.takeIf { it.isNotEmpty() }?.let { json.encodeToString(checklistSerializer, it) }

    fun encodeLabels(labels: List<String>): String? =
        labels.takeIf { it.isNotEmpty() }?.let { json.encodeToString(labelsSerializer, it) }

    fun decodeChecklist(raw: String?): List<ChecklistItem> =
        raw?.let { runCatching { json.decodeFromString(checklistSerializer, it) }.getOrNull() }.orEmpty()

    fun decodeLabels(raw: String?): List<String> =
        raw?.let { runCatching { json.decodeFromString(labelsSerializer, it) }.getOrNull() }.orEmpty()
}
