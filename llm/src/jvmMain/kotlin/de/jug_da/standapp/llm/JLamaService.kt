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
        val systemPrompt = "You are helpfull chatbot who creating questions and answers from text."

        val truncatedPrompt = prompt.take(200) // Limit prompt size
        
        val ctx = if (m.promptSupport().isPresent) {
            m.promptSupport()
                .get()
                .builder()
                .addSystemMessage("You are a helpful chatbot who writes short responses.")
                .addUserMessage(truncatedPrompt)
                .build();
        } else {
            PromptContext.of(truncatedPrompt);
        }
        val r: Generator.Response =
            m.generate(UUID.randomUUID(), ctx, temperature, 100, BiConsumer { s: String?, f: Float? -> })
        return r.responseText
    }

    fun generateStream(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        onToken: (String) -> Unit
    ) {
        val truncatedPrompt = prompt.take(200) // Limit prompt size
        
        val ctx = if (m.promptSupport().isPresent) {
            m.promptSupport()
                .get()
                .builder()
                .addSystemMessage("You are a helpful chatbot who writes short responses.")
                .addUserMessage(truncatedPrompt)
                .build();
        } else {
            PromptContext.of(truncatedPrompt);
        }
        
        m.generate(UUID.randomUUID(), ctx, temperature, maxTokens, BiConsumer { token: String?, _: Float? ->
            token?.let { onToken(it) }
        })
    }

    companion object {
        fun create(
            modelPath: String,
            tokenizerPath: String,
            maxSequenceLength: Int = 512
        ): JLamaService {

            //val model = "tjake/Llama-3.2-1B-Instruct-JQ4"
            val model = "mistralai/Mistral-7B-Instruct-v0.3"
            val workingDirectory = "./models"

            // Downloads the model or just returns the local path if it's already downloaded
            val localModelPath = Downloader(workingDirectory, model).huggingFaceModel()


            // Loads the quantized model and specified use of quantized memory
            val llm = ModelSupport.loadModel(localModelPath, DType.F32, DType.I8)


            return JLamaService(llm)
        }
    }
}
