package de.jug_da.standapp.llm

import com.github.tjake.jlama.model.AbstractModel
import com.github.tjake.jlama.model.ModelSupport
import com.github.tjake.jlama.model.functions.Generator
import com.github.tjake.jlama.safetensors.DType
import com.github.tjake.jlama.safetensors.prompt.PromptContext
import com.github.tjake.jlama.util.Downloader
import java.util.*
import java.util.function.BiConsumer


class JLamaService private constructor(private val m: AbstractModel) : LLMService {
    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float
    ): String {
        val systemPrompt = "You are a helpful assistant that creates concise standup summaries from git commit data."

        val ctx = if (m.promptSupport().isPresent) {
            m.promptSupport()
                .get()
                .builder()
                .addSystemMessage(systemPrompt)
                .addUserMessage(prompt)
                .build();
        } else {
            PromptContext.of(prompt);
        }
        val r: Generator.Response =
            m.generate(UUID.randomUUID(), ctx, temperature, maxTokens, BiConsumer { s: String?, f: Float? -> })
        return r.responseText
    }

    companion object {
        private const val DEFAULT_MODEL = "mistralai/Mistral-7B-Instruct-v0.3"

        fun create(
            modelPath: String,
            tokenizerPath: String,
            maxSequenceLength: Int = 512
        ): JLamaService {
            val model = modelPath.ifBlank { DEFAULT_MODEL }
            val workingDirectory = "./models"

            // Downloads the model or just returns the local path if it's already downloaded
            val localModelPath = Downloader(workingDirectory, model).huggingFaceModel()

            // Loads the quantized model and specified use of quantized memory
            val llm = ModelSupport.loadModel(localModelPath, DType.F32, DType.I8)

            return JLamaService(llm)
        }
    }
}
