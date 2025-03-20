package de.jug_da.data.git

import kotlinx.datetime.Instant

expect fun commitsByAuthorAndPeriod(author: String, start: Instant, end: Instant): List<GitInfo>