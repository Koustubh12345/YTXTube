package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    @Test
    fun everySupportedTypeSurvivesARoundTrip() {
        val prefs = mapOf(
            "playback" to mapOf<String, Any?>(
                "flag" to true,
                "count" to 5,
                "big" to 1_234_567_890_123L,
                "ratio" to 1.5f,
                "name" to "hello",
                "tags" to setOf("a", "b"),
            ),
            "theme" to mapOf<String, Any?>("dark" to false),
        )
        val json = BackupCodec.encode(prefs, 1L, "2.2.0")
        assertEquals(prefs, BackupCodec.decode(json))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownFormatIsRejected() {
        BackupCodec.decode("""{"format":99,"prefs":{}}""")
    }

    @Test
    fun unknownValueTypesAreSkipped() {
        val json = """{"format":1,"prefs":{"x":{"a":{"t":"zz","v":1},"b":{"t":"i","v":2}}}}"""
        assertEquals(mapOf("x" to mapOf<String, Any?>("b" to 2)), BackupCodec.decode(json))
    }

    @Test
    fun replaceModeWritesEverything() {
        val plan = BackupCodec.mergePlan(
            mapOf("a" to 1, "b" to 2),
            mapOf("b" to 9, "c" to 3),
            Backup.ConflictMode.Replace,
        )
        assertEquals(mapOf<String, Any?>("b" to 9, "c" to 3), plan.writes)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun keepExistingOnlyAddsMissingKeys() {
        val plan = BackupCodec.mergePlan(
            mapOf("a" to 1, "b" to 2),
            mapOf("b" to 9, "c" to 3),
            Backup.ConflictMode.KeepExisting,
        )
        assertEquals(mapOf<String, Any?>("c" to 3), plan.writes)
        assertEquals(1, plan.skipped)
    }

    @Test
    fun credentialLikeFilesAreNeverExported() {
        assertTrue(Backup.isExportable("playback"))
        assertTrue(Backup.isExportable("audio_effects"))
        assertFalse(Backup.isExportable("secure_store"))
        assertFalse(Backup.isExportable("AccountPrefs"))
        assertFalse(Backup.isExportable("auth_token"))
        assertFalse(Backup.isExportable("androidx.work.prefs"))
    }
}
