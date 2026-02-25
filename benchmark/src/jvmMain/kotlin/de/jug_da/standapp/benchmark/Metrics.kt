package de.jug_da.standapp.benchmark

import java.lang.management.ManagementFactory

/**
 * Operational metrics collection (PRD Section 6).
 */
object Metrics {

    data class RunMetrics(
        val latencyMs: Long,
        val charCount: Int,
        val throughputCharsPerSec: Double,
        val heapBeforeMb: Long,
        val heapAfterMb: Long,
    )

    data class AggregateMetrics(
        val latencyMedianMs: Long,
        val latencyP95Ms: Long,
        val latencyMaxMs: Long,
        val throughputMedianCharsPerSec: Double,
        val determinismScore: Double,
        val timeoutCount: Int,
        val errorCount: Int,
    )

    /** Snapshot current JVM heap usage in MB. */
    fun heapUsageMb(): Long {
        val memBean = ManagementFactory.getMemoryMXBean()
        return memBean.heapMemoryUsage.used / (1024 * 1024)
    }

    /** Get process CPU load (0.0-1.0), or -1 if unavailable. */
    fun cpuLoad(): Double {
        val osBean = ManagementFactory.getOperatingSystemMXBean()
        return if (osBean is com.sun.management.OperatingSystemMXBean) {
            osBean.processCpuLoad
        } else {
            -1.0
        }
    }

    /** Compute percentile from a sorted list. */
    fun percentile(sortedValues: List<Long>, p: Double): Long {
        if (sortedValues.isEmpty()) return 0
        val index = ((p / 100.0) * (sortedValues.size - 1)).toInt().coerceIn(0, sortedValues.size - 1)
        return sortedValues[index]
    }

    /** Aggregate individual run metrics into summary statistics. */
    fun aggregate(
        runs: List<RunMetrics>,
        outputs: List<String>,
        timeoutCount: Int,
        errorCount: Int,
    ): AggregateMetrics {
        val latencies = runs.map { it.latencyMs }.sorted()
        val throughputs = runs.map { it.throughputCharsPerSec }.sorted()

        return AggregateMetrics(
            latencyMedianMs = percentile(latencies, 50.0),
            latencyP95Ms = percentile(latencies, 95.0),
            latencyMaxMs = latencies.lastOrNull() ?: 0,
            throughputMedianCharsPerSec = if (throughputs.isEmpty()) 0.0
                else throughputs[throughputs.size / 2],
            determinismScore = computeDeterminism(outputs),
            timeoutCount = timeoutCount,
            errorCount = errorCount,
        )
    }

    /**
     * Compute determinism as average pairwise similarity across outputs.
     * Uses Jaccard similarity on word sets as a lightweight proxy.
     */
    fun computeDeterminism(outputs: List<String>): Double {
        if (outputs.size < 2) return 1.0
        val wordSets = outputs.map { it.lowercase().split(Regex("\\s+")).toSet() }
        var totalSim = 0.0
        var pairCount = 0
        for (i in wordSets.indices) {
            for (j in i + 1 until wordSets.size) {
                val intersection = wordSets[i].intersect(wordSets[j]).size.toDouble()
                val union = wordSets[i].union(wordSets[j]).size.toDouble()
                totalSim += if (union == 0.0) 1.0 else intersection / union
                pairCount++
            }
        }
        return if (pairCount == 0) 1.0 else totalSim / pairCount
    }
}
