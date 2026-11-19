package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiRepository
import com.example.data.ai.AiRunRequest
import com.example.data.ai.AiStreamEvent
import com.example.data.export.AndroidExportConfig
import com.example.data.export.GeneratedAndroidProjectFiles
import com.example.data.local.UserPreferences
import com.example.data.model.CloudExportRecord
import com.example.data.model.CloudMessage
import com.example.data.model.CloudProject
import com.example.data.model.ProjectCategory
import com.example.data.model.StudioDestination
import com.example.data.model.StudioTemplate
import com.example.data.model.ThemeMode
import com.example.data.repository.StudioRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "StudioViewModel"
private const val STOP_TIMEOUT_MS = 5_000L

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

sealed interface GenerationUiState {
    data object Idle : GenerationUiState
    data class Streaming(val partialText: String) : GenerationUiState
    data class Error(val message: String) : GenerationUiState
}

sealed interface StudioUiEvent {
    data class ShowMessage(val message: String) : StudioUiEvent
    data class ShareFile(val file: File, val mimeType: String, val chooserTitle: String) : StudioUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModel(
    private val repository: StudioRepository,
    private val aiRepository: AiRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _currentDestination = MutableStateFlow(StudioDestination.AI_STUDIO)
    val currentDestination: StateFlow<StudioDestination> = _currentDestination.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow(ProjectCategory.ALL)
    val selectedCategory: StateFlow<ProjectCategory> = _selectedCategory.asStateFlow()

    private val _activeProjectId = MutableStateFlow<String?>(null)
    val activeProjectId: StateFlow<String?> = _activeProjectId.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val generationState: StateFlow<GenerationUiState> = _generationState.asStateFlow()

    private val _attachedImageUri = MutableStateFlow<String?>(null)
    val attachedImageUri: StateFlow<String?> = _attachedImageUri.asStateFlow()

    private val _exportPreviewFiles = MutableStateFlow<GeneratedAndroidProjectFiles?>(null)
    val exportPreviewFiles: StateFlow<GeneratedAndroidProjectFiles?> = _exportPreviewFiles.asStateFlow()

    private val _exportBusy = MutableStateFlow(false)
    val exportBusy: StateFlow<Boolean> = _exportBusy.asStateFlow()

    private val _events = MutableSharedFlow<StudioUiEvent>()
    val events: SharedFlow<StudioUiEvent> = _events.asSharedFlow()

    private var activeGenerationJob: Job? = null

    val preferences: StateFlow<UserPreferences> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UserPreferences())

