package de.jug_da.standapp.llm.model

/** Model families this app knows how to load; each maps to a SKaiNET-transformers network loader. */
enum class ModelFamily(val acceptedArchitectures: Set<String>) {
    QWEN(setOf("qwen2", "qwen3", "qwen35")),
    LLAMA(setOf("llama", "mistral")),
}

/**
 * A GGUF checkpoint the pipeline can resolve: either from an explicit local
 * path / env override, or by downloading [hfUri] into the model cache.
 *
 * @property envOverride environment variable naming a local GGUF that replaces the download.
 * @property approxBytes expected size, used only for a sanity warning after download.
 */
data class ModelSpec(
    val id: String,
    val family: ModelFamily,
    val hfUri: String,
    val fileName: String,
    val envOverride: String,
    val approxBytes: Long,
)

/** The two models the daily standup pipeline ships with. */
object ModelCatalog {
    /** Tool-calling stage: turns the request into a `get_recent_commits` call. */
    val QWEN3_0_6B = ModelSpec(
        id = "qwen3-0.6b",
        family = ModelFamily.QWEN,
        hfUri = "hf://Qwen/Qwen3-0.6B-GGUF@main/Qwen3-0.6B-Q8_0.gguf",
        fileName = "Qwen3-0.6B-Q8_0.gguf",
        envOverride = "STANDAPP_QWEN_MODEL_PATH",
        approxBytes = 639_446_688L,
    )

    /** Summarisation stage. Q4_K_M stays packed on the native kernel path in SKaiNET 0.53. */
    val LLAMA_3_2_3B = ModelSpec(
        id = "llama-3.2-3b-instruct",
        family = ModelFamily.LLAMA,
        hfUri = "hf://bartowski/Llama-3.2-3B-Instruct-GGUF@main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        envOverride = "STANDAPP_LLAMA_MODEL_PATH",
        approxBytes = 2_019_377_696L,
    )

    val all: List<ModelSpec> = listOf(QWEN3_0_6B, LLAMA_3_2_3B)
}
