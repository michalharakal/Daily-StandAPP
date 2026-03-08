package dev.standapp.engine.entity

import kotlinx.serialization.Serializable

@Serializable
data class CommitInfo(
    val id: String,
    val authorName: String,
    val authorEmail: String,
    val date: String,
    val message: String,
)