    // Two-tier Flow error handling unwrapping FirebaseFirestoreException from snapshots() cancellation
    val projectsState: StateFlow<CloudUiState<List<CloudProject>>> = repository.observeProjects(currentUserId)
        .map<List<CloudProject>, CloudUiState<List<CloudProject>>> { CloudUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing projects", rootError)
            emit(CloudUiState.Error(rootError.localizedMessage ?: "خطا در دریافت پروژه‌های ابری"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CloudUiState.Loading)

    val allProjects: StateFlow<List<CloudProject>> = projectsState
        .map { (it as? CloudUiState.Success)?.data ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val filteredProjects: StateFlow<List<CloudProject>> = combine(
        allProjects,
        _searchQuery,
        _selectedCategory
    ) { list, query, category ->
        val q = query.trim().lowercase()
        list.filter { item ->
            val matchesCategory = category == ProjectCategory.ALL || item.category == category.id
            val matchesQuery = q.isBlank() ||
                item.title.lowercase().contains(q) ||
                item.description.lowercase().contains(q) ||
                item.systemInstruction.lowercase().contains(q) ||
                item.modelId.lowercase().contains(q)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val totalMessageCount: StateFlow<Long> = allProjects
        .map { list -> list.sumOf { it.messageCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0L)

    val activeProject: StateFlow<CloudProject?> = combine(
        allProjects,
        _activeProjectId
    ) { list, activeId ->
        if (activeId == null) list.firstOrNull()
        else list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val activeMessagesState: StateFlow<CloudUiState<List<CloudMessage>>> = activeProject
        .map { it?.id }
        .flatMapLatest { projectId ->
            if (projectId.isNullOrBlank()) {
                flowOf(CloudUiState.Success(emptyList()))
            } else {
                repository.observeMessages(currentUserId, projectId)
                    .map<List<CloudMessage>, CloudUiState<List<CloudMessage>>> { CloudUiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing messages", rootError)
                        emit(CloudUiState.Error(rootError.localizedMessage ?: "خطا در بارگذاری پیام‌ها"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CloudUiState.Loading)

    val activeMessages: StateFlow<List<CloudMessage>> = activeMessagesState
        .map { (it as? CloudUiState.Success)?.data ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val exportRecords: StateFlow<List<CloudExportRecord>> = repository.observeExportRecords(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing exports", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun navigateTo(destination: StudioDestination) {
        _currentDestination.value = destination
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: ProjectCategory) {
        _selectedCategory.value = category
    }

    fun openProjectInWorkbench(projectId: String) {
        stopGeneration()
        _activeProjectId.value = projectId
        _attachedImageUri.value = null
        _currentDestination.value = StudioDestination.WORKBENCH
    }

    fun openProjectInExport(projectId: String) {
        _activeProjectId.value = projectId
        _currentDestination.value = StudioDestination.EXPORT
    }

    fun selectActiveProject(projectId: String) {
        stopGeneration()
        _activeProjectId.value = projectId
        _attachedImageUri.value = null
    }

    fun createProject(
        title: String,
        description: String,
        category: ProjectCategory,
        systemInstruction: String,
        modelId: String = preferences.value.defaultModelId
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        viewModelScope.launch {
            repository.createProject(
                userId = currentUserId,
                title = cleanTitle,
                description = description,
                category = category.id,
                modelId = modelId,
                systemInstruction = systemInstruction
            ).fold(
                onSuccess = { id ->
                    _activeProjectId.value = id
                    _currentDestination.value = StudioDestination.WORKBENCH
                    _events.emit(StudioUiEvent.ShowMessage("پروژه «$cleanTitle» در ابر ذخیره شد."))
                },
                onFailure = { e ->
                    _events.emit(StudioUiEvent.ShowMessage(e.localizedMessage ?: "خطا در ساخت پروژه ابری."))
                }
            )
        }
    }

    fun createFromTemplate(template: StudioTemplate) {
        viewModelScope.launch {
            repository.createFromTemplate(currentUserId, template).fold(
                onSuccess = { id ->
                    _activeProjectId.value = id
                    _currentDestination.value = StudioDestination.WORKBENCH
                    _events.emit(StudioUiEvent.ShowMessage("قالب «${template.title}» آماده اجرا شد."))
                },
                onFailure = { e ->
                    _events.emit(StudioUiEvent.ShowMessage(e.localizedMessage ?: "خطا در ساخت قالب."))
                }
            )
        }
    }

    fun togglePin(project: CloudProject) {
        viewModelScope.launch {
            repository.togglePin(currentUserId, project.id, project.pinned).onFailure { e ->
                _events.emit(StudioUiEvent.ShowMessage(e.localizedMessage ?: "خطا در بروزرسانی سنجاق."))
            }
        }
    }

    fun duplicateProject(project: CloudProject) {
        viewModelScope.launch {
            repository.duplicateProject(currentUserId, project).fold(
                onSuccess = { _events.emit(StudioUiEvent.ShowMessage("نسخه کپی در ابر ذخیره شد.")) },
                onFailure = { e -> _events.emit(StudioUiEvent.ShowMessage(e.localizedMessage ?: "خطا در کپی پروژه.")) }
            )
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(currentUserId, projectId).fold(
                onSuccess = { _events.emit(StudioUiEvent.ShowMessage("پروژه حذف شد.")) },
                onFailure = { e -> _events.emit(StudioUiEvent.ShowMessage(e.localizedMessage ?: "خطا در حذف پروژه.")) }
            )
        }
    }

    fun updateSystemInstruction(instruction: String) {
        val project = activeProject.value ?: return
        if (project.systemInstruction == instruction.trim()) return
        viewModelScope.launch {
            repository.updateSystemInstruction(currentUserId, project.id, instruction).fold(
                onSuccess = { _events.emit(StudioUiEvent.ShowMessage("دستور سیستمی همگام‌سازی شد.")) },
                onFailure = { e -> _events.emit(StudioUiEvent.ShowMessage(e.localizedMessage ?: "خطا در ذخیره دستور سیستمی.")) }
            )
        }
    }

    fun updateModelConfig(
        modelId: String,
        temperature: Float,
        topP: Float,
        topK: Int,
        maxOutputTokens: Int,
        jsonModeEnabled: Boolean,
        jsonSchema: String,
        safetyBalanced: Boolean
    ) {
        val project = activeProject.value ?: return
        viewModelScope.launch {
            repository.updateModelConfig(
                userId = currentUserId,
                projectId = project.id,
                modelId = modelId,
                temperature = temperature.toDouble(),
                topP = topP.toDouble(),
                topK = topK.toLong(),
                maxOutputTokens = maxOutputTokens.toLong(),
                jsonModeEnabled = jsonModeEnabled,
                jsonSchema = jsonSchema,
                safetyBalanced = safetyBalanced
            ).fold(
                onSuccess = { _events.emit(StudioUiEvent.ShowMessage("پیکربندی مدل در ابر ذخیره شد.")) },
                onFailure = { e -> _events.emit(StudioUiEvent.ShowMessage(e.localizedMessage ?: "خطا در ذخیره تنظیمات.")) }
            )
        }
    }

    fun setAttachedImage(uri: String?) {
        _attachedImageUri.value = uri
    }

    fun runPrompt(promptText: String) {
        val project = activeProject.value ?: return
        val cleanPrompt = promptText.trim()
        val imageUri = _attachedImageUri.value
        if (cleanPrompt.isBlank() && imageUri == null) return

        stopGeneration()
        val historySnapshot = activeMessages.value
        val effectivePrompt = cleanPrompt.ifBlank { "این تصویر را تحلیل کن." }

        activeGenerationJob = viewModelScope.launch {
            val userTokens = (effectivePrompt.length / 3L).coerceAtLeast(4L)
            val saveResult = repository.addMessage(
                userId = currentUserId,
                projectId = project.id,
                role = "user",
                content = effectivePrompt,
                modelId = project.modelId,
                estimatedTokens = userTokens,
                hasImageAttachment = imageUri != null
            )
            if (saveResult.isFailure) {
                _generationState.value = GenerationUiState.Error(
                    saveResult.exceptionOrNull()?.localizedMessage ?: "خطا در ذخیره پیام در فضای ابری."
                )
                return@launch
            }

            _attachedImageUri.value = null
            _generationState.value = GenerationUiState.Streaming("")

            try {
                aiRepository.streamPrompt(
                    AiRunRequest(
                        project = project,
                        history = historySnapshot,
                        userPrompt = effectivePrompt,
                        attachedImageUri = imageUri
                    )
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Chunk -> {
                            _generationState.value = GenerationUiState.Streaming(event.accumulatedText)
                        }
                        is AiStreamEvent.Completed -> {
                            repository.addMessage(
                                userId = currentUserId,
                                projectId = project.id,
                                role = "model",
                                content = event.fullText,
                                modelId = project.modelId,
                                estimatedTokens = event.estimatedTokens,
                                hasImageAttachment = false
                            )
                            _generationState.value = GenerationUiState.Idle
                        }
                        is AiStreamEvent.Error -> {
                            _generationState.value = GenerationUiState.Error(event.message)
                        }
                    }
                }
            } catch (e: CancellationException) {
                _generationState.value = GenerationUiState.Idle
                throw e
            } catch (e: Exception) {
                _generationState.value = GenerationUiState.Error(
                    e.localizedMessage ?: "خطا در ارتباط زنده با Gemini."
                )
            }
        }
    }

    fun regenerateLastResponse() {
        val project = activeProject.value ?: return
        val currentMsgs = activeMessages.value
        val lastUserMsg = currentMsgs.lastOrNull { it.role == "user" } ?: return
        val lastModelMsg = currentMsgs.lastOrNull()

        stopGeneration()
        activeGenerationJob = viewModelScope.launch {
            if (lastModelMsg != null && lastModelMsg.role == "model") {
                repository.deleteMessage(currentUserId, project.id, lastModelMsg.id)
            }
            val priorHistory = currentMsgs.takeWhile { it.id != lastUserMsg.id }
            _generationState.value = GenerationUiState.Streaming("")

            try {
                aiRepository.streamPrompt(
                    AiRunRequest(
                        project = project,
                        history = priorHistory,
                        userPrompt = lastUserMsg.content,
                        attachedImageUri = null
                    )
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Chunk -> {
                            _generationState.value = GenerationUiState.Streaming(event.accumulatedText)
                        }
                        is AiStreamEvent.Completed -> {
                            repository.addMessage(
                                userId = currentUserId,
                                projectId = project.id,
                                role = "model",
                                content = event.fullText,
                                modelId = project.modelId,
                                estimatedTokens = event.estimatedTokens,
                                hasImageAttachment = false
                            )
                            _generationState.value = GenerationUiState.Idle
                        }
                        is AiStreamEvent.Error -> {
                            _generationState.value = GenerationUiState.Error(event.message)
                        }
                    }
                }
            } catch (e: CancellationException) {
                _generationState.value = GenerationUiState.Idle
                throw e
            }
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        if (_generationState.value is GenerationUiState.Streaming) {
            _generationState.value = GenerationUiState.Idle
        }
    }

    fun clearActiveChat() {
        val project = activeProject.value ?: return
        stopGeneration()
        viewModelScope.launch {
            repository.clearMessages(currentUserId, project.id).fold(
                onSuccess = { _events.emit(StudioUiEvent.ShowMessage("سابقه گفتگو پاک شد.")) },
                onFailure = { e -> _events.emit(StudioUiEvent.ShowMessage(e.localizedMessage ?: "خطا در پاک‌سازی گفتگو.")) }
            )
        }
    }

    fun refreshExportPreview(config: AndroidExportConfig) {
        val project = activeProject.value ?: return
        _exportPreviewFiles.value = repository.getProjectPreviewFiles(project, activeMessages.value, config)
    }

    fun exportProjectZipToUri(uri: Uri, config: AndroidExportConfig, fileName: String) {
        val project = activeProject.value ?: return
        viewModelScope.launch {
            _exportBusy.value = true
            val result = repository.exportAndroidProjectZipToUri(
                userId = currentUserId,
                uri = uri,
                project = project,
                messages = activeMessages.value,
                config = config,
                fileName = fileName
            )
            _exportBusy.value = false
            result.fold(
                onSuccess = { _events.emit(StudioUiEvent.ShowMessage("بسته پروژه آماده ساخت APK ذخیره شد.")) },
                onFailure = { _events.emit(StudioUiEvent.ShowMessage(it.localizedMessage ?: "خطا در ذخیره فایل ZIP.")) }
            )
        }
    }

    fun shareProjectZip(config: AndroidExportConfig) {
        val project = activeProject.value ?: return
        viewModelScope.launch {
            _exportBusy.value = true
            val result = repository.prepareShareableProjectZip(
                userId = currentUserId,
                project = project,
                messages = activeMessages.value,
                config = config
            )
            _exportBusy.value = false
            result.fold(
                onSuccess = { file ->
                    _events.emit(
                        StudioUiEvent.ShareFile(
                            file = file,
                            mimeType = "application/zip",
                            chooserTitle = "اشتراک‌گذاری پروژه آماده APK"
                        )
                    )
                },
                onFailure = { _events.emit(StudioUiEvent.ShowMessage(it.localizedMessage ?: "خطا در آماده‌سازی فایل.")) }
            )
        }
    }

    fun exportInstalledApkToUri(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _exportBusy.value = true
            val result = repository.exportInstalledApkToUri(currentUserId, uri, fileName)
            _exportBusy.value = false
            result.fold(
                onSuccess = { _events.emit(StudioUiEvent.ShowMessage("فایل APK اپلیکیشن ذخیره شد.")) },
                onFailure = { _events.emit(StudioUiEvent.ShowMessage(it.localizedMessage ?: "خطا در استخراج APK.")) }
            )
        }
    }

    fun shareInstalledApk() {
        viewModelScope.launch {
            _exportBusy.value = true
            val result = repository.prepareShareableInstalledApk(currentUserId)
            _exportBusy.value = false
            result.fold(
                onSuccess = { file ->
                    _events.emit(
                        StudioUiEvent.ShareFile(
                            file = file,
                            mimeType = "application/vnd.android.package-archive",
                            chooserTitle = "اشتراک‌گذاری فایل APK"
                        )
                    )
                },
                onFailure = { _events.emit(StudioUiEvent.ShowMessage(it.localizedMessage ?: "خطا در اشتراک APK.")) }
            )
        }
    }

    fun deleteExportRecord(recordId: String) {
        viewModelScope.launch {
            repository.deleteExportRecord(currentUserId, recordId)
        }
    }

    fun exportFullBackup(uri: Uri) {
        viewModelScope.launch {
            val result = repository.exportFullBackupToUri(currentUserId, uri, allProjects.value)
            result.fold(
                onSuccess = { count -> _events.emit(StudioUiEvent.ShowMessage("پشتیبان ابری ($count پروژه) ذخیره شد.")) },
                onFailure = { _events.emit(StudioUiEvent.ShowMessage(it.localizedMessage ?: "خطا در ذخیره پشتیبان.")) }
            )
        }
    }

    fun importFullBackup(uri: Uri) {
        viewModelScope.launch {
            val result = repository.importFullBackupFromUri(currentUserId, uri)
            result.fold(
                onSuccess = { count -> _events.emit(StudioUiEvent.ShowMessage("$count پروژه در فضای ابری بازیابی شد.")) },
                onFailure = { _events.emit(StudioUiEvent.ShowMessage(it.localizedMessage ?: "فرمت فایل پشتیبان معتبر نیست.")) }
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    fun setDefaultModelId(modelId: String) {
        viewModelScope.launch { repository.setDefaultModelId(modelId) }
    }

    fun setUseSignedInAccountForWeb(enabled: Boolean) {
        viewModelScope.launch { repository.setUseSignedInAccountForWeb(enabled) }
    }

    fun setDesktopModeWebView(enabled: Boolean) {
        viewModelScope.launch { repository.setDesktopModeWebView(enabled) }
    }

    fun addWebBookmark(title: String, url: String) {
        viewModelScope.launch {
            repository.addWebBookmark(title, url)
            _events.emit(StudioUiEvent.ShowMessage("میانبر پروژه ذخیره شد."))
        }
    }

    fun deleteWebBookmark(bookmarkId: String) {
        viewModelScope.launch {
            repository.deleteWebBookmark(bookmarkId)
            _events.emit(StudioUiEvent.ShowMessage("میانبر حذف شد."))
        }
    }

    fun notifyMessage(text: String) {
        viewModelScope.launch { _events.emit(StudioUiEvent.ShowMessage(text)) }
    }
}
