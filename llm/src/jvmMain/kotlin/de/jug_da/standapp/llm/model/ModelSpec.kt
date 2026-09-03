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

    /**
     * Faster, lower-quality summariser preset (`--summary-model 1b`): roughly a
     * third of the weights and 16 layers × 32 heads instead of 28 × 24, so both
     * the memory-bound gemv and the scalar attention loops run ~2–3× faster.
     */
    val LLAMA_3_2_1B = ModelSpec(
        id = "llama-3.2-1b-instruct",
        family = ModelFamily.LLAMA,
        hfUri = "hf://bartowski/Llama-3.2-1B-Instruct-GGUF@main/Llama-3.2-1B-Instruct-Q8_0.gguf",
        fileName = "Llama-3.2-1B-Instruct-Q8_0.gguf",
        envOverride = "STANDAPP_LLAMA_MODEL_PATH",
        approxBytes = 1_321_082_016L,
    )

    val all: List<ModelSpec> = listOf(QWEN3_0_6B, LLAMA_3_2_3B, LLAMA_3_2_1B)

    /** `--summary-model` presets. */
    fun summaryModel(name: String): ModelSpec? = when (name.lowercase()) {
        "3b", "llama-3.2-3b", "llama3.2-3b" -> LLAMA_3_2_3B
        "1b", "llama-3.2-1b", "llama3.2-1b" -> LLAMA_3_2_1B
        else -> null
    }
}

/**
 * Prefill strategy knob shared by both model stages, read from
 * `STANDAPP_PREFILL`: `autoregressive` or `batched[:N]` (default `batched:64`).
 * Exposed because on some SKaiNET versions the batched attention path is the
 * slower one; the verification log records which wins on a given machine.
 */
object PrefillSettings {
    fun fromEnv(env: (String) -> String? = System::getenv): sk.ainet.apps.llm.PrefillStrategy {
        val raw = env("STANDAPP_PREFILL")?.trim()?.lowercase() ?: return sk.ainet.apps.llm.PrefillStrategy.Batched(64)
        return when {
            raw == "autoregressive" || raw == "auto" -> sk.ainet.apps.llm.PrefillStrategy.Autoregressive
            raw.startsWith("batched") -> {
                val n = raw.substringAfter(':', "").toIntOrNull() ?: 64
                sk.ainet.apps.llm.PrefillStrategy.Batched(n)
            }
            else -> sk.ainet.apps.llm.PrefillStrategy.Batched(64)
        }
    }
}

/**
 * Per-step activation slab for `OptimizedLLMRuntime` (`forwardSlabFloats`), read
 * from `STANDAPP_SLAB_FLOATS` (default: upstream 8 M floats = 32 MB). A prefill
 * chunk that outgrows the slab overflows to tracked heap storage — correct but
 * allocation-heavy; `[STAGE/SCOPE]` logs report `usedFloats` / `overflowBytes`.
 */
object SlabSettings {
    const val UPSTREAM_DEFAULT = 8 * 1024 * 1024
    fun fromEnv(env: (String) -> String? = System::getenv): Int =
        env("STANDAPP_SLAB_FLOATS")?.trim()?.toIntOrNull()?.takeIf { it >= 0 } ?: UPSTREAM_DEFAULT
}
