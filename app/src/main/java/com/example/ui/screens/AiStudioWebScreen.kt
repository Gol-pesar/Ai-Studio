package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.local.AiStudioBookmark
import com.example.data.local.UserPreferences
import com.example.data.web.AiStudioWebHelper
import com.example.data.web.AiStudioWebSection
import com.example.ui.components.GoogleAccountAvatar
import com.example.ui.components.GoogleLogoGlyph
import com.google.firebase.auth.FirebaseUser

private enum class WebHubMode {
    CHROME_TAB,
    EMBEDDED_WEB
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStudioWebScreen(
    currentUser: FirebaseUser,
    preferences: UserPreferences,
    darkTheme: Boolean,
    onOpenAccountDialog: () -> Unit,
    onToggleAccountBinding: (Boolean) -> Unit,
    onToggleDesktopMode: (Boolean) -> Unit,
    onAddBookmark: (String, String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val toolbarColorArgb = MaterialTheme.colorScheme.surface.toArgb()

    var mode by remember { mutableStateOf(WebHubMode.CHROME_TAB) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var embeddedTargetUrl by remember {
        mutableStateOf(
            AiStudioWebHelper.buildAccountAwareUrl(
                AiStudioWebSection.APPS.url,
                currentUser.email,
                preferences.useSignedInAccountForWeb
            )
        )
    }

    val openInCustomTab: (String) -> Unit = { rawUrl ->
        val resolved = AiStudioWebHelper.buildAccountAwareUrl(
            baseUrl = rawUrl,
            accountEmail = currentUser.email,
            useSignedInAccount = preferences.useSignedInAccountForWeb
        )
        val launched = AiStudioWebHelper.launchCustomTab(
            context = context,
            url = resolved,
            toolbarColorArgb = toolbarColorArgb,
            darkTheme = darkTheme
        )
        if (!launched) {
            onNotify("مرورگری برای باز کردن صفحه یافت نشد.")
        }
    }

    val openInEmbedded: (String) -> Unit = { rawUrl ->
        embeddedTargetUrl = AiStudioWebHelper.buildAccountAwareUrl(
            baseUrl = rawUrl,
            accountEmail = currentUser.email,
            useSignedInAccount = preferences.useSignedInAccountForWeb
        )
        mode = WebHubMode.EMBEDDED_WEB
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top Google-style App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                GoogleLogoGlyph(size = 24.dp)
                Column {
                    Text(
                        text = stringResource(R.string.web_hub_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentUser.email ?: "aistudio.google.com",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            GoogleAccountAvatar(
                user = currentUser,
                onClick = onOpenAccountDialog,
                size = 38.dp
            )
        }

        // Mode Switcher: Chrome Custom Tab (Safe Full Login) vs Embedded Mobile WebView
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            SegmentedButton(
                selected = mode == WebHubMode.CHROME_TAB,
                onClick = { mode = WebHubMode.CHROME_TAB },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("web_mode_chrome_tab")
            ) {
                Text(
                    text = stringResource(R.string.web_mode_fast),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SegmentedButton(
                selected = mode == WebHubMode.EMBEDDED_WEB,
                onClick = { mode = WebHubMode.EMBEDDED_WEB },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("web_mode_embedded")
            ) {
                Text(
                    text = stringResource(R.string.web_mode_embedded),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        when (mode) {
            WebHubMode.CHROME_TAB -> {
                AiStudioLauncherDashboard(
                    currentUser = currentUser,
                    preferences = preferences,
                    onOpenCustomTab = openInCustomTab,
                    onOpenEmbedded = openInEmbedded,
                    onToggleAccountBinding = onToggleAccountBinding,
                    onAddBookmarkClick = { showAddBookmarkDialog = true },
                    onDeleteBookmark = onDeleteBookmark,
                    modifier = Modifier.weight(1f)
                )
            }

            WebHubMode.EMBEDDED_WEB -> {
                AiStudioEmbeddedWorkspace(
                    initialUrl = embeddedTargetUrl,
                    accountEmail = currentUser.email,
                    useSignedInAccount = preferences.useSignedInAccountForWeb,
                    desktopMode = preferences.desktopModeWebView,
                    onToggleDesktopMode = onToggleDesktopMode,
                    onOpenInCustomTab = openInCustomTab,
                    onSwitchToLauncher = { mode = WebHubMode.CHROME_TAB },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showAddBookmarkDialog) {
        AddBookmarkDialog(
            onDismiss = { showAddBookmarkDialog = false },
            onSave = { title, rawUrl ->
                val sanitized = AiStudioWebHelper.sanitizeAiStudioUrl(rawUrl)
                if (sanitized == null) {
                    onNotify("آدرس واردشده معتبر نیست.")
                } else {
                    onAddBookmark(title, sanitized)
                    showAddBookmarkDialog = false
                }
            }
        )
    }
}

@Composable
private fun AiStudioLauncherDashboard(
    currentUser: FirebaseUser,
    preferences: UserPreferences,
    onOpenCustomTab: (String) -> Unit,
    onOpenEmbedded: (String) -> Unit,
    onToggleAccountBinding: (Boolean) -> Unit,
    onAddBookmarkClick: () -> Unit,
    onDeleteBookmark: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sectionPairs = remember { AiStudioWebSection.entries.chunked(2) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primary Hero Action Card -> Direct aistudio.google.com/apps
        item {
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Apps,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "aistudio.google.com",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(R.string.web_hub_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.web_hero_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onOpenCustomTab(AiStudioWebSection.APPS.url) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_aistudio_apps_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.web_hero_cta),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Account Binding Card (?authuser=email)
        item {
            OutlinedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.web_account_binding_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = currentUser.email ?: stringResource(R.string.web_account_binding_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = preferences.useSignedInAccountForWeb,
                        onCheckedChange = onToggleAccountBinding,
                        modifier = Modifier.testTag("toggle_authuser_binding")
                    )
                }
            }
        }

        // Official aistudio.google.com Sections Grid
        item {
            Text(
                text = stringResource(R.string.web_sections_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(sectionPairs) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { section ->
                    SectionActionCard(
                        section = section,
                        onOpenTab = { onOpenCustomTab(section.url) },
                        onOpenEmbedded = { onOpenEmbedded(section.url) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Pinned User Project Shortcuts
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.web_bookmarks_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                FilledTonalButton(
                    onClick = onAddBookmarkClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_web_bookmark_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.web_bookmarks_add),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        if (preferences.webBookmarks.isEmpty()) {
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.web_bookmarks_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(preferences.webBookmarks, key = { it.id }) { bookmark ->
                BookmarkCard(
                    bookmark = bookmark,
                    onOpen = { onOpenCustomTab(bookmark.url) },
                    onDelete = { onDeleteBookmark(bookmark.id) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionActionCard(
    section: AiStudioWebSection,
    onOpenTab: () -> Unit,
    onOpenEmbedded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (section) {
        AiStudioWebSection.APPS -> Icons.Filled.Apps
        AiStudioWebSection.LIBRARY -> Icons.Filled.FolderOpen
        AiStudioWebSection.NEW_CHAT -> Icons.Filled.AutoAwesome
        AiStudioWebSection.API_KEYS -> Icons.Filled.Key
        AiStudioWebSection.USAGE -> Icons.Filled.BarChart
        AiStudioWebSection.DRIVE_FOLDER -> Icons.Filled.Cloud
        AiStudioWebSection.GOOGLE_ACCOUNT -> Icons.Filled.AccountCircle
    }

    Card(
        onClick = onOpenTab,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag("aistudio_section_${section.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onOpenEmbedded,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = stringResource(R.string.web_mode_embedded),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = section.titleFa,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = section.subtitleFa,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: AiStudioBookmark,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = bookmark.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cd_web_delete_bookmark),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AiStudioEmbeddedWorkspace(
    initialUrl: String,
    accountEmail: String?,
    useSignedInAccount: Boolean,
    desktopMode: Boolean,
    onToggleDesktopMode: (Boolean) -> Unit,
    onOpenInCustomTab: (String) -> Unit,
    onSwitchToLauncher: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var lastStudioUrl by remember { mutableStateOf(initialUrl) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var authRedirectDetected by remember { mutableStateOf(false) }
    var reloadNonce by remember { mutableIntStateOf(0) }

    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        pendingFileCallback?.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
        pendingFileCallback = null
    }

    BackHandler(enabled = true) {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onSwitchToLauncher()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    LaunchedEffect(initialUrl) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (wv.url != initialUrl) {
            authRedirectDetected = false
            lastStudioUrl = initialUrl
            wv.loadUrl(initialUrl)
        }
    }

    LaunchedEffect(desktopMode, reloadNonce) {
        val wv = webViewRef ?: return@LaunchedEffect
        val settings = wv.settings
        settings.userAgentString = if (desktopMode) {
            AiStudioWebHelper.DESKTOP_USER_AGENT
        } else {
            WebSettings.getDefaultUserAgent(context)
        }
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = desktopMode
        if (reloadNonce > 0) {
            wv.reload()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Quick route chips for 1-tap switching across aistudio.google.com sections
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiStudioWebSection.entries.filter { it.isPrimaryStudioRoute }.forEach { section ->
                val isSelected = currentUrl.contains("/${section.id}", ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val target = AiStudioWebHelper.buildAccountAwareUrl(
                            section.url,
                            accountEmail,
                            useSignedInAccount
                        )
                        authRedirectDetected = false
                        lastStudioUrl = target
                        webViewRef?.loadUrl(target)
                    },
                    label = { Text(section.shortLabelFa) }
                )
            }
        }

        // Compact native Android browser controls
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.cd_web_back)
                        )
                    }
                    IconButton(
                        onClick = { webViewRef?.goForward() },
                        enabled = canGoForward
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_web_forward)
                        )
                    }
                    IconButton(onClick = { reloadNonce++ }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.cd_web_reload)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            onToggleDesktopMode(!desktopMode)
                            reloadNonce++
                        }
                    ) {
                        Icon(
                            imageVector = if (desktopMode) Icons.Filled.DesktopWindows else Icons.Filled.PhoneAndroid,
                            contentDescription = stringResource(R.string.cd_web_desktop),
                            tint = if (desktopMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { onOpenInCustomTab(lastStudioUrl) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("embedded_open_custom_tab_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.cd_web_open_tab),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Chrome Tab",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        if (progress > 0f && progress < 1f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (authRedirectDetected) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.web_auth_guard_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.web_auth_guard_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Button(
                        onClick = { onOpenInCustomTab(lastStudioUrl) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.web_auth_guard_action))
                    }
                }
            }
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = desktopMode
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (desktopMode) {
                        settings.userAgentString = AiStudioWebHelper.DESKTOP_USER_AGENT
                    }

                    setDownloadListener { downloadUrl, _, _, _, _ ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            ctx.startActivity(intent)
                        } catch (_: Exception) {
                            onOpenInCustomTab(downloadUrl)
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress / 100f
                        }

                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            pendingFileCallback?.onReceiveValue(null)
                            pendingFileCallback = filePathCallback
                            return try {
                                fileChooserLauncher.launch(arrayOf("*/*"))
                                true
                            } catch (_: Exception) {
                                pendingFileCallback = null
                                false
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val nextUrl = request?.url?.toString() ?: return false
                            if (AiStudioWebHelper.isGoogleAuthUrl(nextUrl)) {
                                authRedirectDetected = true
                                return false
                            }
                            if (nextUrl.contains(AiStudioWebHelper.BASE_HOST, ignoreCase = true)) {
                                lastStudioUrl = nextUrl
                                authRedirectDetected = false
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (!url.isNullOrBlank()) {
                                currentUrl = url
                                if (AiStudioWebHelper.isGoogleAuthUrl(url)) {
                                    authRedirectDetected = true
                                } else if (url.contains(AiStudioWebHelper.BASE_HOST, ignoreCase = true)) {
                                    lastStudioUrl = url
                                    authRedirectDetected = false
                                    if (!desktopMode) {
                                        view?.evaluateJavascript(AiStudioWebHelper.MOBILE_POLISH_SCRIPT, null)
                                    }
                                }
                            }
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }
                    }

                    webViewRef = this
                    loadUrl(initialUrl)
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("aistudio_embedded_webview")
        )
    }
}

@Composable
private fun AddBookmarkDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://aistudio.google.com/apps") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.web_bookmark_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.web_bookmark_title_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bookmark_title_input")
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.web_bookmark_url_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bookmark_url_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title.ifBlank { "پروژه AI Studio" }, url) },
                enabled = url.isNotBlank(),
                modifier = Modifier.testTag("bookmark_save_button")
            ) {
                Text(stringResource(R.string.web_bookmark_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.projects_action_cancel))
            }
        }
    )
}
