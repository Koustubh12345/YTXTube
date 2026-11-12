package com.tensei.ytxtube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassStudioTest {

    @Test
    fun validatesHexColorsAndWcagContrastRatio() {
        assertTrue(ThemePackageValidator.isValidHexColor("#7C3AED"))
        assertTrue(ThemePackageValidator.isValidHexColor("#0091ff"))
        assertFalse(ThemePackageValidator.isValidHexColor("7C3AED"))
        assertFalse(ThemePackageValidator.isValidHexColor("#GGGGGG"))

        val ratio = ThemePackageValidator.contrastRatio("#000000", "#FFFFFF")
        assertTrue("Black/White contrast should be ~21:1, got $ratio", ratio > 20.0)
    }

    @Test
    fun validatesAndParsesCommunityThemePackage() {
        val pkg = LiquidGlassStudio.communityThemePackages.first()
        val res = ThemePackageValidator.validate(pkg.toJson().toString())
        assertTrue(res.valid)
        assertTrue(res.errors.isEmpty())
        assertNotNull(res.parsedPackage)
        assertTrue(res.contrastRatio >= 4.5)
    }

    @Test
    fun rejectsMalformedThemePackageSchemaAndBounds() {
        val badJson = """{"schema":"wrong","name":"","blurDp":999,"seedHex":"not_a_hex"}"""
        val res = ThemePackageValidator.validate(badJson)
        assertFalse(res.valid)
        assertTrue(res.errors.size >= 3)
    }
}
