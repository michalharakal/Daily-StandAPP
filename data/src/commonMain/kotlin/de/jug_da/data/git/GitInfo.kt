package de.jug_da.data.git

import kotlinx.datetime.Instant

data class GitInfo(val id: String, val authorName: String, val authorEmail: String, val whenDate: Instant, val message: String, )
