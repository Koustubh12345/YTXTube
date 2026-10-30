package com.tensei.ytxtube

import android.util.Log
import okhttp3.Request
import java.util.regex.Pattern

data class ParsedCue(val startMs: Long, val endMs: Long, val text: String)

object SubtitleParser {
    private const val TAG = "SubtitleParser"

    fun fetchAndParse(url: String): List<ParsedCue> {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", Net.USER_AGENT)
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .build()
            val response = Net.client.newCall(req).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Subtitle fetch failed: HTTP ${response.code}")
                return emptyList()
            }
            val body = response.body?.string().orEmpty()
            parse(body)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch subtitle cues from $url", e)
            emptyList()
        }
    }

    fun parse(raw: String): List<ParsedCue> {
        if (raw.isBlank()) return emptyList()
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("WEBVTT") || trimmed.contains("-->") -> parseVtt(trimmed)
            trimmed.contains("<p ") || trimmed.contains("<p>") -> parseTtml(trimmed)
            trimmed.contains("<text ") -> parseXml(trimmed)
            else -> emptyList()
        }
    }

    private fun cleanText(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#10;", "\n")
            .replace("\n\n+", "\n")
            .trim()
    }

    private fun parseTtml(xml: String): List<ParsedCue> {
        val cues = mutableListOf<ParsedCue>()
        // Pattern 1: t="18640" d="3240" (milliseconds)
        val pMs = Pattern.compile("<p\\s+[^>]*t=\"(\\d+)\"[^>]*d=\"(\\d+)\"[^>]*>(.*?)</p>", Pattern.DOTALL)
        var m = pMs.matcher(xml)
        while (m.find()) {
            val start = m.group(1)?.toLongOrNull() ?: continue
            val dur = m.group(2)?.toLongOrNull() ?: continue
            val text = cleanText(m.group(3).orEmpty())
            if (text.isNotBlank()) cues.add(ParsedCue(start, start + dur, text))
        }
        if (cues.isNotEmpty()) return cues

        // Pattern 2: begin="00:00:18.640" end="00:00:21.880"
        val pTime = Pattern.compile("<p\\s+[^>]*begin=\"([^\"]+)\"[^>]*end=\"([^\"]+)\"[^>]*>(.*?)</p>", Pattern.DOTALL)
        m = pTime.matcher(xml)
        while (m.find()) {
            val start = parseTimestamp(m.group(1).orEmpty()) ?: continue
            val end = parseTimestamp(m.group(2).orEmpty()) ?: continue
            val text = cleanText(m.group(3).orEmpty())
            if (text.isNotBlank()) cues.add(ParsedCue(start, end, text))
        }
        return cues
    }

    private fun parseXml(xml: String): List<ParsedCue> {
        val cues = mutableListOf<ParsedCue>()
        val p = Pattern.compile("<text\\s+[^>]*start=\"([\\d.]+)\"[^>]*dur=\"([\\d.]+)\"[^>]*>(.*?)</text>", Pattern.DOTALL)
        val m = p.matcher(xml)
        while (m.find()) {
            val startSec = m.group(1)?.toFloatOrNull() ?: continue
            val durSec = m.group(2)?.toFloatOrNull() ?: continue
            val start = (startSec * 1000).toLong()
            val end = start + (durSec * 1000).toLong()
            val text = cleanText(m.group(3).orEmpty())
            if (text.isNotBlank()) cues.add(ParsedCue(start, end, text))
        }
        return cues
    }

    private fun parseVtt(vtt: String): List<ParsedCue> {
        val cues = mutableListOf<ParsedCue>()
        val timePattern = Pattern.compile("((?:\\d{1,2}:)?\\d{2}:\\d{2}[.,]\\d{3})\\s*-->\\s*((?:\\d{1,2}:)?\\d{2}:\\d{2}[.,]\\d{3})")
        val blocks = vtt.split(Regex("\r?\n\r?\n+"))
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val timeIndex = lines.indexOfFirst { timePattern.matcher(it).find() }
            if (timeIndex < 0) continue
            val timeLine = lines[timeIndex]
            val tm = timePattern.matcher(timeLine)
            if (!tm.find()) continue
            val start = parseTimestamp(tm.group(1).orEmpty()) ?: continue
            val end = parseTimestamp(tm.group(2).orEmpty()) ?: continue
            val textLines = lines.drop(timeIndex + 1)
            val text = cleanText(textLines.joinToString("\n"))
            if (text.isNotBlank()) {
                cues.add(ParsedCue(start, end, text))
            }
        }
        return cues
    }

    private fun parseTimestamp(raw: String): Long? {
        val cleaned = raw.trim().removeSuffix("s")
        val parts = cleaned.split(':')
        return try {
            when (parts.size) {
                1 -> (parts[0].toDouble() * 1000).toLong()
                2 -> {
                    val min = parts[0].toLong()
                    val sec = parts[1].replace(',', '.').toDouble()
                    (min * 60 * 1000) + (sec * 1000).toLong()
                }
                3 -> {
                    val hr = parts[0].toLong()
                    val min = parts[1].toLong()
                    val sec = parts[2].replace(',', '.').toDouble()
                    (hr * 3600 * 1000) + (min * 60 * 1000) + (sec * 1000).toLong()
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
