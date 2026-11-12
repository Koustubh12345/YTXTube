package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ============================================================================
// CATEGORY 1: AI VIDEO INTELLIGENCE (Features 1–15)
// Optional, privacy-first on-device NLP engine + user-consented Local/External LLM
// ============================================================================

enum class AiProviderMode(val label: String, val subtitle: String, val usesNetwork: Boolean) {
    OnDevice("On-Device NLP (Default)", "100% offline extractive TF-IDF & BM25 intelligence; zero data leaves device", false),
    LocalServer("Local AI Server (Ollama / LocalAI)", "Connects to a local LAN/loopback endpoint (e.g. http://127.0.0.1:11434)", true),
    ExternalOpenAi("External OpenAI-Compatible API", "Uses your own API key (stored in Android Keystore) with explicit permission", true),
    ExternalGemini("External Google Gemini API", "Uses your own Gemini API key with explicit per-session permission", true),
}

@Immutable
data class TimestampedBullet(
    val timeMs: Long,
    val text: String,
    val badge: String? = null,
    val score: Float = 0f,
)

@Immutable
data class VideoTopicClassification(
    val primaryTopic: String,
    val secondaryTopic: String?,
    val confidence: Float,
    val matchedKeywords: List<String>,
)

@Immutable
data class TranscriptQaAnswer(
    val question: String,
    val answer: String,
    val citations: List<TimestampedBullet>,
    val providerUsed: String,
)

@Immutable
data class AiVideoAnalysis(
    val videoId: String,
    val executiveSummary: String,
    val timeBucketSummary: List<TimestampedBullet>,
    val keyPoints: List<TimestampedBullet>,
    val importantMoments: List<TimestampedBullet>,
    val suggestedChapters: List<Chapter>,
    val topic: VideoTopicClassification,
    val cueCount: Int,
    val providerUsed: String,
)

@Immutable
data class EnhancedSearchQuery(
    val label: String,
    val query: String,
    val reason: String,
)

/**
 * Pure, deterministic On-Device Video & Transcript Intelligence Engine (Features 1–12).
 * Kept free of Android context so every algorithm can be unit-tested on the JVM.
 */
object AiTranscriptIntelligence {

    private val stopWords = setOf(
        "the", "and", "that", "this", "with", "from", "your", "have", "what", "when", "where", "which",
        "will", "into", "about", "they", "their", "them", "then", "than", "just", "more", "most", "some",
        "such", "only", "other", "also", "very", "here", "there", "been", "were", "being", "because",
        "would", "could", "should", "really", "going", "think", "know", "like", "make", "time", "people",
        "video", "watch", "subscribe", "channel", "today", "guys", "welcome", "back", "right", "actually",
    )

    private val topicLexicon: Map<String, Set<String>> = mapOf(
        "Software & Coding" to setOf(
            "kotlin", "java", "python", "rust", "golang", "typescript", "javascript", "react", "compose",
            "android", "compiler", "algorithm", "code", "coding", "programming", "developer", "github",
            "api", "backend", "frontend", "database", "sql", "linux", "docker", "kubernetes", "git",
        ),
        "Science & Engineering" to setOf(
            "physics", "quantum", "chemistry", "biology", "space", "nasa", "rocket", "orbit", "astronomy",
            "engineering", "circuit", "robotics", "mathematics", "theorem", "calculus", "experiment", "research",
        ),
        "Hardware & Tech" to setOf(
            "cpu", "gpu", "nvidia", "amd", "intel", "apple", "silicon", "smartphone", "laptop", "display",
            "oled", "amoled", "battery", "camera", "sensor", "benchmark", "review", "unboxing", "hardware",
        ),
        "AI & Machine Learning" to setOf(
            "neural", "network", "transformer", "llm", "model", "training", "inference", "openai", "gemini",
            "claude", "ollama", "artificial", "intelligence", "deep", "learning", "dataset", "tokens",
        ),
        "Music & Audio" to setOf(
            "music", "song", "album", "lyrics", "vocal", "guitar", "piano", "synth", "drums", "mix",
            "mastering", "equalizer", "acoustic", "concert", "live", "official", "remix", "beat", "chord",
        ),
        "Gaming" to setOf(
            "game", "gaming", "gameplay", "walkthrough", "speedrun", "boss", "playstation", "xbox",
            "nintendo", "steam", "fps", "rpg", "esports", "multiplayer", "shader", "minecraft",
        ),
        "Education & Lectures" to setOf(
            "lecture", "course", "tutorial", "lesson", "explained", "introduction", "guide", "university",
            "professor", "study", "exam", "history", "philosophy", "economics", "psychology", "learn",
        ),
        "Design & Creative" to setOf(
            "design", "ui", "ux", "figma", "typography", "animation", "motion", "color", "shader", "glass",
            "illustration", "editing", "cinema", "photography", "lighting", "render", "blender",
        ),
    )

    private val hookPhrases = listOf(
        "most important", "key takeaway", "remember that", "in summary", "the problem is",
        "the solution", "here is how", "secret is", "crucial", "essential", "step one",
        "step 1", "first thing", "main reason", "conclusion", "bottom line", "pro tip",
        "warning", "mistake", "benchmark", "result", "compared to",
    )

    private val chapterMarkers = listOf(
        "first", "second", "third", "next up", "moving on", "now let", "another",
        "part ", "step ", "section", "finally", "in conclusion", "to wrap up", "overview",
    )

