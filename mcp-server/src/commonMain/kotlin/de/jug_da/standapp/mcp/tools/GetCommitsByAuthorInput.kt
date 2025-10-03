package de.jug_da.standapp.mcp.tools

import kotlinx.serialization.Serializable

@Serializable
data class GetCommitsByAuthorInput(
    val repoDir: String,
    val author: String,
    val startDate: String,
    val endDate: String
)