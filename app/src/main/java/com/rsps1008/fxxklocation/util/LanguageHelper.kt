package com.rsps1008.fxxklocation.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguageHelper {
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_CHINESE_TRADITIONAL = "zh-TW"

    fun currentLanguageTag(context: Context): String {
        val locale = context.resources.configuration.locales[0]?.toLanguageTag().orEmpty()
        return if (locale.startsWith("zh")) {
            LANGUAGE_CHINESE_TRADITIONAL
        } else {
            LANGUAGE_ENGLISH
        }
    }

    fun applyLanguage(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }
}