    fun tokenize(text: String): List<String> =
        text.lowercase(Locale.US)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in stopWords && !it.all(Char::isDigit) }

    /** Feature 8: Video topic classification. */
    fun classifyTopic(title: String, description: String = "", cues: List<ParsedCue> = emptyList()): VideoTopicClassification {
        val sampleTranscript = cues.take(80).joinToString(" ") { it.text }
        val tokens = tokenize("$title $title $title $description $sampleTranscript")
        if (tokens.isEmpty()) {
            return VideoTopicClassification("General Video", null, 0.35f, emptyList())
        }
        val scores = LinkedHashMap<String, Int>()
        val matchedByTopic = LinkedHashMap<String, MutableSet<String>>()
        for ((topic, lex) in topicLexicon) {
            var count = 0
            val matched = LinkedHashSet<String>()
            for (t in tokens) {
                if (t in lex) {
                    count++
                    matched.add(t)
                }
            }
            if (count > 0) {
                scores[topic] = count
                matchedByTopic[topic] = matched
            }
        }
        if (scores.isEmpty()) {
            return VideoTopicClassification("General & Entertainment", null, 0.45f, tokens.distinct().take(4))
        }
        val sorted = scores.entries.sortedByDescending { it.value }
        val primary = sorted[0].key
        val secondary = sorted.getOrNull(1)?.key
        val totalHits = sorted.sumOf { it.value }.coerceAtLeast(1)
        val conf = (0.5f + 0.48f * (sorted[0].value.toFloat() / totalHits)).coerceIn(0.5f, 0.98f)
        return VideoTopicClassification(
            primaryTopic = primary,
            secondaryTopic = secondary,
            confidence = conf,
            matchedKeywords = matchedByTopic[primary].orEmpty().take(6),
        )
    }

    /** Feature 1, 2, 5, 6, 7: Full deterministic analysis of video & transcript cues. */
    fun analyze(
        videoId: String,
        title: String,
        channel: String,
        description: String,
        durationSec: Long,
        cues: List<ParsedCue>,
        existingChapters: List<Chapter> = emptyList(),
    ): AiVideoAnalysis {
        val topic = classifyTopic(title, description, cues)
        if (cues.isEmpty()) {
            // Fallback to description + title intelligence when captions are unavailable
            val descLines = description.lines().map { it.trim() }.filter { it.length >= 20 && !it.startsWith("http") }
            val exec = if (descLines.isNotEmpty()) {
                descLines.take(3).joinToString(" ")
            } else {
                "$title by ${channel.ifBlank { "this creator" }} covers ${topic.primaryTopic.lowercase(Locale.US)}" +
                    (if (topic.matchedKeywords.isNotEmpty()) " (${topic.matchedKeywords.joinToString(", ")})" else "") + "."
            }
            val keyPts = descLines.take(5).mapIndexed { i, line ->
                TimestampedBullet(i * 60_000L, line.take(140), "Description Point #${i + 1}", 1f)
            }.ifEmpty {
                listOf(TimestampedBullet(0L, "Overview: $title (${topic.primaryTopic})", "Overview", 1f))
            }
            return AiVideoAnalysis(
                videoId = videoId,
                executiveSummary = exec,
                timeBucketSummary = keyPts,
                keyPoints = keyPts,
                importantMoments = existingChapters.map { TimestampedBullet(it.startMs, it.title, "Chapter", 1f) },
                suggestedChapters = existingChapters,
                topic = topic,
                cueCount = 0,
                providerUsed = "On-Device Metadata Analyzer (No Captions on Video)",
            )
        }

        // Merge tiny consecutive cues into coherent ~12-second windows for cleaner NLP sentences
        val windows = coalesceCues(cues, windowMs = 12_000L)
        val df = HashMap<String, Int>()
        val windowTokens = windows.map { w ->
            val toks = tokenize(w.text)
            toks.distinct().forEach { df[it] = (df[it] ?: 0) + 1 }
            toks
        }
        val titleTokens = tokenize(title).toSet()
        val nDocs = windows.size.coerceAtLeast(1)

        // Score each window by TF-IDF + title overlap + hook phrase presence + numerical density
        val scoredWindows = windows.mapIndexed { idx, w ->
            val toks = windowTokens[idx]
            val tfIdf = if (toks.isEmpty()) 0f else {
                toks.sumOf { t ->
                    val docFreq = df[t] ?: 1
                    val idf = ln(1.0 + nDocs.toDouble() / docFreq.toDouble())
                    val titleBoost = if (t in titleTokens) 1.75 else 1.0
                    idf * titleBoost
                }.toFloat() / sqrt(toks.size.toFloat() + 2f)
            }
            val lower = w.text.lowercase(Locale.US)
            val hookBoost = if (hookPhrases.any { lower.contains(it) }) 1.6f else 0f
            val numberBoost = if (Regex("\\b\\d+(?:\\.\\d+)?%?\\b").containsMatchIn(w.text)) 0.55f else 0f
            val posBoost = if (idx == 0 || idx == windows.lastIndex) 0.35f else 0f
            val totalScore = tfIdf + hookBoost + numberBoost + posBoost
            TimestampedBullet(
                timeMs = w.startMs,
                text = cleanSentence(w.text),
                badge = when {
                    hookBoost > 0f -> "Key Insight"
                    numberBoost > 0f -> "Data / Metric"
                    idx == 0 -> "Opening Thesis"
                    else -> "Core Point"
                },
                score = totalScore,
            )
        }

        // Feature 1: Executive Summary (top 3 non-overlapping windows in chronological order)
        val topThree = selectNonOverlapping(scoredWindows, count = 3, minGapMs = 25_000L)
            .sortedBy { it.timeMs }
        val executiveSummary = buildString {
            append("${topic.primaryTopic}: ")
            append(topThree.joinToString(" ") { it.text })
        }

        // Feature 2: Automatic transcript summarisation by time buckets
        val totalDurationMs = max(durationSec * 1000L, cues.last().endMs).coerceAtLeast(60_000L)
        val bucketCount = (totalDurationMs / 120_000L).toInt().coerceIn(3, 8)
        val bucketSpan = totalDurationMs / bucketCount
        val timeBucketSummary = (0 until bucketCount).mapNotNull { b ->
            val from = b * bucketSpan
            val to = if (b == bucketCount - 1) Long.MAX_VALUE else (b + 1) * bucketSpan
            scoredWindows.filter { it.timeMs in from until to }
                .maxByOrNull { it.score }
                ?.copy(badge = "Section ${b + 1}")
        }

        // Feature 7: Key-point extraction (top 6 distinct points)
        val keyPoints = selectNonOverlapping(scoredWindows, count = 6, minGapMs = 20_000L)
            .sortedBy { it.timeMs }

        // Feature 6: Important moments detection (hooks, metrics, emphasis peaks)
        val importantMoments = selectNonOverlapping(
            scoredWindows.sortedByDescending { it.score },
            count = 5,
            minGapMs = 30_000L,
        ).sortedBy { it.timeMs }

        // Feature 5: Automatic chapter suggestions
        val suggestedChapters = if (existingChapters.size >= 2) {
            existingChapters
        } else {
            generateChapters(windows, windowTokens, totalDurationMs)
        }

        return AiVideoAnalysis(
            videoId = videoId,
            executiveSummary = executiveSummary,
            timeBucketSummary = timeBucketSummary,
            keyPoints = keyPoints,
            importantMoments = importantMoments,
            suggestedChapters = suggestedChapters,
            topic = topic,
            cueCount = cues.size,
            providerUsed = "On-Device Extractive NLP (${cues.size} cues analyzed)",
        )
    }

    /** Feature 3: Ask questions about video transcripts (BM25 + contextual window synthesis). */
    fun answerQuestion(
        question: String,
        title: String,
        description: String,
        cues: List<ParsedCue>,
    ): TranscriptQaAnswer {
        val qTokens = tokenize(question)
        if (qTokens.isEmpty()) {
            return TranscriptQaAnswer(
                question = question,
                answer = "Please enter a specific question about the video topic or transcript.",
                citations = emptyList(),
                providerUsed = "On-Device BM25 QA",
            )
        }
        val windows = coalesceCues(cues, windowMs = 14_000L)
        if (windows.isEmpty()) {
            val descMatch = description.lines()
                .filter { line -> qTokens.any { line.contains(it, ignoreCase = true) } }
                .take(3)
            val ans = if (descMatch.isNotEmpty()) {
                descMatch.joinToString(" ")
            } else {
                "This video does not have transcript cues loaded yet, and no exact match for '${qTokens.joinToString(" ")}' was found in the description."
            }
            return TranscriptQaAnswer(question, ans, emptyList(), "On-Device Description QA")
        }

        val df = HashMap<String, Int>()
        val docTokens = windows.map { w ->
            val t = tokenize(w.text)
            t.distinct().forEach { df[it] = (df[it] ?: 0) + 1 }
            t
        }
        val avgDl = docTokens.map { it.size }.average().coerceAtLeast(1.0)
        val nDocs = windows.size

        val ranked = windows.mapIndexedNotNull { idx, w ->
            val toks = docTokens[idx]
            val lower = w.text.lowercase(Locale.US)
            var score = 0.0
            for (qt in qTokens) {
                val tf = toks.count { it == qt || it.startsWith(qt) || qt.startsWith(it) }
                val subMatch = if (tf == 0 && lower.contains(qt)) 1 else tf
                if (subMatch > 0) {
                    val docFreq = df[qt] ?: 1
                    val idf = ln(1.0 + (nDocs - docFreq + 0.5) / (docFreq + 0.5))
                    val k1 = 1.5
                    val b = 0.75
                    val normTf = (subMatch * (k1 + 1.0)) / (subMatch + k1 * (1.0 - b + b * (toks.size / avgDl)))
                    score += idf * normTf
                }
            }
            if (score > 0.0) {
                TimestampedBullet(w.startMs, cleanSentence(w.text), "Match (${"%.1f".format(Locale.US, score)})", score.toFloat())
            } else null
        }.sortedByDescending { it.score }

        val topCitations = selectNonOverlapping(ranked, count = 3, minGapMs = 12_000L)
        val synthesized = if (topCitations.isNotEmpty()) {
            topCitations.sortedBy { it.timeMs }.joinToString(" ") { "[${formatDuration(it.timeMs)}] ${it.text}" }
        } else {
            val fallback = analyze(
                videoId = "",
                title = title,
                channel = "",
                description = description,
                durationSec = 0L,
                cues = cues,
            ).executiveSummary
            "No exact phrase match for '${qTokens.joinToString(" ")}', but here is the closest video context: $fallback"
        }

        return TranscriptQaAnswer(
            question = question,
            answer = synthesized,
            citations = topCitations,
            providerUsed = "On-Device BM25 Transcript QA",
        )
    }

    /** Feature 4: Transcript-based search. */
    fun searchTranscript(cues: List<ParsedCue>, query: String): List<TimestampedBullet> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isBlank()) return emptyList()
        val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        return cues.mapNotNull { cue ->
            val lower = cue.text.lowercase(Locale.US)
            if (terms.all { lower.contains(it) }) {
                TimestampedBullet(cue.startMs, cue.text.replace("\n", " "), "Match")
            } else null
        }
    }

    /** Feature 5: Automatic chapter suggestion generator. */
    fun generateChapters(
        windows: List<ParsedCue>,
        windowTokens: List<List<String>>,
        totalDurationMs: Long,
    ): List<Chapter> {
        if (windows.isEmpty()) return emptyList()
        val chapters = mutableListOf(Chapter("Introduction", 0L))
        val minChapterGapMs = max(35_000L, totalDurationMs / 10L)
        var lastChapterMs = 0L

        for (i in 1 until windows.size) {
            val w = windows[i]
            if (w.startMs - lastChapterMs < minChapterGapMs) continue
            val prevSet = windowTokens[i - 1].toSet()
            val curSet = windowTokens[i].toSet()
            val union = (prevSet + curSet).size.coerceAtLeast(1)
            val jaccard = prevSet.intersect(curSet).size.toFloat() / union.toFloat()
            val lower = w.text.lowercase(Locale.US)
            val hasMarker = chapterMarkers.any { lower.contains(it) }
            val pauseGapMs = w.startMs - windows[i - 1].endMs

            if (hasMarker || (jaccard < 0.08f && curSet.size >= 4) || pauseGapMs >= 3_500L) {
                val titleWords = windowTokens[i].take(4).joinToString(" ") { word ->
                    word.replaceFirstChar { it.titlecase(Locale.US) }
                }.ifBlank { "Section ${chapters.size + 1}" }
                chapters.add(Chapter(titleWords, w.startMs))
                lastChapterMs = w.startMs
                if (chapters.size >= 10) break
            }
        }
        return chapters
    }

    /** Feature 11: Search query enhancement. */
    fun enhanceSearchQuery(rawQuery: String): List<EnhancedSearchQuery> {
        val base = rawQuery.trim()
        if (base.isBlank()) return emptyList()
        val topic = classifyTopic(base)
        return listOf(
            EnhancedSearchQuery("Deep Dive", "$base full technical breakdown explained", "Long-form comprehensive analysis"),
            EnhancedSearchQuery("Step-by-Step Guide", "$base tutorial guide walkthrough", "Hands-on practical tutorial"),
            EnhancedSearchQuery("No-Clickbait Comparison", "$base honest review benchmark comparison", "Direct evidence-focused results"),
            EnhancedSearchQuery("Recent (${topic.primaryTopic})", "$base 2026 latest update", "Fresh uploads in ${topic.primaryTopic}"),
        )
    }

    /** Feature 9: Smart playlist generation from candidate pool. */
    fun generateSmartPlaylist(
        candidates: List<VideoItem>,
        targetTopic: String?,
        maxDurationMinutes: Int,
    ): List<VideoItem> {
        val budgetSec = (maxDurationMinutes.coerceAtLeast(5)) * 60L
        val scored = candidates.distinctBy { it.id }.map { video ->
            val t = classifyTopic(video.title)
            val topicScore = when {
                targetTopic.isNullOrBlank() || targetTopic == "All" -> 1.0f
                t.primaryTopic.equals(targetTopic, ignoreCase = true) -> 3.0f
                t.secondaryTopic.equals(targetTopic, ignoreCase = true) -> 1.8f
                else -> 0.4f
            }
            val userInterest = AiIntelligenceSettings.interestWeights[t.primaryTopic] ?: 1.0f
            video to (topicScore * userInterest)
        }.sortedByDescending { it.second }

        val picked = mutableListOf<VideoItem>()
        var totalSec = 0L
        for ((video, _) in scored) {
            val dur = if (video.durationSec > 0) video.durationSec else 300L
            if (picked.isEmpty() || totalSec + dur <= budgetSec) {
                picked.add(video)
                totalSec += dur
            }
            if (picked.size >= 20 || totalSec >= budgetSec) break
        }
        return picked
    }

    private fun coalesceCues(cues: List<ParsedCue>, windowMs: Long): List<ParsedCue> {
        if (cues.isEmpty()) return emptyList()
        val out = mutableListOf<ParsedCue>()
        var start = cues.first().startMs
        var end = cues.first().endMs
        val sb = StringBuilder(cues.first().text)

        for (i in 1 until cues.size) {
            val c = cues[i]
            if (c.endMs - start <= windowMs && sb.length < 240) {
                sb.append(' ').append(c.text)
                end = max(end, c.endMs)
            } else {
                out.add(ParsedCue(start, end, sb.toString().replace(Regex("\\s+"), " ").trim()))
                start = c.startMs
                end = c.endMs
                sb.setLength(0)
                sb.append(c.text)
            }
        }
        if (sb.isNotEmpty()) {
            out.add(ParsedCue(start, end, sb.toString().replace(Regex("\\s+"), " ").trim()))
        }
        return out
    }

    private fun selectNonOverlapping(
        items: List<TimestampedBullet>,
        count: Int,
        minGapMs: Long,
    ): List<TimestampedBullet> {
        val sorted = items.sortedByDescending { it.score }
        val chosen = mutableListOf<TimestampedBullet>()
        for (candidate in sorted) {
            if (chosen.none { kotlin.math.abs(it.timeMs - candidate.timeMs) < minGapMs }) {
                chosen.add(candidate)
                if (chosen.size >= count) break
            }
        }
        return chosen
    }

    private fun cleanSentence(raw: String): String {
        val t = raw.replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return t
        val cap = t.replaceFirstChar { it.titlecase(Locale.US) }
        return if (cap.endsWith(".") || cap.endsWith("!") || cap.endsWith("?")) cap else "$cap."
    }
}

