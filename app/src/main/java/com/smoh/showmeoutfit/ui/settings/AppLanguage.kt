package com.smoh.showmeoutfit.ui.settings

import java.util.Locale

/**
 * The languages the app ships translations for.
 *
 * [tag] is the BCP-47 primary language tag used to build a [Locale]; [SYSTEM] means "follow the
 * device", which is represented as an empty locale list on Android 13+ and as "no saved tag" on
 * older releases.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    VIETNAMESE("vi"),
    FRENCH("fr");

    companion object {
        /** Maps a platform tag ("vi", "vi-VN", "fr_CA") to an entry; unknown values mean SYSTEM. */
        fun fromTag(tag: String?): AppLanguage {
            if (tag.isNullOrBlank()) return SYSTEM
            val primary = tag.trim()
                .replace('_', '-')
                .substringBefore('-')
                .lowercase(Locale.ROOT)
            return entries.firstOrNull { it.tag == primary } ?: SYSTEM
        }
    }
}
