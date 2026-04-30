package de.jug_da.standapp.llm

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Locates the embedded Llama 3.2 1B Instruct GGUF (packaged at
 * `models/Llama-3.2-1B-Instruct-Q8_0.gguf` in the JAR resources) and extracts
 * it to a stable cache directory because [sk.ainet.apps.kllama.java.KLlamaJava.loadGGUF]
 * needs a real, random-access file path — GGUF cannot be streamed from a JAR.
 *
 * First call extracts to `${user.home}/.cache/standapp/models/`; subsequent calls
 * reuse the existing file when its size matches the resource length.
 */
object EmbeddedModelLoader {

    const val RESOURCE_PATH: String = "models/Llama-3.2-1B-Instruct-Q8_0.gguf"
    private const val CACHE_DIR_NAME: String = ".cache/standapp/models"
    private const val FILE_NAME: String = "Llama-3.2-1B-Instruct-Q8_0.gguf"

    fun extract(): Path {
        val classLoader = EmbeddedModelLoader::class.java.classLoader
        val resourceUrl = classLoader.getResource(RESOURCE_PATH)
            ?: error(
                "Embedded model not found on classpath at '$RESOURCE_PATH'. " +
                        "Run `uv run scripts/download-model.py` and rebuild."
            )

        val cacheDir = Path.of(System.getProperty("user.home")).resolve(CACHE_DIR_NAME)
        Files.createDirectories(cacheDir)
        val target = cacheDir.resolve(FILE_NAME)

        val resourceLength = resourceUrl.openConnection().contentLengthLong
        if (Files.exists(target) && resourceLength > 0 && Files.size(target) == resourceLength) {
            return target
        }

        println("[EmbeddedModelLoader] Extracting $FILE_NAME to $target …")
        resourceUrl.openStream().use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }
        println("[EmbeddedModelLoader] Extracted ${Files.size(target)} bytes")
        return target
    }
}
