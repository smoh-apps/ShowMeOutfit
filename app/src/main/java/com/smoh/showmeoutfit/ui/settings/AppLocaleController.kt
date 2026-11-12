package com.smoh.showmeoutfit.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/** Reads and writes the language the app is displayed in. */
interface AppLocaleController {
    fun currentLanguage(): AppLanguage
    fun setLanguage(language: AppLanguage)
}

/**
 * Platform-only locale switching: no AppCompat, because the activity is a [android.app.Activity]
 * subclass of `ComponentActivity` running on a platform Material theme.
 *
 * - Android 13+ (API 33): the per-app locale comes from [LocaleManager], which recreates the
 *   activity on its own when the list changes.
 * - Android 8-12 (API 26-32): there is no per-app locale API, so the tag is persisted and
 *   [wrapContext] applies it from [android.app.Activity#attachBaseContext] on the next attach. The
 *   caller recreates the activity to apply it immediately.
 *
 * The choice is always mirrored to SharedPreferences so the pre-33 wrapper has a value, the picker
 * can render its selection immediately, and the mirror survives on API 33+ devices as a fallback.
 */
class AndroidAppLocaleController(context: Context) : AppLocaleController {

    private val context: Context = context.applicationContext ?: context

    private val preferences
        get() = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun currentLanguage(): AppLanguage {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (localeManager != null) {
                val locales = localeManager.applicationLocales
                val platformTag = if (locales.isEmpty) null else locales[0].toLanguageTag()
                return AppLanguage.fromTag(platformTag)
            }
        }
        return AppLanguage.fromTag(storedTag())
    }

    // commit rather than apply: the value has to be readable from attachBaseContext, which the
    // recreate() triggered by the picker reaches before a queued disk write would have landed.
    override fun setLanguage(language: AppLanguage) {
        preferences.edit(commit = true) { putString(KEY_LANGUAGE_TAG, language.tag.orEmpty()) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = language.tag
                ?.let { LocaleList.forLanguageTags(it) }
                ?: LocaleList.getEmptyLocaleList()
            context.getSystemService(LocaleManager::class.java)?.applicationLocales = locales
        } else {
            val targetLocale = language.tag
                ?.let(Locale::forLanguageTag)
                ?: systemDefaultLocale
            Locale.setDefault(targetLocale)
        }
    }

    /**
     * Applies the saved language to a context for API < 33. On API 33+ the platform already did it,
     * so the base context is returned untouched.
     */
    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base

        val tag = storedTag()
        val targetLocale = if (tag.isNullOrBlank()) {
            systemDefaultLocale
        } else {
            Locale.forLanguageTag(tag)
        }
        Locale.setDefault(targetLocale)
        if (tag.isNullOrBlank()) return base

        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(targetLocale)
        return base.createConfigurationContext(configuration)
    }

    private fun storedTag(): String? = preferences.getString(KEY_LANGUAGE_TAG, null)

    private companion object {
        const val PREFERENCES_NAME = "app_locale"
        const val KEY_LANGUAGE_TAG = "language_tag"
        val systemDefaultLocale: Locale = Locale.getDefault()
    }
}