/**
 * Features 13, 14, 15: AI Provider Configuration, Secure Key Management & Privacy/Usage Dashboard.
 */
object AiIntelligenceSettings {
    private const val PREFS = "ai_intelligence_v292"
    private const val SECURE_PREFS = "ai_secure_keys"
    private var prefs: SharedPreferences? = null
    private var secureStore: SecureStore? = null

    var enabled by mutableStateOf(true)
        private set
    var providerMode by mutableStateOf(AiProviderMode.OnDevice)
        private set
    var localEndpointUrl by mutableStateOf("http://127.0.0.1:11434/api/generate")
        private set
    var localModelName by mutableStateOf("llama3.2:3b")
        private set
    var externalEndpointUrl by mutableStateOf("https://api.openai.com/v1/chat/completions")
        private set
    var externalModelName by mutableStateOf("gpt-4o-mini")
        private set
    var explicitNetworkConsent by mutableStateOf(false)
        private set

    // Feature 15: Usage & privacy audit counters
    var localAnalysesCount by mutableIntStateOf(0)
        private set
    var networkRequestsCount by mutableIntStateOf(0)
        private set
    var estimatedTokensProcessed by mutableLongStateOf(0L)
        private set
    var lastAuditEvent by mutableStateOf("No external AI calls made (100% local)")
        private set

