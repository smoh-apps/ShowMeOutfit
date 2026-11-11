package com.smoh.showmeoutfit.ui.settings

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smoh.showmeoutfit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Proves the `values-vi` / `values-fr` resources actually resolve at runtime, not just at compile
 * time: a missing key silently falls back to English, so a literal comparison is the only way to
 * catch it.
 */
@RunWith(AndroidJUnit4::class)
class LocalizationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun vietnamese_copy_resolves_to_the_translation() {
        val vi = localized("vi")

        assertEquals("Your outfit", context.getString(R.string.home_section_outfit))
        assertEquals("Trang phục của bạn", vi.getString(R.string.home_section_outfit))
        assertEquals("Đừng quên", vi.getString(R.string.home_section_accessories))
        assertEquals("Trời quang", vi.getString(R.string.home_condition_clear_sky))
        assertEquals("Ngôn ngữ", vi.getString(R.string.settings_section_language))
        assertEquals("Tiếng Việt", vi.getString(R.string.settings_language_vietnamese))
        assertEquals("ShowMeOutfit", vi.getString(R.string.app_name))
    }

    @Test
    fun french_copy_resolves_to_the_translation() {
        val fr = localized("fr")

        assertEquals("Your outfit", context.getString(R.string.home_section_outfit))
        assertEquals("Votre tenue", fr.getString(R.string.home_section_outfit))
        assertEquals("À ne pas oublier", fr.getString(R.string.home_section_accessories))
        assertEquals("Ciel dégagé", fr.getString(R.string.home_condition_clear_sky))
        assertEquals("Langue", fr.getString(R.string.settings_section_language))
        assertEquals("Français", fr.getString(R.string.settings_language_french))
        assertEquals("ShowMeOutfit", fr.getString(R.string.app_name))
    }

    @Test
    fun the_two_locales_never_share_the_same_copy_by_accident() {
        val vi = localized("vi")
        val fr = localized("fr")

        listOf(
            R.string.home_section_outfit,
            R.string.home_section_accessories,
            R.string.settings_section_language,
            R.string.home_empty_title
        ).forEach { id ->
            assertNotEquals(
                "values-vi and values-fr must differ for " + context.resources.getResourceEntryName(id),
                vi.getString(id),
                fr.getString(id)
            )
        }
    }

    @Test
    fun plurals_are_translated_with_the_right_quantities() {
        val vi = localized("vi")
        val fr = localized("fr")

        assertEquals("3 giờ", vi.resources.getQuantityString(R.plurals.home_cache_age_hours, 3, 3))
        assertEquals("1 giờ", vi.resources.getQuantityString(R.plurals.home_cache_age_hours, 1, 1))
        assertEquals("1 heure", fr.resources.getQuantityString(R.plurals.home_cache_age_hours, 1, 1))
        assertEquals("3 heures", fr.resources.getQuantityString(R.plurals.home_cache_age_hours, 3, 3))
        assertEquals("1 ville trouvée", fr.resources.getQuantityString(R.plurals.location_search_result_count, 1, 1))
        assertEquals("4 villes trouvées", fr.resources.getQuantityString(R.plurals.location_search_result_count, 4, 4))
    }

    /** A sample sweep: every string id must resolve to non-blank text in both locales. */
    @Test
    fun every_string_id_resolves_in_both_locales() {
        val vi = localized("vi")
        val fr = localized("fr")

        stringIds().forEach { id ->
            val name = context.resources.getResourceEntryName(id)
            assertTrue("vi missing text for $name", vi.getString(id).isNotBlank())
            assertTrue("fr missing text for $name", fr.getString(id).isNotBlank())
        }
    }

    private fun stringIds(): List<Int> = R.string::class.java.fields.mapNotNull { field ->
        runCatching { field.getInt(null) }.getOrNull()
    }

    private fun localized(languageTag: String): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(languageTag))
        return context.createConfigurationContext(configuration)
    }
}
