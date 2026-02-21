package de.jug_da.standapp.benchmark

import de.jug_da.data.git.GitInfo
import dev.standapp.engine.entity.CommitInfo
import dev.standapp.engine.entity.QualityScores

fun CommitEntry.toCommitInfo() = CommitInfo(
    id = id,
    authorName = authorName,
    authorEmail = authorEmail,
    date = whenDate,
    message = message,
)

fun GitInfo.toCommitInfo() = CommitInfo(
    id = id,
    authorName = authorName,
    authorEmail = authorEmail,
    date = whenDate.toString(),
    message = message,
)

val QualityScores.allPassed: Boolean
    get() {
        val checks = listOfNotNull(jsonParseable, jsonSchemaCompliant, headingsPresent)
        return checks.all { it } && allIdsValid && noHallucinatedIds
    }
