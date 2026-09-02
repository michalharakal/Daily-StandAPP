package de.jug_da.standapp.llm.model

import kotlinx.coroutines.test.runTest
import sk.ainet.data.source.DataSourceException
import sk.ainet.data.source.DataSourceRemoteContent
import sk.ainet.data.source.RemoteDataSourceFetcher
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModelResolverTest {

    private class CountingFetcher(private val payload: ByteArray) : RemoteDataSourceFetcher {
        val uris = mutableListOf<String>()
        override suspend fun fetch(uri: String, headers: Map<String, String>): DataSourceRemoteContent {
            uris += uri
            return DataSourceRemoteContent.fromBytes(payload)
        }
    }

    private val payload = ByteArray(2048) { (it % 7).toByte() }

    private fun resolver(env: Map<String, String>, fetcher: RemoteDataSourceFetcher, cache: Path) =
        ModelResolver(cacheDir = cache.toFile(), env = { env[it] }, log = {}, fetcher = fetcher)

    private fun tempDir(): Path = Files.createTempDirectory("standapp-models")

    private fun localGguf(dir: Path, name: String): Path = dir.resolve(name).also { it.writeBytes(payload) }

    @Test
    fun downloads_into_cache_once_then_hits_cache() = runTest {
        val cache = tempDir()
        val fetcher = CountingFetcher(payload)
        val r = resolver(emptyMap(), fetcher, cache)

        val first = r.resolve(ModelCatalog.QWEN3_0_6B)
        val second = r.resolve(ModelCatalog.QWEN3_0_6B)

        assertEquals(first, second)
        assertTrue(first.exists() && first.startsWith(cache), "expected $first under $cache")
        assertTrue(first.fileName.toString().endsWith(ModelCatalog.QWEN3_0_6B.fileName))
        assertTrue(payload.contentEquals(first.readBytes()))
        assertEquals(listOf("https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q8_0.gguf"), fetcher.uris)
    }

    @Test
    fun explicit_path_wins_over_env_and_download() = runTest {
        val dir = tempDir()
        val explicit = localGguf(dir, "explicit.gguf")
        val fromEnv = localGguf(dir, "env.gguf")
        val fetcher = CountingFetcher(payload)
        val r = resolver(mapOf("STANDAPP_LLAMA_MODEL_PATH" to fromEnv.toString()), fetcher, dir.resolve("cache"))

        assertEquals(explicit, r.resolve(ModelCatalog.LLAMA_3_2_3B, explicit))
        assertEquals(fromEnv, r.resolve(ModelCatalog.LLAMA_3_2_3B))
        assertTrue(fetcher.uris.isEmpty())
    }

    @Test
    fun mcp_model_path_applies_to_the_summariser_only() = runTest {
        val dir = tempDir()
        val legacy = localGguf(dir, "legacy.gguf")
        val fetcher = CountingFetcher(payload)
        val r = resolver(mapOf("MCP_LLM_MODEL_PATH" to legacy.toString()), fetcher, dir.resolve("cache"))

        assertEquals(legacy, r.resolve(ModelCatalog.LLAMA_3_2_3B))
        val qwen = r.resolve(ModelCatalog.QWEN3_0_6B)
        assertTrue(qwen.startsWith(dir.resolve("cache")))
        assertEquals(1, fetcher.uris.size)
    }

    @Test
    fun offline_with_cold_cache_fails_fast() = runTest {
        val fetcher = CountingFetcher(payload)
        val r = resolver(mapOf("STANDAPP_OFFLINE" to "1"), fetcher, tempDir())
        assertFailsWith<DataSourceException> { r.resolve(ModelCatalog.QWEN3_0_6B) }
        assertTrue(fetcher.uris.isEmpty())
    }

    @Test
    fun missing_override_file_is_reported() = runTest {
        val r = resolver(mapOf("STANDAPP_QWEN_MODEL_PATH" to "/nowhere/model.gguf"), CountingFetcher(payload), tempDir())
        val e = assertFailsWith<DataSourceException> { r.resolve(ModelCatalog.QWEN3_0_6B) }
        assertTrue(e.message!!.contains("STANDAPP_QWEN_MODEL_PATH"))
    }

    @Test
    fun default_cache_dir_honours_env() {
        assertEquals("/data/models", ModelResolver.defaultCacheDir { if (it == "STANDAPP_MODEL_CACHE_DIR") "/data/models" else null }.path)
        assertTrue(ModelResolver.defaultCacheDir { null }.path.endsWith(".cache/standapp/models"))
    }
}
