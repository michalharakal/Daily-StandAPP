package de.jug_da.data.git

import kotlin.time.Instant

data class GitInfo(val id: String, val authorName: String, val authorEmail: String, val whenDate: Instant, val message: String, )
