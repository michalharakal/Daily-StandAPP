package de.jug_da.standapp.mcp.tools

import kotlinx.serialization.Serializable

@Serializable
data class GetAllCommitsInput(
    val repoDir: String,
    val startDate: String,
    val endDate: String
)