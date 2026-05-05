package de.jug_da.standapp.benchmark.engines

import de.jug_da.standapp.llm.LLMService

/**
 * Reflection-based discovery of benchmark-only LLM engines.
 *
 * The actual engine implementations (e.g. [DeliveranceLLMService]) live in
 * source sets that are conditionally included based on a Gradle property
 * (`-Pdeliverance.enabled=true` for Deliverance, `-Pqxotic.enabled=true` for
 * qxotic). When the property is unset the source set is excluded from
 * compilation, so the engine class isn't on the classpath at runtime.
 *
 * Going through this registry — instead of importing the engine class directly
 * — keeps `Main.kt` compileable in the default configuration (CI, fat JAR for
 * everyday use) without dragging the alternative-engine maven-local dependencies
 * onto the production classpath.
 */
object BenchmarkEngineRegistry {

    /**
     * Returns a factory that constructs a Deliverance-backed [LLMService] from
     * a HuggingFace `owner/name` spec, or `null` if the deliverance source set
     * was not compiled in.
     *
     * The factory is invoked once per backend by [BenchmarkRunner]; it's
     * deliberately *not* called eagerly here because model loading is a
     * heavyweight operation we only want to pay if the user asked for it.
     */
    fun deliveranceFactory(modelSpec: String): (() -> LLMService)? {
        val parts = modelSpec.split("/", limit = 2)
        if (parts.size != 2 || parts.any { it.isBlank() }) {
            System.err.println("[BenchmarkEngineRegistry] Invalid BENCH_DELIVERANCE_MODEL='$modelSpec' — expected 'owner/name'")
            return null
        }
        val (owner, name) = parts
        val klass = loadOptional("de.jug_da.standapp.benchmark.engines.DeliveranceLLMService") ?: return null
        return {
            val createMethod = klass.declaredMethods.firstOrNull {
                it.name == "create" && it.parameterCount == 2
            } ?: error("DeliveranceLLMService.create(owner, name) not found via reflection")
            createMethod.invoke(null, owner, name) as LLMService
        }
    }

    /**
     * Reserved for the qxotic engine wiring. Mirrors [deliveranceFactory] —
     * returns `null` if the qxotic source set wasn't compiled in.
     */
    fun qxoticFactory(modelSpec: String): (() -> LLMService)? {
        val klass = loadOptional("de.jug_da.standapp.benchmark.engines.QxoticLLMService") ?: return null
        return {
            val createMethod = klass.declaredMethods.firstOrNull {
                it.name == "create" && it.parameterCount == 1
            } ?: error("QxoticLLMService.create(spec) not found via reflection")
            createMethod.invoke(null, modelSpec) as LLMService
        }
    }

    private fun loadOptional(fqcn: String): Class<*>? = try {
        Class.forName(fqcn)
    } catch (_: ClassNotFoundException) {
        null
    } catch (t: Throwable) {
        System.err.println("[BenchmarkEngineRegistry] Failed to load $fqcn: ${t.message}")
        null
    }
}
