package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.CloudMessage
import com.example.data.model.CloudProject
import com.example.data.model.GeminiModelCatalog
import com.example.ui.components.GoogleAccountAvatar
import com.example.ui.components.ModelBadgePill
import com.example.ui.theme.LocalStudioColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.viewmodel.GenerationUiState
import com.google.firebase.auth.FirebaseUser
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchScreen(
    currentUser: FirebaseUser,
    activeProject: CloudProject?,
    allProjects: List<CloudProject>,
    messages: List<CloudMessage>,
    generationState: GenerationUiState,
    attachedImageUri: String?,
    onOpenAccountDialog: () -> Unit,
    onSelectProject: (String) -> Unit,
    onUpdateSystemInstruction: (String) -> Unit,
    onSaveModelConfig: (String, Float, Float, Int, Int, Boolean, String, Boolean) -> Unit,
    onAttachImage: (String?) -> Unit,
    onSendPrompt: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerateLast: () -> Unit,
    onClearChat: () -> Unit,
    onGoToProjects: () -> Unit,
    onGoToExport: () -> Unit,
    onCopiedNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onGoToProjects() }

    if (activeProject == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = stringResource(R.string.workbench_no_project),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick = onGoToProjects,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.testTag("workbench_go_projects_button")
                    ) {
                        Text(stringResource(R.string.workbench_go_projects))
                    }
                }
            }
        }
        return
    }

    var promptText by remember(activeProject.id) { mutableStateOf("") }
    var systemText by remember(activeProject.id, activeProject.systemInstruction) {
        mutableStateOf(activeProject.systemInstruction)
    }
    var systemExpanded by remember(activeProject.id) { mutableStateOf(false) }
    var projectDropdownExpanded by remember { mutableStateOf(false) }
    var showTuningSheet by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val copiedLabel = stringResource(R.string.workbench_copied)
    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> onAttachImage(uri?.toString()) }
    )

    val totalTokens = remember(messages, systemText) {
        messages.sumOf { it.estimatedTokens } + (systemText.length / 4L)
    }

    LaunchedEffect(messages.size, generationState) {
        val targetIndex = messages.size + if (generationState is GenerationUiState.Streaming) 1 else 0
        if (targetIndex > 0) {
            listState.animateScrollToItem(targetIndex - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Google AI Studio Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { projectDropdownExpanded = true }
                                .testTag("workbench_project_selector"),
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = activeProject.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = projectDropdownExpanded,
                            onDismissRequest = { projectDropdownExpanded = false }
                        ) {
                            allProjects.forEach { proj ->
                                DropdownMenuItem(
                                    text = { Text(proj.title) },
                                    onClick = {
                                        projectDropdownExpanded = false
                                        onSelectProject(proj.id)
                                    }
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showTuningSheet = true },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("workbench_tuning_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = stringResource(R.string.cd_tuning_button),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onGoToExport,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("workbench_export_shortcut")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Android,
                                contentDescription = "خروجی پروژه APK",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (messages.isNotEmpty()) {
                            IconButton(
                                onClick = onClearChat,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("workbench_clear_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteSweep,
                                    contentDescription = stringResource(R.string.workbench_clear_chat),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        GoogleAccountAvatar(
                            user = currentUser,
                            onClick = onOpenAccountDialog,
                            size = 32.dp
                        )
                    }
                }

                // Model & Run Settings Ribbon (like Google AI Studio)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ModelBadgePill(
                            modelId = activeProject.modelId,
                            modifier = Modifier.clickable { showTuningSheet = true }
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.clickable { showTuningSheet = true }
                            ) {
                                Text(
                                    text = "T=${"%.2f".format(activeProject.temperature)}",
                                    style = MonoMetricStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (activeProject.jsonModeEnabled) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "JSON",
                                    style = MonoMetricStyle,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = "$totalTokens tok",
                            style = MonoMetricStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Collapsible System Instructions Panel
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { systemExpanded = !systemExpanded }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("system_instruction_toggle"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.workbench_system_instruction),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Icon(
                        imageVector = if (systemExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = systemExpanded) {
                    Column(
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = systemText,
                            onValueChange = { systemText = it.take(12000) },
                            placeholder = { Text(stringResource(R.string.workbench_system_hint)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("system_instruction_input"),
                            minLines = 2,
                            maxLines = 5,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onUpdateSystemInstruction(systemText)
                                    systemExpanded = false
                                },
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.testTag("save_system_instruction_button")
                            ) {
                                Text("ذخیره دستور سیستمی")
                            }
                        }
                    }
                }
            }
        }

        // Messages Thread (Clean Google Gemini / AI Studio Stream)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty() && generationState is GenerationUiState.Idle) {
                item(key = "workbench_empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = stringResource(R.string.workbench_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.workbench_empty_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                GeminiTurnCard(
                    message = msg,
                    isLastModelMessage = msg.role == "model" && msg.id == messages.lastOrNull()?.id,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(msg.content))
                        onCopiedNotice(copiedLabel)
                    },
                    onRegenerate = onRegenerateLast
                )
            }

            if (generationState is GenerationUiState.Streaming) {
                item(key = "streaming_bubble") {
                    StreamingGeminiCard(
                        modelId = activeProject.modelId,
                        partialText = generationState.partialText
                    )
                }
            }

            if (generationState is GenerationUiState.Error) {
                item(key = "error_bubble") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = generationState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }

        // Google Gemini Floating Pill Prompt Composer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (attachedImageUri != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Box {
                        AsyncImage(
                            model = attachedImageUri,
                            contentDescription = "تصویر پیوست‌شده",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp)
                                .clickable { onAttachImage(null) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_remove_image),
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(3.dp)
                            )
                        }
                    }
                    Text(
                        text = "تصویر برای تحلیل چندرسانه‌ای آماده ارسال است",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("workbench_attach_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = stringResource(R.string.cd_attach_image),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("workbench_prompt_input"),
                        placeholder = { Text(stringResource(R.string.workbench_prompt_hint)) },
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    val isStreaming = generationState is GenerationUiState.Streaming
                    FilledIconButton(
                        onClick = {
                            if (isStreaming) {
                                onStopGeneration()
                            } else {
                                val toSend = promptText
                                promptText = ""
                                onSendPrompt(toSend)
                            }
                        },
                        enabled = isStreaming || promptText.isNotBlank() || attachedImageUri != null,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isStreaming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = if (isStreaming) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("workbench_send_button")
                    ) {
                        Icon(
                            imageVector = if (isStreaming) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(
                                if (isStreaming) R.string.cd_stop_generation else R.string.cd_send_prompt
                            )
                        )
                    }
                }
            }
        }
    }

    if (showTuningSheet) {
        ModelTuningBottomSheet(
            project = activeProject,
            onDismiss = { showTuningSheet = false },
            onSave = { modelId, temp, topP, topK, maxTok, jsonMode, schema, safety ->
                onSaveModelConfig(modelId, temp, topP, topK, maxTok, jsonMode, schema, safety)
                showTuningSheet = false
            }
        )
    }
}

