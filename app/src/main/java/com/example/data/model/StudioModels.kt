package com.example.data.model

enum class StudioDestination(val route: String) {
    AI_STUDIO("ai_studio"),
    PROJECTS("projects"),
    WORKBENCH("workbench"),
    EXPORT("export"),
    SETTINGS("settings")
}

enum class ProjectCategory(val id: String, val persianLabel: String) {
    ALL("ALL", "همه"),
    CHAT("CHAT", "گفتگو و دستیار"),
    STRUCTURED("STRUCTURED", "خروجی JSON"),
    VISION("VISION", "چندرسانه‌ای"),
    CODE("CODE", "برنامه‌نویسی");

    companion object {
        fun fromId(id: String): ProjectCategory =
            entries.firstOrNull { it.id == id } ?: CHAT
    }
}

enum class ThemeMode(val id: String, val persianLabel: String) {
    SYSTEM("SYSTEM", "خودکار سیستم"),
    DARK("DARK", "تاریک استودیو"),
    LIGHT("LIGHT", "روشن");

    companion object {
        fun fromId(id: String): ThemeMode =
            entries.firstOrNull { it.id == id } ?: DARK
    }
}

data class GeminiModelSpec(
    val id: String,
    val displayName: String,
    val tierLabel: String,
    val description: String,
    val contextWindow: String,
    val supportsVision: Boolean = true,
    val supportsJsonSchema: Boolean = true
)

object GeminiModelCatalog {
    const val DEFAULT_MODEL_ID = "gemini-3.8-flash"

    val supportedModels: List<GeminiModelSpec> = listOf(
        GeminiModelSpec(
            id = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash",
            tierLabel = "پیشنهادی • سریع و متعادل",
            description = "مدل همه‌کاره برای چت، تحلیل تصویر، تولید کد و خروجی ساختاری JSON با تأخیر بسیار کم.",
            contextWindow = "1M tokens"
        ),
        GeminiModelSpec(
            id = "gemini-4-argon",
            displayName = "Gemini 4 Argon",
            tierLabel = "استدلال عمیق و معماری",
            description = "قوی‌ترین مدل برای مسائل پیچیده مهندسی، معماری نرم‌افزار، ریاضیات و کدنویسی پیشرفته.",
            contextWindow = "2M tokens"
        ),
        GeminiModelSpec(
            id = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash-Lite",
            tierLabel = "فوق‌سریع و کم‌مصرف",
            description = "بهینه برای پردازش‌های پرتکرار، دسته‌بندی سریع، خلاصه‌سازی و پاسخ‌گویی آنی در موبایل.",
            contextWindow = "1M tokens"
        )
    )

    fun findById(id: String): GeminiModelSpec =
        supportedModels.firstOrNull { it.id == id } ?: supportedModels.first()
}

data class StudioTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: ProjectCategory,
    val modelId: String,
    val temperature: Float,
    val systemInstruction: String,
    val jsonModeEnabled: Boolean = false,
    val jsonSchema: String = ""
)

object StudioTemplates {
    val presets: List<StudioTemplate> = listOf(
        StudioTemplate(
            id = "kotlin_architect",
            title = "معمار اندروید کاتلین",
            subtitle = "تولید کد Jetpack Compose و MVVM استاندارد",
            category = ProjectCategory.CODE,
            modelId = "gemini-4-argon",
            temperature = 0.35f,
            systemInstruction = "تو یک مهندس ارشد اندروید متخصص Kotlin، Jetpack Compose و Material Design 3 هستی. پاسخ‌ها را به فارسی روان بنویس و کدها را تمیز، مدرن و آماده اجرا ارائه بده."
        ),
        StudioTemplate(
            id = "json_extractor",
            title = "استخراج‌کننده JSON ساختاری",
            subtitle = "تبدیل متن آزاد به خروجی استاندارد Schema",
            category = ProjectCategory.STRUCTURED,
            modelId = "gemini-3.8-flash",
            temperature = 0.15f,
            systemInstruction = "داده‌های ورودی کاربر را تحلیل کن و دقیقاً مطابق اسکیمای JSON تعریف‌شده بدون هیچ متن اضافه‌ای برگردان.",
            jsonModeEnabled = true,
            jsonSchema = """
                {
                  "type": "object",
                  "properties": {
                    "title": { "type": "string" },
                    "priority": { "type": "string", "enum": ["high", "medium", "low"] },
                    "tags": { "type": "array", "items": { "type": "string" } },
                    "summary": { "type": "string" }
                  },
                  "required": ["title", "priority", "summary"]
                }
            """.trimIndent()
        ),
        StudioTemplate(
            id = "vision_ui_auditor",
            title = "تحلیلگر رابط کاربری و تصویر",
            subtitle = "بررسی اسکرین‌شات UI و پیشنهاد بهبود UX",
            category = ProjectCategory.VISION,
            modelId = "gemini-3.8-flash",
            temperature = 0.6f,
            systemInstruction = "تصویر یا شرح رابط کاربری را از نظر سلسله‌مراتب بصری، خوانایی، کنتراست رنگ و اصول Material 3 بررسی کن و راهکارهای عملی بده."
        ),
        StudioTemplate(
            id = "fast_translator",
            title = "مترجم تخصصی مستندات فنی",
            subtitle = "ترجمه دقیق و سریع متون مهندسی به فارسی",
            category = ProjectCategory.CHAT,
            modelId = "gemini-3.5-flash-lite",
            temperature = 0.25f,
            systemInstruction = "مستندات فنی و برنامه‌نویسی را با حفظ اصطلاحات کلیدی انگلیسی، به فارسی دقیق، روان و حرفه‌ای ترجمه کن."
        )
    )
}
