package de.jug_da.standapp.benchmark

import dev.standapp.engine.entity.CommitInfo
import dev.standapp.engine.entity.QualityScores

fun CommitEntry.toCommitInfo() = CommitInfo(
    id = id,
    authorName = authorName,
    authorEmail = authorEmail,
    date = whenDate,
    message = message,
)

val QualityScores.allPassed: Boolean
    get() {
        val checks = listOfNotNull(jsonParseable, jsonSchemaCompliant, headingsPresent)
        return checks.all { it } && allIdsValid && noHallucinatedIds
    }