@Composable
private fun GeminiTurnCard(
    message: CloudMessage,
    isLastModelMessage: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit
) {
    val isUser = message.role == "user"
    val studioColors = LocalStudioColors.current
    val looksLikeJson = message.content.trimStart().startsWith("{")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message_card_${message.id}"),
        shape = RoundedCornerShape(20.dp),
        color = if (isUser) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (isUser) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isUser) Icons.Rounded.Person else Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = if (isUser) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isUser) "شما" else message.modelId,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (message.hasImageAttachment) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = "دارای تصویر",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLastModelMessage) {
                        IconButton(onClick = onRegenerate, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.workbench_regenerate),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.workbench_copy),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (looksLikeJson) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = studioColors.codeBackground
                    ) {
                        Text(
                            text = message.content,
                            style = MonoCodeStyle,
                            color = studioColors.codeText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun StreamingGeminiCard(
    modelId: String,
    partialText: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "$modelId • در حال پاسخ‌گویی زنده…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(99.dp))
            )
            if (partialText.isNotBlank()) {
                Text(
                    text = partialText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelTuningBottomSheet(
    project: CloudProject,
    onDismiss: () -> Unit,
    onSave: (String, Float, Float, Int, Int, Boolean, String, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedModelId by remember { mutableStateOf(project.modelId) }
    var temperature by remember { mutableFloatStateOf(project.temperature.toFloat()) }
    var topP by remember { mutableFloatStateOf(project.topP.toFloat()) }
    var topK by remember { mutableIntStateOf(project.topK.toInt()) }
    var maxTokens by remember { mutableIntStateOf(project.maxOutputTokens.toInt()) }
    var jsonMode by remember { mutableStateOf(project.jsonModeEnabled) }
    var jsonSchema by remember { mutableStateOf(project.jsonSchema) }
    var safetyBalanced by remember { mutableStateOf(project.safetyBalanced) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.tuning_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = stringResource(R.string.tuning_model_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            GeminiModelCatalog.supportedModels.forEach { spec ->
                val selected = selectedModelId == spec.id
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { selectedModelId = spec.id },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(selected = selected, onClick = { selectedModelId = spec.id })
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(spec.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    spec.contextWindow,
                                    style = MonoMetricStyle,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                spec.tierLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                spec.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            SliderSettingRow(
                label = stringResource(R.string.tuning_temperature),
                valueDisplay = "%.2f".format(temperature),
                value = temperature,
                valueRange = 0f..2f,
                onValueChange = { temperature = it }
            )

            SliderSettingRow(
                label = stringResource(R.string.tuning_top_p),
                valueDisplay = "%.2f".format(topP),
                value = topP,
                valueRange = 0.05f..1f,
                onValueChange = { topP = it }
            )

            SliderSettingRow(
                label = stringResource(R.string.tuning_top_k),
                valueDisplay = topK.toString(),
                value = topK.toFloat(),
                valueRange = 1f..100f,
                onValueChange = { topK = it.roundToInt() }
            )

            SliderSettingRow(
                label = stringResource(R.string.tuning_max_tokens),
                valueDisplay = maxTokens.toString(),
                value = maxTokens.toFloat(),
                valueRange = 256f..8192f,
                onValueChange = { maxTokens = (it / 256f).roundToInt() * 256 }
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.tuning_json_mode), style = MaterialTheme.typography.titleSmall)
                Switch(checked = jsonMode, onCheckedChange = { jsonMode = it })
            }

            AnimatedVisibility(visible = jsonMode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = jsonSchema,
                        onValueChange = { jsonSchema = it.take(12000) },
                        label = { Text(stringResource(R.string.tuning_json_schema_label)) },
                        textStyle = MonoCodeStyle,
                        minLines = 4,
                        maxLines = 7,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.tuning_safety_balanced), style = MaterialTheme.typography.titleSmall)
                Switch(checked = safetyBalanced, onCheckedChange = { safetyBalanced = it })
            }

            Button(
                onClick = {
                    onSave(
                        selectedModelId,
                        temperature,
                        topP,
                        topK,
                        maxTokens,
                        jsonMode,
                        jsonSchema,
                        safetyBalanced
                    )
                },
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("tuning_save_button")
            ) {
                Text(stringResource(R.string.tuning_save))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SliderSettingRow(
    label: String,
    valueDisplay: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = valueDisplay,
                    style = MonoMetricStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}