    // Feature 12: Personalised content discovery interest weights
    val interestWeights = mutableStateMapOf<String, Float>()
    val analysisCache = HashMap<String, AiVideoAnalysis>()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        secureStore = runCatching { SecureStore(context, SECURE_PREFS) }.getOrNull()
        enabled = p.getBoolean("enabled", true)
        providerMode = AiProviderMode.entries.firstOrNull {
            it.name == p.getString("provider_mode", "OnDevice")
        } ?: AiProviderMode.OnDevice
        localEndpointUrl = p.getString("local_url", "http://127.0.0.1:11434/api/generate") ?: "http://127.0.0.1:11434/api/generate"
        localModelName = p.getString("local_model", "llama3.2:3b") ?: "llama3.2:3b"
        externalEndpointUrl = p.getString("external_url", "https://api.openai.com/v1/chat/completions") ?: "https://api.openai.com/v1/chat/completions"
        externalModelName = p.getString("external_model", "gpt-4o-mini") ?: "gpt-4o-mini"
        explicitNetworkConsent = p.getBoolean("explicit_network_consent", false)
        localAnalysesCount = p.getInt("local_analyses", 0)
        networkRequestsCount = p.getInt("network_requests", 0)
        estimatedTokensProcessed = p.getLong("tokens_processed", 0L)
        lastAuditEvent = p.getString("last_audit", "No external AI calls made (100% local)") ?: "No external AI calls made (100% local)"

