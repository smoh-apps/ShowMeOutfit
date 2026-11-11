package com.smoh.showmeoutfit.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `system is the fallback for missing or blank tags`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("   "))
    }

    @Test
    fun `primary language tags map to their entry`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromTag("vi"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTag("fr"))
    }

    @Test
    fun `region and script variants fall back to the primary subtag`() {
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromTag("vi-VN"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTag("fr-CA"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-GB"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTag("fr_CA"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("EN-us"))
    }

    @Test
    fun `unsupported languages map to system`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("de"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("ja-JP"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("-"))
    }

    @Test
    fun `tags round-trip through fromTag`() {
        AppLanguage.entries.forEach { language ->
            if (language.tag == null) {
                assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
            } else {
                assertEquals(language, AppLanguage.fromTag(language.tag))
            }
        }
    }

    @Test
    fun `only system has no language tag`() {
        assertNull(AppLanguage.SYSTEM.tag)
        assertEquals(
            listOf("en", "vi", "fr"),
            AppLanguage.entries.mapNotNull { it.tag }
        )
    }
}
