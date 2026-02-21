package dev.standapp.engine.backend

import com.github.tjake.jlama.model.AbstractModel
import com.github.tjake.jlama.model.ModelSupport
import com.github.tjake.jlama.model.functions.Generator
import com.github.tjake.jlama.safetensors.DType
import com.github.tjake.jlama.safetensors.prompt.PromptContext
import com.github.tjake.jlama.util.Downloader
import dev.standapp.engine.boundary.LLMBackend
import dev.standapp.engine.entity.GenerationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.function.BiConsumer

/**
 * LLM backend powered by JLama (pure Java inference).
 *
 * Downloads models from HuggingFace automatically if not cached locally.
 */
class JLamaBackend private constructor(
    private val model: AbstractModel,
) : LLMBackend {

    override suspend fun generate(prompt: String, config: GenerationConfig): String =
        withContext(Dispatchers.IO) {
            val ctx = if (model.promptSupport().isPresent) {
                model.promptSupport()
                    .get()
                    .builder()
                    .addSystemMessage("You are a helpful assistant that creates concise standup summaries from git commit data.")
                    .addUserMessage(prompt)
                    .build()
            } else {
                PromptContext.of(prompt)
            }

            val response: Generator.Response = model.generate(
                UUID.randomUUID(),
                ctx,
                config.temperature,
                config.maxTokens,
                BiConsumer { _: String?, _: Float? -> },
            )

            response.responseText
        }

    companion object {
        /**
         * Create a [JLamaBackend] from a HuggingFace model identifier.
         *
         * The model is downloaded to [workingDirectory] if not already present.
         */
        fun create(
            modelName: String = "mistralai/Mistral-7B-Instruct-v0.3",
            workingDirectory: String = "./models",
        ): JLamaBackend {
            val localModelPath = Downloader(workingDirectory, modelName).huggingFaceModel()
            val llm = ModelSupport.loadModel(localModelPath, DType.F32, DType.I8)
            return JLamaBackend(llm)
        }
    }
}
