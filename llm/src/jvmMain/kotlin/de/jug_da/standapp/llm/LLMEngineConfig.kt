package de.jug_da.standapp.llm

enum class LLMEngineType {
    JLAMA,
    SKAINET
}

data class SkainetConfig(
    val modelPath: String,
    val maxSeqLen: Int = 2048,
    val defaultTemperature: Float = 0.8f,
    val ropeFreqBase: Float = 10000f,
    val eps: Float = 1e-5f
)

object LLMEngineConfig {
    fun getEngineType(): LLMEngineType {
        val engine = System.getProperty("llm.engine", "jlama")
        return if (engine == "skainet") LLMEngineType.SKAINET else LLMEngineType.JLAMA
    }

    fun getSkainetConfig(): SkainetConfig {
        return SkainetConfig(
            modelPath = System.getProperty("llm.skainet.model", "./models/model.gguf"),
            maxSeqLen = System.getProperty("llm.max.seq.len", "2048").toInt()
        )
    }
}
