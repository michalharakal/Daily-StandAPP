package de.jug_da.standapp.llm.model

import sk.ainet.backend.api.kernel.KernelPacks
import sk.ainet.exec.kernel.FfmRowMajorKernelPack
import sk.ainet.lang.memory.ExperimentalMemoryApi
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Once-per-process kernel pack installation.
 *
 * `DecoderGgufWeightLoader` keeps GGUF weights packed (Q8_0 / Q4_K / Q6_K) and
 * memory-mapped. Without the row-major kernel packs installed those packed
 * weights fall to the decoding reference kernel — correct, but dramatically
 * slower per matmul. Mirrors what skainet-cli and `KLlamaJava` do on startup.
 */
object KernelSetup {
    private val installed = AtomicBoolean(false)

    @OptIn(ExperimentalMemoryApi::class)
    fun ensureInstalled() {
        if (!installed.compareAndSet(false, true)) return
        KernelPacks.install()
        FfmRowMajorKernelPack.install()
    }
}