        interestWeights.clear()
        runCatching {
            val obj = JSONObject(p.getString("interest_weights", "{}"))
            for (k in obj.keys()) {
                interestWeights[k] = obj.optDouble(k, 1.0).toFloat().coerceIn(0.1f, 3.0f)
            }
        }
    }

    var apiKey: String
        get() = secureStore?.get("external_ai_api_key").orEmpty()
        set(value) {
            secureStore?.put("external_ai_api_key", value.trim().ifBlank { null })
        }

    fun hasConfiguredApiKey(): Boolean = apiKey.isNotBlank()

    fun maskedApiKey(): String {
        val k = apiKey
        if (k.length <= 8) return if (k.isEmpty()) "Not set" else "••••••••"
        return "${k.take(4)}••••••••${k.takeLast(4)}"
    }

    fun updateEnabled(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
    }

    fun updateProvider(
        mode: AiProviderMode,
        localUrl: String = localEndpointUrl,
        localModel: String = localModelName,
        extUrl: String = externalEndpointUrl,
        extModel: String = externalModelName,
        consent: Boolean = explicitNetworkConsent,
    ) {
        providerMode = mode
        localEndpointUrl = localUrl.trim()
        localModelName = localModel.trim()
        externalEndpointUrl = extUrl.trim()
        externalModelName = extModel.trim()
        explicitNetworkConsent = consent
        prefs?.edit()
            ?.putString("provider_mode", mode.name)
            ?.putString("local_url", localEndpointUrl)
            ?.putString("local_model", localModelName)
            ?.putString("external_url", externalEndpointUrl)
            ?.putString("external_model", externalModelName)
            ?.putBoolean("explicit_network_consent", consent)
            ?.apply()
    }

    fun updateInterestWeight(topic: String, weight: Float) {
        val clamped = weight.coerceIn(0.2f, 2.5f)
        interestWeights[topic] = clamped
        val obj = JSONObject()
        interestWeights.forEach { (k, v) -> obj.put(k, v.toDouble()) }
        prefs?.edit()?.putString("interest_weights", obj.toString())?.apply()
    }

    fun recordTopicWatch(video: VideoItem) {
        if (!enabled || Tweaks.noWatchHistory) return
        val topic = AiTranscriptIntelligence.classifyTopic(video.title).primaryTopic
        val current = interestWeights[topic] ?: 1.0f
        updateInterestWeight(topic, (current + 0.12f).coerceAtMost(2.5f))
    }

    internal fun recordUsage(network: Boolean, tokens: Int, detail: String) {
        if (network) networkRequestsCount++ else localAnalysesCount++
        estimatedTokensProcessed += tokens.coerceAtLeast(1)
        lastAuditEvent = detail
        prefs?.edit()
            ?.putInt("local_analyses", localAnalysesCount)
            ?.putInt("network_requests", networkRequestsCount)
            ?.putLong("tokens_processed", estimatedTokensProcessed)
            ?.putString("last_audit", lastAuditEvent)
            ?.apply()
    }

    fun resetUsageAndKeys() {
        apiKey = ""
        explicitNetworkConsent = false
        providerMode = AiProviderMode.OnDevice
        localAnalysesCount = 0
        networkRequestsCount = 0
        estimatedTokensProcessed = 0L
        lastAuditEvent = "Cleared all AI history & revoked keys"
        interestWeights.clear()
        analysisCache.clear()
        prefs?.edit()?.clear()?.apply()
    }

    /**
     * Optional LLM prompt runner (Features 13 & 14).
     * Never sends data unless [enabled], [providerMode.usesNetwork], and [explicitNetworkConsent] are all true.
     */
    suspend fun queryOptionalProvider(systemPrompt: String, userPrompt: String): String? = withContext(Dispatchers.IO) {
        if (!enabled || !providerMode.usesNetwork || !explicitNetworkConsent) return@withContext null
        val tokenEst = (systemPrompt.length + userPrompt.length) / 4
        runCatching {
            when (providerMode) {
                AiProviderMode.OnDevice -> null
                AiProviderMode.LocalServer -> {
                    val payload = JSONObject()
                        .put("model", localModelName)
                        .put("prompt", "$systemPrompt\n\n$userPrompt")
                        .put("stream", false)
                    val req = Request.Builder()
                        .url(localEndpointUrl)
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    Net.client.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) return@use null
                        val text = JSONObject(res.body?.string().orEmpty()).optString("response").trim()
                        if (text.isNotEmpty()) {
                            recordUsage(true, tokenEst, "Local AI Server ($localModelName)")
                            text
                        } else null
                    }
                }
                AiProviderMode.ExternalOpenAi -> {
                    val key = apiKey.takeIf { it.isNotBlank() } ?: return@runCatching null
                    val messages = JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", userPrompt))
                    val payload = JSONObject()
                        .put("model", externalModelName)
                        .put("messages", messages)
                        .put("temperature", 0.3)
                    val req = Request.Builder()
                        .url(externalEndpointUrl)
                        .header("Authorization", "Bearer $key")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    Net.client.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) return@use null
                        val content = JSONObject(res.body?.string().orEmpty())
                            .optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content")
                            ?.trim()
                        if (!content.isNullOrEmpty()) {
                            recordUsage(true, tokenEst, "External OpenAI ($externalModelName)")
                            content
                        } else null
                    }
                }
                AiProviderMode.ExternalGemini -> {
                    val key = apiKey.takeIf { it.isNotBlank() } ?: return@runCatching null
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/${externalModelName.ifBlank { "gemini-2.5-flash" }}:generateContent?key=$key"
                    val payload = JSONObject().put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", "$systemPrompt\n\n$userPrompt")),
                            ),
                        ),
                    )
                    val req = Request.Builder()
                        .url(url)
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    Net.client.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) return@use null
                        val content = JSONObject(res.body?.string().orEmpty())
                            .optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text")
                            ?.trim()
                        if (!content.isNullOrEmpty()) {
                            recordUsage(true, tokenEst, "External Gemini ($externalModelName)")
                            content
                        } else null
                    }
                }
            }
        }.getOrNull()
    }
}

