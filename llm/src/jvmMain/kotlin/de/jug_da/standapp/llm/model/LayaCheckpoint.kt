package de.jug_da.standapp.llm.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.ainet.context.DirectCpuExecutionContext
import sk.ainet.lang.tensor.data.MemorySegmentTensorDataFactory
import sk.ainet.lang.types.FP32
import sk.ainet.models.laya.LayaAgent
import sk.ainet.models.laya.LayaModelLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * A Laya decision checkpoint: a directory, not a single GGUF, so it has its own spec.
 *
 * The directory layout is the one published on Hugging Face (`rl_agent_config.json`,
 * `encoder/config.json`, `tokenizer/tokenizer.json`, `tokenizer/tokenizer_config.json`,
 * `model.safetensors`). Resolution order mirrors [ModelResolver]: explicit directory,
 * `STANDAPP_LAYA_MODEL_PATH`, else the files are downloaded one by one through the same
 * `hf://` cache and assembled under `<cache>/<dirName>`.
 */
data class LayaCheckpointSpec(
    val id: String,
    val envOverride: String,
    val dirName: String,
    /** `hf://org/repo@rev/path` per file, keyed by the path relative to the checkpoint directory. */
    val files: Map<String, String>,
    val approxBytes: Long,
) {
    companion object {
        private const val HF_ROOT = "hf://convaiinnovations/laya@7b928d828b7b0e022f929d9bd2e44165aa270148/multilingual"

        /** The multilingual base checkpoint (mmBERT-base encoder, Apache-2.0, Convai Innovations). */
        val MULTILINGUAL = LayaCheckpointSpec(
            id = "laya-multilingual",
            envOverride = "STANDAPP_LAYA_MODEL_PATH",
            dirName = "laya-multilingual",
            files = listOf(
                "rl_agent_config.json",
                "encoder/config.json",
                "tokenizer/tokenizer.json",
                "tokenizer/tokenizer_config.json",
                "model.safetensors",
            ).associateWith { "$HF_ROOT/$it" },
            approxBytes = 643_835_514L,
        )

        /** Files that must exist for [LayaModelLoader] to accept a directory. */
        val REQUIRED = MULTILINGUAL.files.keys
    }
}

/** Resolves a [LayaCheckpointSpec] to a local directory with every required file. */
class LayaCheckpointResolver(
    private val models: ModelResolver,
    private val env: (String) -> String? = System::getenv,
    private val log: (String) -> Unit = System.err::println,
) {
    suspend fun resolve(spec: LayaCheckpointSpec, explicit: Path? = null): Path {
        explicit?.let { return checked(spec, it, "flag") }
        env(spec.envOverride)?.takeIf { it.isNotBlank() }?.let { return checked(spec, Path.of(it), spec.envOverride) }
        val dir = models.cacheDir.toPath().resolve(spec.dirName)
        for ((relative, uri) in spec.files) {
            val target = dir.resolve(relative)
            if (target.exists()) continue
            val downloaded = models.resolveUri("${spec.id}/$relative", uri)
            Files.createDirectories(target.parent)
            Files.copy(downloaded, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return checked(spec, dir, "cache")
    }

    private fun checked(spec: LayaCheckpointSpec, dir: Path, origin: String): Path {
        if (!dir.isDirectory()) {
            throw sk.ainet.data.source.DataSourceException("${spec.id}: $origin points to '$dir' which is not a directory")
        }
        val missing = LayaCheckpointSpec.REQUIRED.filterNot { dir.resolve(it).exists() }
        if (missing.isNotEmpty()) {
            throw sk.ainet.data.source.DataSourceException("${spec.id}: '$dir' lacks ${missing.joinToString()}")
        }
        log("[MODEL] ${spec.id}: using checkpoint directory from $origin -> $dir")
        return dir
    }
}

/** Loads a Laya checkpoint directory on the FP32 CPU path (exact GELU, as the reference). */
object LayaCheckpointLoader {
    suspend fun load(dir: Path, log: (String) -> Unit = System.err::println): LayaAgent<FP32> =
        withContext(Dispatchers.Default) {
            KernelSetup.ensureInstalled(log)
            val started = System.nanoTime()
            val ctx = DirectCpuExecutionContext(
                tensorDataFactory = MemorySegmentTensorDataFactory(),
                schedule = ScheduleSettings.scheduleFromEnv(),
            )
            val agent = LayaModelLoader.load(dir, ctx, exactGelu = true)
            log("[MODEL LOAD] laya: $dir in ${(System.nanoTime() - started) / 1_000_000} ms")
            agent
        }
}
