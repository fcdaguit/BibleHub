package com.sermonprep.app.ai

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.sermonprep.app.data.repo.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around MediaPipe's on-device LLM runtime. The model file (a Gemma ".task" bundle,
 * for example Gemma 3n E4B) is imported by the user, stored in app-private storage and loaded
 * lazily. Nothing is sent over the network at inference time.
 *
 * The model is NOT bundled in the APK: it is several GB and most Gemma downloads require accepting
 * a licence on the model host first. Gemma 3n E2B/E4B are distributed as ".litertlm" files; smaller
 * Gemma models also come as ".task" files.
 *
 * NOTE: Google lists the MediaPipe LLM Inference API as maintenance-only and recommends LiteRT-LM for
 * new work. This class is the single place that touches the runtime, so migrating means rewriting
 * load/generate/close here and nothing else.
 */
@Singleton
class LocalLlm @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settings: SettingsRepository,
) {
    private var engine: LlmInference? = null
    private val lock = Mutex()

    val isLoaded: Boolean get() = engine != null

    private val modelsDir get() = File(ctx.filesDir, "models").apply { mkdirs() }

    /** Copies a user-picked model file into private storage and remembers its path. */
    suspend fun importModel(uri: Uri, displayName: String, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val safe = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "model.task" }
        val dest = File(modelsDir, safe)
        val total = ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(1 shl 20)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        } ?: error("Cannot open the selected file")
        settings.setModelPath(dest.absolutePath)
        close()
        dest.name
    }

    suspend fun removeModel() = withContext(Dispatchers.IO) {
        close()
        settings.modelPath.first()?.let { File(it).delete() }
        settings.setModelPath(null)
    }

    suspend fun hasModel(): Boolean = settings.modelPath.first()?.let { File(it).exists() } == true

    suspend fun modelName(): String? = settings.modelPath.first()?.let { File(it).takeIf { f -> f.exists() }?.name }

    /** Loads the model if needed. Can take several seconds and a lot of RAM (E4B ≈ 4+ GB). */
    private suspend fun ensureLoaded(): LlmInference = lock.withLock {
        engine?.let { return it }
        withContext(Dispatchers.IO) {
            val path = settings.modelPath.first()?.takeIf { File(it).exists() }
                ?: error("No AI model installed. Import one in Settings → Offline AI model.")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(1536)
                .build()
            LlmInference.createFromOptions(ctx, options).also { engine = it }
        }
    }

    suspend fun generate(prompt: String): String {
        val e = ensureLoaded()
        return withContext(Dispatchers.Default) { lock.withLock { e.generateResponse(prompt) } }
    }

    fun close() {
        runCatching { engine?.close() }
        engine = null
    }
}
