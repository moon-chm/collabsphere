package com.collabsphere.app.model.task

import androidx.room.TypeConverter
import com.collabsphere.app.dto.task.ChecklistItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object TaskListCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val checklistSerializer = ListSerializer(ChecklistItem.serializer())
    private val labelsSerializer = ListSerializer(String.serializer())

    fun encodeChecklist(items: List<ChecklistItem>): String = json.encodeToString(checklistSerializer, items)

    fun decodeChecklist(raw: String?): List<ChecklistItem> =
        raw?.let { runCatching { json.decodeFromString(checklistSerializer, it) }.getOrNull() }.orEmpty()

    fun encodeLabels(labels: List<String>): String = json.encodeToString(labelsSerializer, labels)

    fun decodeLabels(raw: String?): List<String> =
        raw?.let { runCatching { json.decodeFromString(labelsSerializer, it) }.getOrNull() }.orEmpty()
}

class TaskListConverters {
    @TypeConverter
    fun checklistToString(items: List<ChecklistItem>): String = TaskListCodec.encodeChecklist(items)

    @TypeConverter
    fun stringToChecklist(raw: String?): List<ChecklistItem> = TaskListCodec.decodeChecklist(raw)

    @TypeConverter
    fun labelsToString(labels: List<String>): String = TaskListCodec.encodeLabels(labels)

    @TypeConverter
    fun stringToLabels(raw: String?): List<String> = TaskListCodec.decodeLabels(raw)
}
