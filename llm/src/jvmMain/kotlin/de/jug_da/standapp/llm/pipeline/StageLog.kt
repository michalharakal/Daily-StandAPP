package de.jug_da.standapp.llm.pipeline

/**
 * `[STAGE/...]` trace lines on stderr so a human running the CLI can follow
 * what each pipeline stage and model is doing. stdout stays reserved for the
 * rendered summary.
 */
object StageLog {
    fun line(text: String) = System.err.println(text)

    fun stage(stage: String, detail: String) = System.err.println("[STAGE/$stage] $detail")

    fun block(label: String, body: String) {
        System.err.println("[STAGE/$label] >>>")
        System.err.println(body)
        System.err.println("[STAGE/$label] <<<")
    }
}
