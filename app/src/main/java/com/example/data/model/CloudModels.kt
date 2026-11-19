package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = ProjectCategory.CHAT.id,
    val modelId: String = GeminiModelCatalog.DEFAULT_MODEL_ID,
    val systemInstruction: String = "",
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
    val topK: Long = 40L,
    val maxOutputTokens: Long = 2048L,
    val jsonModeEnabled: Boolean = false,
    val jsonSchema: String = "",
    val safetyBalanced: Boolean = true,
    val pinned: Boolean = false,
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val role: String = "user",
    val content: String = "",
    val modelId: String = GeminiModelCatalog.DEFAULT_MODEL_ID,
    val estimatedTokens: Long = 0L,
    val hasImageAttachment: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudExportRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectTitle: String = "",
    val exportType: String = "ANDROID_PROJECT_ZIP",
    val packageName: String = "",
    val fileName: String = "",
    val sizeBytes: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)
