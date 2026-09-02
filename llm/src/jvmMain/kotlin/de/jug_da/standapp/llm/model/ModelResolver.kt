package de.jug_da.standapp.llm.model

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.buffered
import sk.ainet.data.source.CachePolicy
import sk.ainet.data.source.DataSourceException
import sk.ainet.data.source.DataSourceRemoteContent
import sk.ainet.data.source.DataSourceRequest
import sk.ainet.data.source.JvmDataSourceResolver
import sk.ainet.data.source.KtorRemoteDataSourceFetcher
import sk.ainet.data.source.RemoteDataSourceFetcher
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * Turns a [ModelSpec] into a local GGUF path.
 *
 * Precedence:
 * 1. explicit path handed in by the caller (CLI flag),
 * 2. the spec's env override (`STANDAPP_QWEN_MODEL_PATH` / `STANDAPP_LLAMA_MODEL_PATH`),
 * 3. `MCP_LLM_MODEL_PATH` — for the summariser only, so existing Docker/benchmark
 *    setups that point it at "the GGUF" keep working,
 * 4. download of the spec's `hf://` URI through SKaiNET's data-source resolver
 *    into the model cache (`STANDAPP_MODEL_CACHE_DIR`, default `~/.cache/standapp/models`).
 *
 * Any of the overrides may itself be an `hf://` or `https://` URI, in which
 * case it is resolved through the same cache. `STANDAPP_OFFLINE=1` refuses to
 * touch the network and fails fast when the cache is cold.
 */
class ModelResolver(
    val cacheDir: File = defaultCacheDir(),
    private val env: (String) -> String? = System::getenv,
    private val log: (String) -> Unit = System.err::println,
    fetcher: RemoteDataSourceFetcher? = null,
) : AutoCloseable {

    private val client: HttpClient? = if (fetcher == null) {
        HttpClient(CIO) {
            expectSuccess = true
            install(HttpTimeout) {
                // The data-source default is 600 s per request; a 2 GB GGUF on a
                // slow line needs longer. Socket timeout still catches stalls.
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                connectTimeoutMillis = 60_000
                socketTimeoutMillis = 300_000
            }
        }
    } else null

    private val resolver = JvmDataSourceResolver(
        cacheDir = cacheDir,
        fetcher = ProgressReportingFetcher(fetcher ?: KtorRemoteDataSourceFetcher(client!!), log),
        useEnvironmentHuggingFaceToken = true,
    )

    suspend fun resolve(spec: ModelSpec, explicit: Path? = null): Path {
        explicit?.let { return resolveLocation(spec, it.toString(), "flag") }
        env(spec.envOverride)?.takeIf { it.isNotBlank() }?.let { return resolveLocation(spec, it, spec.envOverride) }
        if (spec.family == ModelFamily.LLAMA) {
            env("MCP_LLM_MODEL_PATH")?.takeIf { it.isNotBlank() }?.let { return resolveLocation(spec, it, "MCP_LLM_MODEL_PATH") }
        }
        return download(spec, spec.hfUri)
    }

    private suspend fun resolveLocation(spec: ModelSpec, location: String, origin: String): Path {
        if (location.startsWith("hf://") || location.startsWith("hf+https://") ||
            location.startsWith("https://") || location.startsWith("http://")
        ) {
            log("[MODEL] ${spec.id}: $origin -> $location")
            return download(spec, location)
        }
        val path = Path.of(location)
        if (!path.exists() || !path.isRegularFile()) {
            throw DataSourceException("${spec.id}: $origin points to '$location' which is not a readable file")
        }
        log("[MODEL] ${spec.id}: using local file from $origin -> $path")
        return path
    }

    private suspend fun download(spec: ModelSpec, uri: String): Path {
        val offline = env("STANDAPP_OFFLINE") == "1"
        val policy = if (offline) CachePolicy.Offline else CachePolicy.Use
        log("[MODEL] ${spec.id}: resolving $uri (cache=$cacheDir, policy=$policy)")
        val artifact = resolver.resolve(DataSourceRequest(uri = uri, cachePolicy = policy))
        val local = artifact.localPath
            ?: throw DataSourceException("${spec.id}: resolver returned no local path for $uri")
        val path = Path.of(local)
        val size = artifact.sizeBytes ?: path.toFile().length()
        log("[MODEL] ${spec.id}: ${if (artifact.cacheHit) "cache hit" else "downloaded"} -> $path (${size / (1L shl 20)} MB)")
        if (uri == spec.hfUri && size < spec.approxBytes * 9 / 10) {
            log("[MODEL] WARNING: ${spec.id} is ${size} bytes, expected about ${spec.approxBytes}; the file may be truncated")
        }
        return path
    }

    override fun close() {
        client?.close()
    }

    companion object {
        fun defaultCacheDir(env: (String) -> String? = System::getenv): File {
            env("STANDAPP_MODEL_CACHE_DIR")?.takeIf { it.isNotBlank() }?.let { return File(it) }
            val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("java.io.tmpdir")
            return File(home, ".cache/standapp/models")
        }
    }
}

/** Wraps a fetcher so multi-GB downloads report progress on stderr every 64 MB. */
private class ProgressReportingFetcher(
    private val delegate: RemoteDataSourceFetcher,
    private val log: (String) -> Unit,
) : RemoteDataSourceFetcher {
    override suspend fun fetch(uri: String, headers: Map<String, String>): DataSourceRemoteContent {
        val content = delegate.fetch(uri, headers)
        val total = content.sizeBytes
        val name = uri.substringAfterLast('/')
        val counting = CountingRawSource(content.source) { done ->
            val doneMb = done / (1L shl 20)
            val totalText = total?.let { "/${it / (1L shl 20)} MB" } ?: " MB"
            log("[MODEL] downloading $name: $doneMb$totalText")
        }
        return DataSourceRemoteContent(source = counting.buffered(), sizeBytes = total)
    }
}

private class CountingRawSource(
    private val source: Source,
    private val onProgress: (Long) -> Unit,
) : RawSource {
    private var read = 0L
    private var lastReport = 0L

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val n = source.readAtMostTo(sink, byteCount)
        if (n > 0) {
            read += n
            if (read - lastReport >= REPORT_EVERY) {
                lastReport = read
                onProgress(read)
            }
        }
        return n
    }

    override fun close() = source.close()

    private companion object {
        const val REPORT_EVERY = 64L shl 20
    }
}
