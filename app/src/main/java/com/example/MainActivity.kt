package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.FirebaseAiRepository
import com.example.data.export.ProjectExportEngine
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.signOutFromGoogle
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.StudioDestination
import com.example.data.model.ThemeMode
import com.example.data.repository.StudioRepository
import com.example.ui.components.GoogleAccountDialog
import com.example.ui.screens.AiStudioWebScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ExportApkScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SettingsCloudScreen
import com.example.ui.screens.WorkbenchScreen
import com.example.ui.theme.PromptStudioTheme
import com.example.ui.viewmodel.StudioUiEvent
import com.example.ui.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE any UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            PromptStudioAppRoot()
        }
    }
}

private data class NavDestinationSpec(
    val destination: StudioDestination,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val studioNavItems = listOf(
    NavDestinationSpec(
        destination = StudioDestination.AI_STUDIO,
        labelRes = R.string.nav_ai_studio,
        selectedIcon = Icons.Filled.Language,
        unselectedIcon = Icons.Outlined.Language
    ),
    NavDestinationSpec(
        destination = StudioDestination.PROJECTS,
        labelRes = R.string.nav_projects,
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder
    ),
    NavDestinationSpec(
        destination = StudioDestination.WORKBENCH,
        labelRes = R.string.nav_workbench,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    ),
    NavDestinationSpec(
        destination = StudioDestination.EXPORT,
        labelRes = R.string.nav_export,
        selectedIcon = Icons.Filled.Android,
        unselectedIcon = Icons.Outlined.Android
    ),
    NavDestinationSpec(
        destination = StudioDestination.SETTINGS,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

@Composable
private fun PromptStudioAppRoot() {
    val context = LocalContext.current
    val prefsRepo = remember(context) { UserPreferencesRepository(context.applicationContext) }
    val preferences by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = UserPreferences())
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val darkTheme = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        PromptStudioTheme(
            darkTheme = darkTheme,
            dynamicColor = preferences.dynamicColor
        ) {
            val user = currentUser
            if (user == null) {
                // Auth Gate: Feature screens & Firestore listeners are never instantiated while unauthenticated.
                AuthScreen(onAuthSuccess = {})
            } else {
                // Key composition & ViewModel by UID so sign-out or account switching tears down listeners cleanly.
                key(user.uid) {
                    AuthenticatedStudioShell(
                        currentUser = user,
                        darkTheme = darkTheme
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedStudioShell(
    currentUser: FirebaseUser,
    darkTheme: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: StudioViewModel = viewModel(
        key = currentUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val prefsRepo = UserPreferencesRepository(app)
                val exportEngine = ProjectExportEngine(app)
                val repository = StudioRepository(app, db, prefsRepo, exportEngine)
                val aiRepository = FirebaseAiRepository(app)
                StudioViewModel(repository, aiRepository, currentUser.uid)
            }
        }
    )

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val destination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val filteredProjects by viewModel.filteredProjects.collectAsStateWithLifecycle()
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val totalMessageCount by viewModel.totalMessageCount.collectAsStateWithLifecycle()
    val exportRecords by viewModel.exportRecords.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val attachedImageUri by viewModel.attachedImageUri.collectAsStateWithLifecycle()
    val exportPreviewFiles by viewModel.exportPreviewFiles.collectAsStateWithLifecycle()
    val exportBusy by viewModel.exportBusy.collectAsStateWithLifecycle()

    var showAccountDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val handleSignOut: () -> Unit = {
        signOutFromGoogle(
            credentialManager = credentialManager,
            scope = scope,
            onBeforeSignOut = { viewModel.stopGeneration() }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StudioUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is StudioUiEvent.ShareFile -> {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        event.file
                    )
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, event.chooserTitle))
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        studioNavItems.forEach { item ->
                            val selected = destination == item.destination
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.navigateTo(item.destination) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes), maxLines = 1) },
                                modifier = Modifier.testTag("nav_${item.destination.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        studioNavItems.forEach { item ->
                            val selected = destination == item.destination
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.navigateTo(item.destination) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag("rail_${item.destination.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)

                    when (destination) {
                        StudioDestination.AI_STUDIO -> {
                            AiStudioWebScreen(
                                currentUser = currentUser,
                                preferences = preferences,
                                darkTheme = darkTheme,
                                onOpenAccountDialog = { showAccountDialog = true },
                                onToggleAccountBinding = viewModel::setUseSignedInAccountForWeb,
                                onToggleDesktopMode = viewModel::setDesktopModeWebView,
                                onAddBookmark = viewModel::addWebBookmark,
                                onDeleteBookmark = viewModel::deleteWebBookmark,
                                onNotify = viewModel::notifyMessage,
                                modifier = contentModifier
                            )
                        }

                        StudioDestination.PROJECTS -> {
                            ProjectsScreen(
                                currentUser = currentUser,
                                projectsState = projectsState,
                                filteredProjects = filteredProjects,
                                totalProjectCount = allProjects.size,
                                totalMessageCount = totalMessageCount,
                                exportCount = exportRecords.size,
                                searchQuery = searchQuery,
                                selectedCategory = selectedCategory,
                                defaultModelId = preferences.defaultModelId,
                                onOpenAccountDialog = { showAccountDialog = true },
                                onSearchChange = viewModel::updateSearchQuery,
                                onCategorySelected = viewModel::selectCategory,
                                onCreateProject = viewModel::createProject,
                                onCreateFromTemplate = viewModel::createFromTemplate,
                                onOpenProject = viewModel::openProjectInWorkbench,
                                onExportProject = viewModel::openProjectInExport,
                                onTogglePin = viewModel::togglePin,
                                onDuplicateProject = viewModel::duplicateProject,
                                onDeleteProject = viewModel::deleteProject,
                                modifier = contentModifier
                            )
                        }

                        StudioDestination.WORKBENCH -> {
                            WorkbenchScreen(
                                currentUser = currentUser,
                                activeProject = activeProject,
                                allProjects = allProjects,
                                messages = activeMessages,
                                generationState = generationState,
                                attachedImageUri = attachedImageUri,
                                onOpenAccountDialog = { showAccountDialog = true },
                                onSelectProject = viewModel::selectActiveProject,
                                onUpdateSystemInstruction = viewModel::updateSystemInstruction,
                                onSaveModelConfig = viewModel::updateModelConfig,
                                onAttachImage = viewModel::setAttachedImage,
                                onSendPrompt = viewModel::runPrompt,
                                onStopGeneration = viewModel::stopGeneration,
                                onRegenerateLast = viewModel::regenerateLastResponse,
                                onClearChat = viewModel::clearActiveChat,
                                onGoToProjects = { viewModel.navigateTo(StudioDestination.PROJECTS) },
                                onGoToExport = { viewModel.navigateTo(StudioDestination.EXPORT) },
                                onCopiedNotice = viewModel::notifyMessage,
                                modifier = contentModifier
                            )
                        }

                        StudioDestination.EXPORT -> {
                            ExportApkScreen(
                                currentUser = currentUser,
                                activeProject = activeProject,
                                allProjects = allProjects,
                                previewFiles = exportPreviewFiles,
                                exportRecords = exportRecords,
                                exportBusy = exportBusy,
                                onOpenAccountDialog = { showAccountDialog = true },
                                onSelectProject = viewModel::selectActiveProject,
                                onRefreshPreview = viewModel::refreshExportPreview,
                                onSaveProjectZipToUri = viewModel::exportProjectZipToUri,
                                onShareProjectZip = viewModel::shareProjectZip,
                                onSaveInstalledApkToUri = viewModel::exportInstalledApkToUri,
                                onShareInstalledApk = viewModel::shareInstalledApk,
                                onDeleteExportRecord = viewModel::deleteExportRecord,
                                onCopiedNotice = viewModel::notifyMessage,
                                onBack = { viewModel.navigateTo(StudioDestination.PROJECTS) },
                                modifier = contentModifier
                            )
                        }

                        StudioDestination.SETTINGS -> {
                            SettingsCloudScreen(
                                currentUser = currentUser,
                                preferences = preferences,
                                projectCount = allProjects.size,
                                onSignOut = handleSignOut,
                                onThemeModeSelected = viewModel::setThemeMode,
                                onDynamicColorChanged = viewModel::setDynamicColor,
                                onDefaultModelSelected = viewModel::setDefaultModelId,
                                onExportBackupUri = viewModel::exportFullBackup,
                                onImportBackupUri = viewModel::importFullBackup,
                                onBack = { viewModel.navigateTo(StudioDestination.PROJECTS) },
                                modifier = contentModifier
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAccountDialog) {
        GoogleAccountDialog(
            user = currentUser,
            onDismiss = { showAccountDialog = false },
            onSignOut = handleSignOut
        )
    }
}
