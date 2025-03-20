package de.jug_da.data.git

import kotlinx.datetime.Instant

expect fun commitsByPeriod(start: Instant, end: Instant): List<GitInfo>