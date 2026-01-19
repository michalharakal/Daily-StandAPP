package de.jug_da.standapp.llm

data class SkainetConfig(
    val modelPath: String,
    val maxSeqLen: Int = 2048,
    val defaultTemperature: Float = 0.8f,
    val ropeFreqBase: Float = 10000f,
    val eps: Float = 1e-5f
)

object LLMEngineConfig {
    fun getSkainetConfig(): SkainetConfig {
        return SkainetConfig(
            modelPath = System.getProperty("llm.model", "./models/model.gguf"),
            maxSeqLen = System.getProperty("llm.max.seq.len", "2048").toInt(),
            defaultTemperature = System.getProperty("llm.temperature", "0.8").toFloat(),
            ropeFreqBase = System.getProperty("llm.rope.freq.base", "10000").toFloat(),
            eps = System.getProperty("llm.eps", "1e-5").toFloat()
        )
    }
}
