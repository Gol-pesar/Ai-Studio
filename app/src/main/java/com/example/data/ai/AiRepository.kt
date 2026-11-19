package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.model.CloudMessage
import com.example.data.model.CloudProject
import com.example.data.model.GeminiModelCatalog
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.max

private const val TAG = "FirebaseAiRepository"
private const val AI_TIMEOUT_MS = 60_000L
private const val MAX_IMAGE_DIMENSION = 1280

data class AiRunRequest(
    val project: CloudProject,
    val history: List<CloudMessage>,
    val userPrompt: String,
    val attachedImageUri: String? = null
)

sealed interface AiStreamEvent {
    data class Chunk(val accumulatedText: String) : AiStreamEvent
    data class Completed(
        val fullText: String,
        val estimatedTokens: Long
    ) : AiStreamEvent
    data class Error(val message: String) : AiStreamEvent
}

interface AiRepository {
    fun streamPrompt(request: AiRunRequest): Flow<AiStreamEvent>
}

class FirebaseAiRepository(private val context: Context) : AiRepository {

    private val aiService by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    override fun streamPrompt(request: AiRunRequest): Flow<AiStreamEvent> = flow {
        val project = request.project
        val modelId = GeminiModelCatalog.findById(project.modelId).id
        val promptText = request.userPrompt.trim()

        val effectiveSystemInstruction = buildString {
            if (project.systemInstruction.isNotBlank()) {
                append(project.systemInstruction.trim())
            }
            if (project.jsonModeEnabled && project.jsonSchema.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("Respond strictly with valid JSON conforming to this schema:\n")
                append(project.jsonSchema.trim())
            }
        }

        val config = generationConfig {
            temperature = project.temperature.toFloat().coerceIn(0f, 2f)
            topP = project.topP.toFloat().coerceIn(0.05f, 1f)
            topK = project.topK.toInt().coerceIn(1, 100)
            maxOutputTokens = project.maxOutputTokens.toInt().coerceIn(256, 8192)
            if (project.jsonModeEnabled) {
                responseMimeType = "application/json"
            }
        }

        val safetySettings = if (project.safetyBalanced) {
            listOf(
                SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.MEDIUM_AND_ABOVE),
                SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.MEDIUM_AND_ABOVE),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.MEDIUM_AND_ABOVE),
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.MEDIUM_AND_ABOVE)
            )
        } else {
            null
        }

        val generativeModel = aiService.generativeModel(
            modelName = modelId,
            generationConfig = config,
            safetySettings = safetySettings,
            systemInstruction = if (effectiveSystemInstruction.isNotBlank()) {
                content { text(effectiveSystemInstruction) }
            } else {
                null
            }
        )

        val bitmap = request.attachedImageUri?.let { uriStr ->
            decodeScaledBitmap(uriStr)
        }

        val turnContent: Content = content("user") {
            if (bitmap != null) {
                image(bitmap)
            }
            text(promptText.ifBlank { "این تصویر را تحلیل کن." })
        }

        val historyContents = buildValidChatHistory(request.history)
        val accumulated = StringBuilder()
        var reportedTokens = 0L

        try {
            withTimeout(AI_TIMEOUT_MS) {
                val streamFlow = if (historyContents.isNotEmpty()) {
                    val chat = generativeModel.startChat(history = historyContents)
                    chat.sendMessageStream(turnContent)
                } else {
                    generativeModel.generateContentStream(turnContent)
                }

                streamFlow.collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(AiStreamEvent.Chunk(accumulated.toString()))
                    }
                    chunk.usageMetadata?.totalTokenCount?.let { count ->
                        if (count > 0) reportedTokens = count.toLong()
                    }
                }
            }

            val finalText = accumulated.toString().trim()
            if (finalText.isNotEmpty()) {
                val tokens = if (reportedTokens > 0L) {
                    reportedTokens
                } else {
                    max(16L, ((promptText.length + finalText.length) / 3).toLong())
                }
                emit(AiStreamEvent.Completed(fullText = finalText, estimatedTokens = tokens))
            } else {
                emit(AiStreamEvent.Error("مدل پاسخی برنگرداند. لطفاً پرامپت را تغییر دهید و دوباره تلاش کنید."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini streaming timed out after ${AI_TIMEOUT_MS}ms", e)
            emit(AiStreamEvent.Error("زمان انتظار پاسخ از سرور گوگل (۶۰ ثانیه) به پایان رسید. دوباره تلاش کنید."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic generation failed", e)
            emit(
                AiStreamEvent.Error(
                    e.localizedMessage ?: "خطا در ارتباط با سرویس رسمی Gemini. اتصال اینترنت را بررسی کنید."
                )
            )
        } finally {
            bitmap?.recycle()
        }
    }

    private fun buildValidChatHistory(messages: List<CloudMessage>): List<Content> {
        val recent = messages.takeLast(16)
        val contents = mutableListOf<Content>()
        var expectedRole = "user"
        for (msg in recent) {
            if (msg.content.isBlank()) continue
            val role = if (msg.role == "model") "model" else "user"
            if (contents.isEmpty() && role != "user") continue
            if (role == expectedRole) {
                contents.add(content(role) { text(msg.content) })
                expectedRole = if (role == "user") "model" else "user"
            }
        }
        // Ensure history ends on model turn so the new user turn alternates cleanly.
        if (contents.isNotEmpty() && contents.last().role == "user") {
            contents.removeAt(contents.lastIndex)
        }
        return contents
    }

    private suspend fun decodeScaledBitmap(uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sampleSize = 1
            while (
                bounds.outWidth / sampleSize > MAX_IMAGE_DIMENSION ||
                bounds.outHeight / sampleSize > MAX_IMAGE_DIMENSION
            ) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode multimodal image attachment", e)
            null
        }
    }
}