/**
 * Interactive AI Video Intelligence Bottom Sheet (Features 1–15).
 * Accessible from the Watch Page, Player Settings, and Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiVideoIntelligenceSheet(
    player: PlayerState,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val item = player.current
    val details = player.details

    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Summary & Key Points", "Ask & Search", "Auto Chapters", "Smart Playlist", "AI Provider & Privacy")

    var cues by remember(item?.id) { mutableStateOf<List<ParsedCue>>(emptyList()) }
    var loadingCues by remember(item?.id) { mutableStateOf(false) }
    var analysis by remember(item?.id) { mutableStateOf(item?.id?.let { AiIntelligenceSettings.analysisCache[it] }) }

    LaunchedEffect(item?.id) {
        val vid = item?.id ?: return@LaunchedEffect
        loadingCues = true
        val fetchedCues = withContext(Dispatchers.IO) {
            val info = StreamCache.get(vid) ?: runCatching { StreamCache.getOrLoad(vid) }.getOrNull()
            val trackUrl = info?.subtitles?.firstOrNull { it.isUrl && it.content.isNotBlank() }?.content
                ?: CaptionTracks.get(vid)?.firstOrNull()?.url
                ?: runCatching { CaptionTracks.load(vid).firstOrNull()?.url }.getOrNull()
            if (trackUrl != null) SubtitleParser.fetchAndParse(trackUrl) else emptyList()
        }
        cues = fetchedCues
        val computed = withContext(Dispatchers.Default) {
            AiTranscriptIntelligence.analyze(
                videoId = vid,
                title = item.title,
                channel = item.channel,
                description = details?.description.orEmpty(),
                durationSec = item.durationSec,
                cues = fetchedCues,
                existingChapters = details?.chapters.orEmpty(),
            )
        }
        AiIntelligenceSettings.recordUsage(
            network = false,
            tokens = (details?.description?.length ?: 0) / 4 + fetchedCues.size * 8,
            detail = "On-device analysis for ${item.title.take(28)}",
        )
        // Optional external/local upgrade if explicitly consented
        val llmSummary = if (AiIntelligenceSettings.providerMode.usesNetwork && AiIntelligenceSettings.explicitNetworkConsent) {
            val sampleText = fetchedCues.take(120).joinToString(" ") { it.text }.ifBlank { details?.description.orEmpty() }
            AiIntelligenceSettings.queryOptionalProvider(
                systemPrompt = "Summarize this video concisely in 3 sentences, focusing on concrete facts and key takeaways.",
                userPrompt = "Title: ${item.title}\nChannel: ${item.channel}\nTranscript: ${sampleText.take(4000)}",
            )
        } else null
        val finalAnalysis = if (llmSummary != null) {
            computed.copy(executiveSummary = llmSummary, providerUsed = AiIntelligenceSettings.providerMode.label)
        } else computed
        analysis = finalAnalysis
        AiIntelligenceSettings.analysisCache[vid] = finalAnalysis
        loadingCues = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "AI Video Intelligence",
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                    )
                    Text(
                        analysis?.providerUsed ?: AiIntelligenceSettings.providerMode.label,
                        color = c.primary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Switch(
                    checked = AiIntelligenceSettings.enabled,
                    onCheckedChange = AiIntelligenceSettings::updateEnabled,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEachIndexed { i, title ->
                    FilterChip(
                        selected = tab == i,
                        onClick = { tab = i },
                        label = { Text(title, fontWeight = FontWeight.SemiBold) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (!AiIntelligenceSettings.enabled && tab != 4) {
                Text(
                    "AI Video Intelligence is currently switched off. Enable it above or configure privacy controls in 'AI Provider & Privacy'.",
                    color = c.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                when (tab) {
                    0 -> AiSummaryAndKeyPointsTab(loadingCues, analysis, player, context, onDismiss)
                    1 -> AiTranscriptQaAndSearchTab(item, details, cues, player, scope, onDismiss)
                    2 -> AiChaptersAndMomentsTab(analysis, player, onDismiss)
                    3 -> AiSmartPlaylistTab(player, onDismiss)
                    4 -> AiProviderAndPrivacyDashboardTab()
                }
            }
        }
    }
}

@Composable
private fun AiSummaryAndKeyPointsTab(
    loading: Boolean,
    analysis: AiVideoAnalysis?,
    player: PlayerState,
    context: Context,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    if (loading && analysis == null) {
        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp)
        }
        return
    }
    val data = analysis ?: run {
        Text("Open a video to generate AI summaries, key takeaways, and topic classification.", color = c.onSurfaceVariant)
        return
    }

    // Topic Classification Card (Feature 8)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.primaryContainer.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Topic: ${data.topic.primaryTopic}" + (data.topic.secondaryTopic?.let { " · $it" } ?: ""),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.5.sp,
                color = c.onPrimaryContainer,
            )
            if (data.topic.matchedKeywords.isNotEmpty()) {
                Text(
                    "Signals: ${data.topic.matchedKeywords.joinToString(", ")} · ${(data.topic.confidence * 100).toInt()}% confidence",
                    fontSize = 11.5.sp,
                    color = c.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
        OutlinedButton(
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("AI Summary", data.executiveSummary))
                player.actionMessage = "Copied summary to clipboard"
            },
        ) {
            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Copy", fontSize = 11.5.sp)
        }
    }

    Spacer(Modifier.height(12.dp))
    Text("Executive Summary", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(4.dp))
    Text(
        data.executiveSummary,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceContainerHigh)
            .padding(12.dp),
    )

    Spacer(Modifier.height(12.dp))
    Text("Key Takeaways (${data.keyPoints.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    data.keyPoints.forEach { pt ->
        TimestampedBulletRow(pt) {
            player.seekTo(pt.timeMs)
            onDismiss()
        }
    }

    if (data.timeBucketSummary.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Time-Bucketed Transcript Breakdown", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
        Spacer(Modifier.height(6.dp))
        data.timeBucketSummary.forEach { seg ->
            TimestampedBulletRow(seg) {
                player.seekTo(seg.timeMs)
                onDismiss()
            }
        }
    }
}

@Composable
private fun AiTranscriptQaAndSearchTab(
    item: VideoItem?,
    details: VideoDetails?,
    cues: List<ParsedCue>,
    player: PlayerState,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var question by remember { mutableStateOf("") }
    var qaAnswer by remember { mutableStateOf<TranscriptQaAnswer?>(null) }
    var asking by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Text("Ask Questions About This Video (Feature 3)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            placeholder = { Text("What is the main conclusion or benchmark?") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            enabled = question.isNotBlank() && !asking,
            onClick = {
                asking = true
                scope.launch {
                    val localAns = withContext(Dispatchers.Default) {
                        AiTranscriptIntelligence.answerQuestion(
                            question = question,
                            title = item?.title.orEmpty(),
                            description = details?.description.orEmpty(),
                            cues = cues,
                        )
                    }
                    val remoteAns = if (AiIntelligenceSettings.providerMode.usesNetwork && AiIntelligenceSettings.explicitNetworkConsent) {
                        val ctxText = localAns.citations.joinToString("\n") { "[${formatDuration(it.timeMs)}] ${it.text}" }
                            .ifBlank { details?.description.orEmpty().take(2000) }
                        AiIntelligenceSettings.queryOptionalProvider(
                            systemPrompt = "Answer the user's question concisely based strictly on the provided video transcript excerpts. Include [mm:ss] timestamps.",
                            userPrompt = "Question: $question\nContext:\n$ctxText",
                        )
                    } else null
                    qaAnswer = if (remoteAns != null) {
                        localAns.copy(answer = remoteAns, providerUsed = AiIntelligenceSettings.providerMode.label)
                    } else localAns
                    asking = false
                }
            },
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, "Ask", modifier = Modifier.size(16.dp))
        }
    }

    qaAnswer?.let { ans ->
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceContainerHigh)
                .padding(12.dp),
        ) {
            Text("Answer (${ans.providerUsed})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.primary)
            Spacer(Modifier.height(4.dp))
            Text(ans.answer, fontSize = 13.sp, lineHeight = 18.sp)
            if (ans.citations.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Source Cues (Tap to jump):", fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = c.onSurfaceVariant)
                ans.citations.forEach { cite ->
                    TimestampedBulletRow(cite) {
                        player.seekTo(cite.timeMs)
                        onDismiss()
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
    Spacer(Modifier.height(10.dp))

    Text("Transcript Full-Text Search (${cues.size} cues)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search any spoken word or phrase in transcript…") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val matches = remember(cues, searchQuery) {
        if (searchQuery.isBlank()) cues.take(15).map { TimestampedBullet(it.startMs, it.text, "Cue") }
        else AiTranscriptIntelligence.searchTranscript(cues, searchQuery)
    }
    Spacer(Modifier.height(6.dp))
    if (cues.isEmpty()) {
        Text("No subtitle cues available for this video.", fontSize = 12.5.sp, color = c.onSurfaceVariant)
    } else {
        Text(
            if (searchQuery.isBlank()) "Showing first ${matches.size} cues" else "${matches.size} matching cues found",
            fontSize = 11.5.sp,
            color = c.onSurfaceVariant,
        )
        matches.take(30).forEach { m ->
            TimestampedBulletRow(m) {
                player.seekTo(m.timeMs)
                onDismiss()
            }
        }
    }
}

@Composable
private fun AiChaptersAndMomentsTab(
    analysis: AiVideoAnalysis?,
    player: PlayerState,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val data = analysis ?: run {
        Text("No video loaded.", color = c.onSurfaceVariant)
        return
    }

    Text("Important Moments Detected (${data.importantMoments.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    data.importantMoments.forEach { m ->
        TimestampedBulletRow(m) {
            player.seekTo(m.timeMs)
            onDismiss()
        }
    }

    Spacer(Modifier.height(12.dp))
    Text("Automatic Chapter Suggestions (${data.suggestedChapters.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    data.suggestedChapters.forEachIndexed { idx, ch ->
        TimestampedBulletRow(
            TimestampedBullet(ch.startMs, ch.title, "Chapter ${idx + 1}"),
        ) {
            player.seekTo(ch.startMs)
            onDismiss()
        }
    }
}

@Composable
private fun AiSmartPlaylistTab(player: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var selectedTopic by remember { mutableStateOf("All") }
    var budgetMin by remember { mutableIntStateOf(30) }
    val pool = remember(player.details?.related, player.queue) {
        (player.details?.related.orEmpty() + player.queue + QueueStore.saved + RecentMusic.items).distinctBy { it.id }
    }
    val generated = remember(pool, selectedTopic, budgetMin) {
        AiTranscriptIntelligence.generateSmartPlaylist(pool, selectedTopic, budgetMin)
    }

    Text("Smart Playlist Generator (Feature 9)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Text("Automatically curates a watch queue matching your topic focus and available time budget.", fontSize = 12.sp, color = c.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))

    Text("Time Budget", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 45, 60, 120).forEach { min ->
            FilterChip(
                selected = budgetMin == min,
                onClick = { budgetMin = min },
                label = { Text("$min min") },
            )
        }
    }

    Spacer(Modifier.height(6.dp))
    Text("Topic Filter", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val topics = listOf("All", "Software & Coding", "Science & Engineering", "Hardware & Tech", "AI & Machine Learning", "Music & Audio", "Gaming", "Education & Lectures")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        topics.forEach { t ->
            FilterChip(
                selected = selectedTopic == t,
                onClick = { selectedTopic = t },
                label = { Text(t) },
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    if (generated.isEmpty()) {
        Text("Play a video first so candidate related videos are available.", fontSize = 12.5.sp, color = c.onSurfaceVariant)
    } else {
        val totalMin = generated.sumOf { it.durationSec } / 60
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Curated ${generated.size} videos (~$totalMin min)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Button(
                onClick = {
                    generated.forEach { player.addToQueue(it) }
                    player.actionMessage = "Queued ${generated.size} smart playlist videos"
                    onDismiss()
                },
            ) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Queue All")
            }
        }
        Spacer(Modifier.height(6.dp))
        generated.forEach { v ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surfaceContainerHigh)
                    .pressable { player.open(v); onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(v.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${v.channel} · ${formatDuration(v.durationSec * 1000)}", fontSize = 11.5.sp, color = c.onSurfaceVariant)
                }
                Icon(Icons.Rounded.PlayArrow, null, tint = c.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AiProviderAndPrivacyDashboardTab() {
    val c = MaterialTheme.colorScheme
    var localUrl by remember { mutableStateOf(AiIntelligenceSettings.localEndpointUrl) }
    var localModel by remember { mutableStateOf(AiIntelligenceSettings.localModelName) }
    var extUrl by remember { mutableStateOf(AiIntelligenceSettings.externalEndpointUrl) }
    var extModel by remember { mutableStateOf(AiIntelligenceSettings.externalModelName) }
    var apiKeyInput by remember { mutableStateOf("") }

    Text("AI Provider Selection (Features 13 & 14)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    AiProviderMode.entries.forEach { mode ->
        val selected = AiIntelligenceSettings.providerMode == mode
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) c.primaryContainer else c.surfaceContainerHigh)
                .pressable { AiIntelligenceSettings.updateProvider(mode) }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(mode.label, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text(mode.subtitle, fontSize = 11.5.sp, color = c.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Rounded.Check, null, tint = c.onPrimaryContainer, modifier = Modifier.size(18.dp))
        }
    }

    if (AiIntelligenceSettings.providerMode == AiProviderMode.LocalServer) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = localUrl,
            onValueChange = { localUrl = it; AiIntelligenceSettings.updateProvider(AiIntelligenceSettings.providerMode, localUrl = it) },
            label = { Text("Local AI Endpoint URL (Ollama / LocalAI)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = localModel,
            onValueChange = { localModel = it; AiIntelligenceSettings.updateProvider(AiIntelligenceSettings.providerMode, localModel = it) },
            label = { Text("Local Model Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (AiIntelligenceSettings.providerMode == AiProviderMode.ExternalOpenAi ||
        AiIntelligenceSettings.providerMode == AiProviderMode.ExternalGemini
    ) {
        Spacer(Modifier.height(8.dp))
        if (AiIntelligenceSettings.providerMode == AiProviderMode.ExternalOpenAi) {
            OutlinedTextField(
                value = extUrl,
                onValueChange = { extUrl = it; AiIntelligenceSettings.updateProvider(AiIntelligenceSettings.providerMode, extUrl = it) },
                label = { Text("External API Endpoint") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = extModel,
            onValueChange = { extModel = it; AiIntelligenceSettings.updateProvider(AiIntelligenceSettings.providerMode, extModel = it) },
            label = { Text("Model Identifier") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Stored Key (AndroidKeyStore AES-GCM): ${AiIntelligenceSettings.maskedApiKey()}",
            fontSize = 12.sp,
            color = c.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("Update API Key (never exported)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    AiIntelligenceSettings.apiKey = apiKeyInput
                    apiKeyInput = ""
                },
            ) {
                Text("Save Key")
            }
        }
    }

    if (AiIntelligenceSettings.providerMode.usesNetwork) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Explicit Permission to Send Transcript Context", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Required before any video title or transcript leaves on-device processing", fontSize = 11.5.sp, color = c.onSurfaceVariant)
            }
            Switch(
                checked = AiIntelligenceSettings.explicitNetworkConsent,
                onCheckedChange = { AiIntelligenceSettings.updateProvider(AiIntelligenceSettings.providerMode, consent = it) },
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
    Spacer(Modifier.height(10.dp))

    Text("AI Privacy & API Usage Dashboard (Feature 15)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("On-Device NLP Analyses: ${AiIntelligenceSettings.localAnalysesCount}", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Text("External/LAN API Requests: ${AiIntelligenceSettings.networkRequestsCount}", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Text("Estimated Tokens Processed: ${AiIntelligenceSettings.estimatedTokensProcessed}", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Text("Last Activity: ${AiIntelligenceSettings.lastAuditEvent}", fontSize = 11.5.sp, color = c.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { AiIntelligenceSettings.resetUsageAndKeys() }) {
        Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Revoke API Keys & Clear AI Usage Data")
    }
}

@Composable
private fun TimestampedBulletRow(bullet: TimestampedBullet, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceContainerHigh)
            .pressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(c.primaryContainer)
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                formatDuration(bullet.timeMs),
                color = c.onPrimaryContainer,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (!bullet.badge.isNullOrBlank()) {
                Text(bullet.badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.primary)
            }
            Text(bullet.text, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
