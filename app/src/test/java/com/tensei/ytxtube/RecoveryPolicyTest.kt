package com.tensei.ytxtube

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryPolicyTest {

    @Test
    fun backoffDoublesPerAttempt() {
        assertEquals(1_000L, RecoveryPolicy.delayMs(1))
        assertEquals(2_000L, RecoveryPolicy.delayMs(2))
        assertEquals(4_000L, RecoveryPolicy.delayMs(3))
        assertEquals(8_000L, RecoveryPolicy.delayMs(4))
    }

    @Test
    fun backoffNeverIndexesOutOfRange() {
        assertEquals(1_000L, RecoveryPolicy.delayMs(0))
        assertEquals(8_000L, RecoveryPolicy.delayMs(99))
    }

    @Test
    fun retryCountIsBounded() {
        assertEquals(4, RecoveryPolicy.maxAttempts)
    }

    @Test
    fun midVideoPositionIsKept() {
        assertEquals(10_000L, RecoveryPolicy.resumePosition(10_000L, 60_000L))
    }

    @Test
    fun positionNearTheEndStepsBack() {
        assertEquals(59_500L, RecoveryPolicy.resumePosition(59_800L, 60_000L))
        assertEquals(59_500L, RecoveryPolicy.resumePosition(60_000L, 60_000L))
    }

    @Test
    fun positionJustBeforeTheThresholdIsKept() {
        assertEquals(59_400L, RecoveryPolicy.resumePosition(59_400L, 60_000L))
    }

    @Test
    fun veryShortVideoClampsToStart() {
        assertEquals(0L, RecoveryPolicy.resumePosition(300L, 400L))
    }

    @Test
    fun unknownDurationKeepsPosition() {
        assertEquals(5_000L, RecoveryPolicy.resumePosition(5_000L, C.TIME_UNSET))
        assertEquals(5_000L, RecoveryPolicy.resumePosition(5_000L, 0L))
    }

    @Test
    fun negativePositionBecomesZero() {
        assertEquals(0L, RecoveryPolicy.resumePosition(-5L, 1_000L))
    }
}
