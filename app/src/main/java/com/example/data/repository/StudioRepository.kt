package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.data.export.AndroidExportConfig
import com.example.data.export.GeneratedAndroidProjectFiles
import com.example.data.export.ProjectExportEngine
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.CloudExportRecord
import com.example.data.model.CloudMessage
import com.example.data.model.CloudProject
import com.example.data.model.GeminiModelCatalog
import com.example.data.model.ProjectCategory
import com.example.data.model.StudioTemplate
import com.example.data.model.ThemeMode
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class StudioRepository(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val preferencesRepository: UserPreferencesRepository,
    private val exportEngine: ProjectExportEngine,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        context = context.applicationContext,
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        preferencesRepository = UserPreferencesRepository(context.applicationContext),
        exportEngine = ProjectExportEngine(context.applicationContext)
    )

    val preferences: Flow<UserPreferences> = preferencesRepository.preferencesFlow

    private fun requireUserId(expectedUid: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("برای دسترسی به فضای ابری، ابتدا با حساب گوگل وارد شوید.")
        if (expectedUid != null && uid != expectedUid) {
            throw IllegalStateException("تغییر حساب کاربری شناسایی شد.")
        }
        return uid
    }

    private fun projectsCollection(uid: String) =
        db.collection("users").document(uid).collection("projects")

    private fun messagesCollection(uid: String, projectId: String) =
        projectsCollection(uid).document(projectId).collection("messages")

    private fun exportsCollection(uid: String) =
        db.collection("users").document(uid).collection("exports")

    fun observeProjects(userId: String): Flow<List<CloudProject>> {
        val path = "users/$userId/projects"
        return projectsCollection(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudProject::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(
                    compareByDescending<CloudProject> { it.pinned }
                        .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeMessages(userId: String, projectId: String): Flow<List<CloudMessage>> {
        val path = "users/$userId/projects/$projectId/messages"
        return messagesCollection(userId, projectId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeExportRecords(userId: String): Flow<List<CloudExportRecord>> {
        val path = "users/$userId/exports"
        return exportsCollection(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudExportRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun createProject(
        userId: String,
        title: String,
        description: String,
        category: String,
        modelId: String,
        systemInstruction: String,
        temperature: Double = 0.7,
        topP: Double = 0.95,
        topK: Long = 40L,
        maxOutputTokens: Long = 2048L,
        jsonModeEnabled: Boolean = false,
        jsonSchema: String = "",
        safetyBalanced: Boolean = true
    ): Result<String> {
        var docPath = "users/$userId/projects"
        return try {
            val uid = requireUserId(userId)
            val safeTitle = title.trim().take(120)
            require(safeTitle.isNotBlank()) { "عنوان پروژه نمی‌تواند خالی باشد." }
            val safeCategory = ProjectCategory.fromId(category).let {
                if (it == ProjectCategory.ALL) ProjectCategory.CHAT.id else it.id
            }
            val safeModel = GeminiModelCatalog.findById(modelId).id
            val docRef = projectsCollection(uid).document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "description" to description.trim().take(500),
                "category" to safeCategory,
                "modelId" to safeModel,
                "systemInstruction" to systemInstruction.trim().take(12000),
                "temperature" to temperature.coerceIn(0.0, 2.0),
                "topP" to topP.coerceIn(0.05, 1.0),
                "topK" to topK.coerceIn(1L, 100L),
                "maxOutputTokens" to maxOutputTokens.coerceIn(256L, 8192L),
                "jsonModeEnabled" to jsonModeEnabled,
                "jsonSchema" to jsonSchema.take(12000),
                "safetyBalanced" to safetyBalanced,
                "pinned" to false,
                "messageCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createFromTemplate(userId: String, template: StudioTemplate): Result<String> {
        return createProject(
            userId = userId,
            title = template.title,
            description = template.subtitle,
            category = template.category.id,
            modelId = template.modelId,
            systemInstruction = template.systemInstruction,
            temperature = template.temperature.toDouble(),
            jsonModeEnabled = template.jsonModeEnabled,
            jsonSchema = template.jsonSchema
        )
    }

    suspend fun updateSystemInstruction(
        userId: String,
        projectId: String,
        instruction: String
    ): Result<Unit> {
        val docPath = "users/$userId/projects/$projectId"
        return try {
            val uid = requireUserId(userId)
            require(projectId.isNotBlank())
            projectsCollection(uid).document(projectId).update(
                mapOf(
                    "systemInstruction" to instruction.trim().take(12000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateModelConfig(
        userId: String,
        projectId: String,
        modelId: String,
        temperature: Double,
        topP: Double,
        topK: Long,
        maxOutputTokens: Long,
        jsonModeEnabled: Boolean,
        jsonSchema: String,
        safetyBalanced: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/projects/$projectId"
        return try {
            val uid = requireUserId(userId)
            require(projectId.isNotBlank())
            val safeModel = GeminiModelCatalog.findById(modelId).id
            projectsCollection(uid).document(projectId).update(
                mapOf(
                    "modelId" to safeModel,
                    "temperature" to temperature.coerceIn(0.0, 2.0),
                    "topP" to topP.coerceIn(0.05, 1.0),
                    "topK" to topK.coerceIn(1L, 100L),
                    "maxOutputTokens" to maxOutputTokens.coerceIn(256L, 8192L),
                    "jsonModeEnabled" to jsonModeEnabled,
                    "jsonSchema" to jsonSchema.take(12000),
                    "safetyBalanced" to safetyBalanced,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun togglePin(userId: String, projectId: String, currentlyPinned: Boolean): Result<Unit> {
        val docPath = "users/$userId/projects/$projectId"
        return try {
            val uid = requireUserId(userId)
            require(projectId.isNotBlank())
            projectsCollection(uid).document(projectId).update(
                mapOf(
                    "pinned" to !currentlyPinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun duplicateProject(userId: String, project: CloudProject): Result<String> {
        return try {
            val uid = requireUserId(userId)
            val created = createProject(
                userId = uid,
                title = "${project.title} (کپی)".take(120),
                description = project.description,
                category = project.category,
                modelId = project.modelId,
                systemInstruction = project.systemInstruction,
                temperature = project.temperature,
                topP = project.topP,
                topK = project.topK,
                maxOutputTokens = project.maxOutputTokens,
                jsonModeEnabled = project.jsonModeEnabled,
                jsonSchema = project.jsonSchema,
                safetyBalanced = project.safetyBalanced
            )
            created
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$userId/projects")
            Result.failure(e)
        }
    }

    suspend fun deleteProject(userId: String, projectId: String): Result<Unit> {
        val docPath = "users/$userId/projects/$projectId"
        return try {
            val uid = requireUserId(userId)
            require(projectId.isNotBlank())
            val msgs = messagesCollection(uid, projectId).get().await()
            if (!msgs.isEmpty) {
                val batch = db.batch()
                msgs.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            projectsCollection(uid).document(projectId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        userId: String,
        projectId: String,
        role: String,
        content: String,
        modelId: String,
        estimatedTokens: Long,
        hasImageAttachment: Boolean
    ): Result<String> {
        var docPath = "users/$userId/projects/$projectId/messages"
        return try {
            val uid = requireUserId(userId)
            val safeContent = content.trim().take(60000)
            require(safeContent.isNotBlank()) { "متن پیام خالی است." }
            val safeRole = if (role == "model") "model" else "user"
            val docRef = messagesCollection(uid, projectId).document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "projectId" to projectId,
                "role" to safeRole,
                "content" to safeContent,
                "modelId" to modelId.take(64).ifBlank { GeminiModelCatalog.DEFAULT_MODEL_ID },
                "estimatedTokens" to estimatedTokens.coerceIn(0L, 2_000_000L),
                "hasImageAttachment" to hasImageAttachment,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            projectsCollection(uid).document(projectId).update(
                mapOf(
                    "messageCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(userId: String, projectId: String, messageId: String): Result<Unit> {
        val docPath = "users/$userId/projects/$projectId/messages/$messageId"
        return try {
            val uid = requireUserId(userId)
            messagesCollection(uid, projectId).document(messageId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearMessages(userId: String, projectId: String): Result<Unit> {
        val path = "users/$userId/projects/$projectId/messages"
        return try {
            val uid = requireUserId(userId)
            val snapshot = messagesCollection(uid, projectId).get().await()
            if (!snapshot.isEmpty) {
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            projectsCollection(uid).document(projectId).update(
                mapOf(
                    "messageCount" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private suspend fun recordExport(
        userId: String,
        projectTitle: String,
        exportType: String,
        packageName: String,
        fileName: String,
        sizeBytes: Long
    ) {
        val docRef = exportsCollection(userId).document()
        val payload = mapOf(
            "userId" to userId,
            "projectTitle" to projectTitle.take(140).ifBlank { "Prompt Studio" },
            "exportType" to exportType,
            "packageName" to packageName.take(160).ifBlank { context.packageName },
            "fileName" to fileName.take(160).ifBlank { "export.zip" },
            "sizeBytes" to sizeBytes.coerceIn(0L, 536_870_912L),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }

    suspend fun deleteExportRecord(userId: String, recordId: String): Result<Unit> {
        val docPath = "users/$userId/exports/$recordId"
        return try {
            val uid = requireUserId(userId)
            exportsCollection(uid).document(recordId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun getProjectPreviewFiles(
        project: CloudProject,
        messages: List<CloudMessage>,
        config: AndroidExportConfig
    ): GeneratedAndroidProjectFiles {
        return exportEngine.generateFiles(project, messages, config)
    }

    suspend fun exportAndroidProjectZipToUri(
        userId: String,
        uri: Uri,
        project: CloudProject,
        messages: List<CloudMessage>,
        config: AndroidExportConfig,
        fileName: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val uid = requireUserId(userId)
            val bytes = context.contentResolver.openOutputStream(uri)?.use { out ->
                exportEngine.writeAndroidProjectZip(out, project, messages, config)
            } ?: error("خطا در نوشتن فایل ZIP.")
            recordExport(
                userId = uid,
                projectTitle = project.title,
                exportType = "ANDROID_PROJECT_ZIP",
                packageName = exportEngine.sanitizePackageName(config.packageName),
                fileName = fileName,
                sizeBytes = bytes
            )
            Result.success(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun prepareShareableProjectZip(
        userId: String,
        project: CloudProject,
        messages: List<CloudMessage>,
        config: AndroidExportConfig
    ): Result<File> {
        return try {
            val uid = requireUserId(userId)
            val (file, size) = exportEngine.createShareableProjectZipFile(project, messages, config)
            recordExport(
                userId = uid,
                projectTitle = project.title,
                exportType = "ANDROID_PROJECT_ZIP",
                packageName = exportEngine.sanitizePackageName(config.packageName),
                fileName = file.name,
                sizeBytes = size
            )
            Result.success(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportInstalledApkToUri(userId: String, uri: Uri, fileName: String): Result<Long> {
        return try {
            val uid = requireUserId(userId)
            val size = exportEngine.writeInstalledApkToUri(uri)
            recordExport(
                userId = uid,
                projectTitle = "Prompt Studio (APK)",
                exportType = "INSTALLED_APK",
                packageName = context.packageName,
                fileName = fileName,
                sizeBytes = size
            )
            Result.success(size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun prepareShareableInstalledApk(userId: String): Result<File> {
        return try {
            val uid = requireUserId(userId)
            val (file, size) = exportEngine.createShareableInstalledApkFile()
            recordExport(
                userId = uid,
                projectTitle = "Prompt Studio (APK)",
                exportType = "INSTALLED_APK",
                packageName = context.packageName,
                fileName = file.name,
                sizeBytes = size
            )
            Result.success(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportFullBackupToUri(
        userId: String,
        uri: Uri,
        projects: List<CloudProject>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val uid = requireUserId(userId)
            val payload = projects.map { project ->
                val msgs = messagesCollection(uid, project.id)
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .get()
                    .await()
                    .toObjects(CloudMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                project to msgs
            }
            val json = exportEngine.exportWorkspaceJson(payload)
            val bytes = json.toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("خطا در ذخیره فایل پشتیبان.")
            recordExport(
                userId = uid,
                projectTitle = "پشتیبان ابری (${projects.size} پروژه)",
                exportType = "WORKSPACE_BACKUP",
                packageName = context.packageName,
                fileName = "prompt_studio_cloud_backup.json",
                sizeBytes = bytes.size.toLong()
            )
            Result.success(projects.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$userId/projects")
            Result.failure(e)
        }
    }

    suspend fun importFullBackupFromUri(userId: String, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val uid = requireUserId(userId)
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: error("خطا در خواندن فایل پشتیبان.")
            val parsed = exportEngine.parseWorkspaceJson(raw)
            var importedCount = 0
            for ((project, messages) in parsed) {
                val createdId = createProject(
                    userId = uid,
                    title = project.title,
                    description = project.description,
                    category = project.category,
                    modelId = project.modelId,
                    systemInstruction = project.systemInstruction,
                    temperature = project.temperature,
                    topP = project.topP,
                    topK = project.topK,
                    maxOutputTokens = project.maxOutputTokens,
                    jsonModeEnabled = project.jsonModeEnabled,
                    jsonSchema = project.jsonSchema,
                    safetyBalanced = project.safetyBalanced
                ).getOrNull() ?: continue
                for (msg in messages) {
                    addMessage(
                        userId = uid,
                        projectId = createdId,
                        role = msg.role,
                        content = msg.content,
                        modelId = msg.modelId,
                        estimatedTokens = msg.estimatedTokens,
                        hasImageAttachment = msg.hasImageAttachment
                    )
                }
                importedCount++
            }
            Result.success(importedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) = preferencesRepository.setThemeMode(mode)
    suspend fun setDynamicColor(enabled: Boolean) = preferencesRepository.setDynamicColor(enabled)
    suspend fun setDefaultModelId(modelId: String) = preferencesRepository.setDefaultModelId(modelId)
}
