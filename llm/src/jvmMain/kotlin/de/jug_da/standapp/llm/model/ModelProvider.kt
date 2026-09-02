package de.jug_da.standapp.llm.model

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path

/**
 * Hands pipeline stages a loaded [LocalModel] and owns its lifetime.
 *
 * [releasing] loads a model for the duration of one `withModel` block and
 * closes it afterwards — the default for the CLI, where the Qwen and Llama
 * stages run one after the other and peak memory stays at one model.
 * [caching] keeps models resident across calls (benchmark, tests,
 * `--keep-models`). Both memoise the resolved path, so a download happens at
 * most once per process.
 */
interface ModelProvider : AutoCloseable {
    suspend fun <R> withModel(spec: ModelSpec, block: suspend (LocalModel) -> R): R

    companion object {
        fun releasing(
            resolver: ModelResolver = ModelResolver(),
            overrides: Map<ModelSpec, Path> = emptyMap(),
            log: (String) -> Unit = System.err::println,
        ): ModelProvider = ReleasingModelProvider(resolver, overrides, log)

        fun caching(
            resolver: ModelResolver = ModelResolver(),
            overrides: Map<ModelSpec, Path> = emptyMap(),
            log: (String) -> Unit = System.err::println,
        ): ModelProvider = CachingModelProvider(resolver, overrides, log)
    }
}

abstract class ResolvingModelProvider(
    private val resolver: ModelResolver,
    private val overrides: Map<ModelSpec, Path>,
    protected val log: (String) -> Unit,
) : ModelProvider {
    private val paths = mutableMapOf<ModelSpec, Path>()
    private val pathLock = Mutex()

    protected suspend fun pathFor(spec: ModelSpec): Path = pathLock.withLock {
        paths.getOrPut(spec) { resolver.resolve(spec, overrides[spec]) }
    }

    override fun close() {
        resolver.close()
    }
}

class ReleasingModelProvider(
    resolver: ModelResolver,
    overrides: Map<ModelSpec, Path>,
    log: (String) -> Unit,
) : ResolvingModelProvider(resolver, overrides, log) {
    override suspend fun <R> withModel(spec: ModelSpec, block: suspend (LocalModel) -> R): R {
        val model = LocalModel.load(spec, pathFor(spec), log)
        try {
            return block(model)
        } finally {
            model.close()
            log("[MODEL] ${spec.id}: released")
        }
    }
}

class CachingModelProvider(
    resolver: ModelResolver,
    overrides: Map<ModelSpec, Path>,
    log: (String) -> Unit,
) : ResolvingModelProvider(resolver, overrides, log) {
    private val loaded = mutableMapOf<ModelSpec, LocalModel>()
    private val loadLock = Mutex()

    override suspend fun <R> withModel(spec: ModelSpec, block: suspend (LocalModel) -> R): R {
        val model = loadLock.withLock {
            loaded.getOrPut(spec) { LocalModel.load(spec, pathFor(spec), log) }
        }
        return block(model)
    }

    override fun close() {
        loaded.values.forEach { it.close() }
        loaded.clear()
        super.close()
    }
}
