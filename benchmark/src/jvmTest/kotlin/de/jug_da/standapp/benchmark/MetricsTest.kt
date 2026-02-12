package de.jug_da.standapp.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsTest {

    @Test
    fun `percentile of single element`() {
        assertEquals(100L, Metrics.percentile(listOf(100L), 50.0))
        assertEquals(100L, Metrics.percentile(listOf(100L), 95.0))
    }

    @Test
    fun `percentile of sorted list`() {
        val values = (1L..100L).toList()
        assertEquals(50L, Metrics.percentile(values, 50.0))
        assertEquals(95L, Metrics.percentile(values, 95.0))
    }

    @Test
    fun `percentile of empty list returns 0`() {
        assertEquals(0L, Metrics.percentile(emptyList(), 50.0))
    }

    @Test
    fun `determinism of identical outputs is 1`() {
        val outputs = listOf("hello world", "hello world", "hello world")
        assertEquals(1.0, Metrics.computeDeterminism(outputs))
    }

    @Test
    fun `determinism of single output is 1`() {
        assertEquals(1.0, Metrics.computeDeterminism(listOf("hello")))
    }

    @Test
    fun `determinism of completely different outputs is near 0`() {
        val outputs = listOf("alpha beta gamma", "one two three", "x y z")
        val score = Metrics.computeDeterminism(outputs)
        assertTrue(score < 0.1, "Expected near 0, got $score")
    }

    @Test
    fun `determinism of similar outputs is high`() {
        val outputs = listOf(
            "Yesterday I worked on login feature and fixed bugs",
            "Yesterday I worked on the login feature and fixed some bugs",
            "Yesterday I worked on login feature and fixed a few bugs",
        )
        val score = Metrics.computeDeterminism(outputs)
        assertTrue(score > 0.5, "Expected > 0.5, got $score")
    }

    @Test
    fun `heap usage returns positive value`() {
        assertTrue(Metrics.heapUsageMb() > 0)
    }
}
