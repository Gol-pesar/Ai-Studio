package com.example.data.web

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

enum class AiStudioWebSection(
    val id: String,
    val titleFa: String,
    val subtitleFa: String,
    val shortLabelFa: String,
    val url: String,
    val isPrimaryStudioRoute: Boolean = true
) {
    APPS(
        id = "apps",
        titleFa = "پروژه‌های Build",
        subtitleFa = "مشاهده، ویرایش و مدیریت اپلیکیشن‌های ساخته‌شده در AI Studio",
        shortLabelFa = "پروژه‌ها",
        url = "https://aistudio.google.com/apps"
    ),
    LIBRARY(
        id = "library",
        titleFa = "کتابخانه پرامپت‌ها",
        subtitleFa = "دسترسی به تمام پرامپت‌ها و گفتگوهای ذخیره‌شده شما",
        shortLabelFa = "کتابخانه",
        url = "https://aistudio.google.com/library"
    ),
    NEW_CHAT(
        id = "new_chat",
        titleFa = "پرامپت جدید",
        subtitleFa = "شروع مستقیم گفتگوی جدید روی سایت aistudio.google.com",
        shortLabelFa = "جدید",
        url = "https://aistudio.google.com/prompts/new_chat"
    ),
    API_KEYS(
        id = "apikey",
        titleFa = "کلیدهای API",
        subtitleFa = "ساخت، بررسی و مدیریت Gemini API Keys پروژه‌ها",
        shortLabelFa = "API Keys",
        url = "https://aistudio.google.com/apikey"
    ),
    USAGE(
        id = "usage",
        titleFa = "مصرف و سهمیه",
        subtitleFa = "بررسی آمار مصرف، محدودیت‌ها و وضعیت پلن حساب",
        shortLabelFa = "مصرف",
        url = "https://aistudio.google.com/usage"
    ),
    DRIVE_FOLDER(
        id = "drive",
        titleFa = "پوشه در Google Drive",
        subtitleFa = "مدیریت فایل‌ها و پرامپت‌های ذخیره‌شده AI Studio در درایو",
        shortLabelFa = "درایو",
        url = "https://drive.google.com/drive/search?q=Google%20AI%20Studio",
        isPrimaryStudioRoute = false
    ),
    GOOGLE_ACCOUNT(
        id = "account",
        titleFa = "مدیریت حساب گوگل",
        subtitleFa = "تنظیمات امنیت، تعویض حساب و دسترسی‌های Google Account",
        shortLabelFa = "حساب گوگل",
        url = "https://myaccount.google.com",
        isPrimaryStudioRoute = false
    )
}

object AiStudioWebHelper {
    const val BASE_HOST = "aistudio.google.com"
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val googleAccountAwareHosts = setOf(
        "aistudio.google.com",
        "drive.google.com",
        "myaccount.google.com"
    )

    fun buildAccountAwareUrl(
        baseUrl: String,
        accountEmail: String?,
        useSignedInAccount: Boolean
    ): String {
        val trimmed = baseUrl.trim()
        if (!useSignedInAccount || accountEmail.isNullOrBlank()) return trimmed
        return try {
            val uri = Uri.parse(trimmed)
            val host = uri.host?.lowercase().orEmpty()
            if (host !in googleAccountAwareHosts) return trimmed
            if (uri.getQueryParameter("authuser") != null) return trimmed
            uri.buildUpon()
                .appendQueryParameter("authuser", accountEmail.trim())
                .build()
                .toString()
        } catch (_: Exception) {
            trimmed
        }
    }

    fun sanitizeAiStudioUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val normalized = when {
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed
            trimmed.startsWith("/") -> "https://$BASE_HOST$trimmed"
            trimmed.startsWith(BASE_HOST) -> "https://$trimmed"
            else -> "https://$BASE_HOST/$trimmed"
        }
        return try {
            val uri = Uri.parse(normalized)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            if (scheme != "https" && scheme != "http") return null
            if (host.isNullOrBlank()) return null
            uri.toString()
        } catch (_: Exception) {
            null
        }
    }

    fun isGoogleAuthUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val host = Uri.parse(url).host?.lowercase().orEmpty()
            host == "accounts.google.com" || host.endsWith(".accounts.google.com")
        } catch (_: Exception) {
            false
        }
    }

    fun launchCustomTab(
        context: Context,
        url: String,
        toolbarColorArgb: Int,
        darkTheme: Boolean
    ): Boolean {
        val uri = Uri.parse(url)
        val colorSchemeParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColorArgb)
            .setNavigationBarColor(toolbarColorArgb)
            .build()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colorSchemeParams)
            .setColorScheme(
                if (darkTheme) CustomTabsIntent.COLOR_SCHEME_DARK
                else CustomTabsIntent.COLOR_SCHEME_LIGHT
            )
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .build()

        return try {
            customTabsIntent.launchUrl(context, uri)
            true
        } catch (_: ActivityNotFoundException) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    // Safe responsive polish injected into embedded WebView for touch-friendly Android viewing
    val MOBILE_POLISH_SCRIPT: String = """
        (function() {
          try {
            if (document.getElementById('prompt-studio-mobile-style')) return;
            var style = document.createElement('style');
            style.id = 'prompt-studio-mobile-style';
            style.textContent = `
              html, body {
                max-width: 100vw !important;
                overflow-x: hidden !important;
                -webkit-tap-highlight-color: rgba(26, 115, 232, 0.16);
              }
              button, a, [role="button"] {
                min-height: 40px;
              }
              textarea, input {
                font-size: 16px !important;
              }
            `;
            document.head.appendChild(style);
          } catch (e) {}
        })();
    """.trimIndent()
}
