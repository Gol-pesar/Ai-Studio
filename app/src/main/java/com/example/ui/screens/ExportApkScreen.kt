package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.export.AndroidExportConfig
import com.example.data.export.GeneratedAndroidProjectFiles
import com.example.data.model.CloudExportRecord
import com.example.data.model.CloudProject
import com.example.ui.components.CodePreviewContainer
import com.example.ui.components.GoogleAccountAvatar
import com.example.ui.components.ModelBadgePill
import com.example.ui.components.formatBytesReadable
import com.example.ui.components.formatTimestampPersian
import com.example.ui.theme.LocalStudioColors
import com.example.ui.theme.MonoMetricStyle
import com.google.firebase.auth.FirebaseUser

@Composable
fun ExportApkScreen(
    currentUser: FirebaseUser,
    activeProject: CloudProject?,
    allProjects: List<CloudProject>,
    previewFiles: GeneratedAndroidProjectFiles?,
    exportRecords: List<CloudExportRecord>,
    exportBusy: Boolean,
    onOpenAccountDialog: () -> Unit,
    onSelectProject: (String) -> Unit,
    onRefreshPreview: (AndroidExportConfig) -> Unit,
    onSaveProjectZipToUri: (Uri, AndroidExportConfig, String) -> Unit,
    onShareProjectZip: (AndroidExportConfig) -> Unit,
    onSaveInstalledApkToUri: (Uri, String) -> Unit,
    onShareInstalledApk: () -> Unit,
    onDeleteExportRecord: (String) -> Unit,
    onCopiedNotice: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val studioColors = LocalStudioColors.current
    val clipboardManager = LocalClipboardManager.current
    val copiedLabel = stringResource(R.string.workbench_copied)

    var appDisplayName by remember(activeProject?.id) {
        mutableStateOf(activeProject?.title ?: "Prompt Studio App")
    }
    var packageName by remember(activeProject?.id) {
        mutableStateOf("com.promptstudio.generated")
    }
    var versionName by remember { mutableStateOf("1.0.0") }
    var versionCodeText by remember { mutableStateOf("1") }
    var selectedPreviewTab by remember { mutableIntStateOf(0) }

    val currentConfig = remember(appDisplayName, packageName, versionName, versionCodeText) {
        AndroidExportConfig(
            appDisplayName = appDisplayName.ifBlank { "Prompt Studio App" },
            packageName = packageName.ifBlank { "com.promptstudio.app" },
            versionName = versionName.ifBlank { "1.0.0" },
            versionCode = versionCodeText.toIntOrNull()?.coerceAtLeast(1) ?: 1
        )
    }

    LaunchedEffect(activeProject?.id, currentConfig) {
        if (activeProject != null) {
            onRefreshPreview(currentConfig)
        }
    }

    val pendingZipFileName = remember(appDisplayName) {
        val slug = appDisplayName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "studio_project" }
        "${slug}_android_apk_project.zip"
    }

    val zipSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            onSaveProjectZipToUri(uri, currentConfig, pendingZipFileName)
        }
    }

    val apkSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        if (uri != null) {
            onSaveInstalledApkToUri(uri, "PromptStudio_native.apk")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.export_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.export_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GoogleAccountAvatar(user = currentUser, onClick = onOpenAccountDialog, size = 34.dp)
            }
        }

        if (exportBusy) {
            item(key = "busy_indicator") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        // Card 1: Direct Installed APK Extractor
        item(key = "direct_apk_card") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Android,
                            contentDescription = null,
                            tint = studioColors.success,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.export_extract_apk_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "استخراج مستقیم فایل نصب (.apk)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.export_extract_apk_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { apkSaverLauncher.launch("PromptStudio_native.apk") },
                            enabled = !exportBusy,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_save_apk_button")
                        ) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.export_save_apk))
                        }
                        OutlinedButton(
                            onClick = onShareInstalledApk,
                            enabled = !exportBusy,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.testTag("export_share_apk_button")
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.export_share_apk))
                        }
                    }
                }
            }
        }

        // Card 2: Prompt Project -> Native Android Gradle Project Packager
        item(key = "project_packager_card") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "بسته‌بندی پروژه ابری برای بیلد APK",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (allProjects.isEmpty()) {
                        Text(
                            text = "ابتدا در بخش پروژه‌ها یک پروژه بسازید تا کد کاتلین متصل به Gemini و بسته Gradle آن تولید شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.export_select_project),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(allProjects, key = { it.id }) { proj ->
                                FilterChip(
                                    selected = activeProject?.id == proj.id,
                                    onClick = { onSelectProject(proj.id) },
                                    label = { Text(proj.title) }
                                )
                            }
                        }

                        if (activeProject != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ModelBadgePill(modelId = activeProject.modelId)
                                Text(
                                    text = if (activeProject.jsonModeEnabled) "حالت JSON فعال" else "حالت متنی/چت",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedTextField(
                                value = appDisplayName,
                                onValueChange = { appDisplayName = it },
                                label = { Text(stringResource(R.string.export_app_name_label)) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("export_app_name_input")
                            )

                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                OutlinedTextField(
                                    value = packageName,
                                    onValueChange = { packageName = it },
                                    label = { Text(stringResource(R.string.export_package_label)) },
                                    textStyle = MonoMetricStyle,
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("export_package_input")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    OutlinedTextField(
                                        value = versionName,
                                        onValueChange = { versionName = it },
                                        label = { Text(stringResource(R.string.export_version_name_label)) },
                                        textStyle = MonoMetricStyle,
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = versionCodeText,
                                        onValueChange = { versionCodeText = it.filter(Char::isDigit) },
                                        label = { Text(stringResource(R.string.export_version_code_label)) },
                                        textStyle = MonoMetricStyle,
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(0.7f)
                                    )
                                }
                            }

                            Button(
                                onClick = { zipSaverLauncher.launch(pendingZipFileName) },
                                enabled = !exportBusy,
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("export_save_zip_button")
                            ) {
                                Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.export_save_zip))
                            }

                            OutlinedButton(
                                onClick = { onShareProjectZip(currentConfig) },
                                enabled = !exportBusy,
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("export_share_zip_button")
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.export_share_zip))
                            }
                        }
                    }
                }
            }
        }

        if (previewFiles != null) {
            item(key = "code_preview_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.export_code_preview),
                        style = MaterialTheme.typography.titleMedium
                    )

                    val tabs = listOf("MainActivity.kt", "build.gradle.kts", "build_apk.sh")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        tabs.forEachIndexed { index, title ->
                            SegmentedButton(
                                selected = selectedPreviewTab == index,
                                onClick = { selectedPreviewTab = index },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                                label = { Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                            )
                        }
                    }

                    val (fileTitle, codeText) = when (selectedPreviewTab) {
                        0 -> "app/src/main/java/.../MainActivity.kt" to previewFiles.mainActivityKt
                        1 -> "app/build.gradle.kts" to previewFiles.appBuildGradleKts
                        else -> "build_apk.sh" to previewFiles.buildApkScript
                    }

                    CodePreviewContainer(
                        title = fileTitle,
                        code = codeText,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(codeText))
                            onCopiedNotice(copiedLabel)
                        }
                    )
                }
            }
        }

        item(key = "build_guide") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.export_build_guide_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.export_build_guide_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "history_title") {
            Text(
                text = stringResource(R.string.export_history_title),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (exportRecords.isEmpty()) {
            item(key = "history_empty") {
                Text(
                    text = stringResource(R.string.export_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(exportRecords, key = { it.id }) { record ->
                CloudExportRecordRow(
                    record = record,
                    onDelete = { onDeleteExportRecord(record.id) }
                )
            }
        }
    }
}

@Composable
private fun CloudExportRecordRow(
    record: CloudExportRecord,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = record.projectTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = "${record.fileName} • ${formatBytesReadable(record.sizeBytes)}",
                        style = MonoMetricStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = formatTimestampPersian(record.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "حذف سابقه",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
