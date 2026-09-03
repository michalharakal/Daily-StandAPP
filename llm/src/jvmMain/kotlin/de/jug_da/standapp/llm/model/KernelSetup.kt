package de.jug_da.standapp.llm.model

import sk.ainet.backend.api.kernel.KernelDispatch
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

    /** A healthy JVM bootstrap registers at least 17 view kernels (7 ffm-rowmajor, 7 native-ffm packed, 2 fp32, 1 reference; 19 with the ternary packs). */
    const val EXPECTED_JVM_KERNELS = 17

    @OptIn(ExperimentalMemoryApi::class)
    fun ensureInstalled(log: (String) -> Unit = System.err::println) {
        if (!installed.compareAndSet(false, true)) return
        // 0.52+: discovers providers (ServiceLoader), then KernelPacks.install(), then the
        // platform packs — in the order upstream requires. The explicit calls below are
        // idempotent and keep older engines covered.
        KernelDispatch.ensureInstalled()
        KernelPacks.install()
        FfmRowMajorKernelPack.install()
        val kernels = KernelDispatch.kernels()
        val rowMajor = kernels.map { it.name }.filter { it.startsWith("ffm-rowmajor") }
        log("[KERNELS] ${kernels.size} view kernels registered; row-major native packs: ${rowMajor.joinToString(", ").ifEmpty { "NONE" }}")
        if (rowMajor.isEmpty()) {
            log("[KERNELS] WARNING: no ffm-rowmajor kernels — the native library did not load; packed GGUF weights will run on the ~1000x slower reference kernel")
        } else if (kernels.size < EXPECTED_JVM_KERNELS) {
            log("[KERNELS] WARNING: expected $EXPECTED_JVM_KERNELS kernels on JVM, got ${kernels.size}: ${kernels.map { it.name }}")
        }
    }
}
