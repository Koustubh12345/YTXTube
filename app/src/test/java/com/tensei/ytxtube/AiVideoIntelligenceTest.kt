package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVideoIntelligenceTest {

    private val sampleCues = listOf(
        ParsedCue(0L, 4_000L, "Welcome to this Kotlin Jetpack Compose architecture tutorial."),
        ParsedCue(4_000L, 10_000L, "The most important concept in Compose is unidirectional data flow and state hoisting."),
        ParsedCue(45_000L, 52_000L, "Moving on to section two, let us benchmark rendering performance at 120 fps."),
        ParsedCue(52_000L, 60_000L, "Our benchmark shows a 42% reduction in frame drops when using derivedStateOf."),
        ParsedCue(110_000L, 118_000L, "Finally in summary, always keep heavy allocations outside composable functions."),
    )

    @Test
    fun classifiesTopicFromTitleAndTranscript() {
        val topic = AiTranscriptIntelligence.classifyTopic(
            title = "Jetpack Compose & Kotlin Coroutines Deep Dive",
            description = "Learn Android UI programming and compilers",
            cues = sampleCues,
        )
        assertEquals("Software & Coding", topic.primaryTopic)
        assertTrue(topic.confidence >= 0.5f)
        assertTrue(topic.matchedKeywords.contains("kotlin") || topic.matchedKeywords.contains("compose"))
    }

    @Test
    fun generatesSummaryKeyPointsMomentsAndChaptersFromCues() {
        val res = AiTranscriptIntelligence.analyze(
            videoId = "abc12345678",
            title = "Kotlin Compose Performance Guide",
            channel = "AndroidDev",
            description = "",
            durationSec = 130L,
            cues = sampleCues,
        )
        assertTrue(res.executiveSummary.isNotBlank())
        assertTrue(res.keyPoints.isNotEmpty())
        assertTrue(res.importantMoments.isNotEmpty())
        assertTrue(res.suggestedChapters.isNotEmpty())
        assertEquals(0L, res.suggestedChapters.first().startMs)
    }

    @Test
    fun answersTranscriptQuestionsWithTimestampCitations() {
        val qa = AiTranscriptIntelligence.answerQuestion(
            question = "How much reduction in frame drops did the benchmark show?",
            title = "Kotlin Compose Performance Guide",
            description = "",
            cues = sampleCues,
        )
        assertTrue(qa.answer.contains("42%"))
        assertFalse(qa.citations.isEmpty())
    }

    @Test
    fun searchesTranscriptByKeyword() {
        val hits = AiTranscriptIntelligence.searchTranscript(sampleCues, "unidirectional")
        assertEquals(1, hits.size)
        assertEquals(4_000L, hits.first().timeMs)
    }

    @Test
    fun enhancesSearchQueriesIntoFourVariants() {
        val enhanced = AiTranscriptIntelligence.enhanceSearchQuery("rust async runtime")
        assertEquals(4, enhanced.size)
        assertTrue(enhanced.all { it.query.contains("rust async runtime") })
    }
}
