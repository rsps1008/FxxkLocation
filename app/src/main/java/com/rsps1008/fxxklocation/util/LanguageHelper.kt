package com.rsps1008.fxxklocation.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguageHelper {
    const val LANGUAGE_CHINESE_TRADITIONAL = "zh-TW"

    fun syncWithSystemLanguage(context: Context) {
        val locale = context.resources.configuration.locales[0]
        val desiredLocales = if (locale.language.equals("zh", ignoreCase = true)) {
            LocaleListCompat.forLanguageTags(LANGUAGE_CHINESE_TRADITIONAL)
        } else {
            LocaleListCompat.getEmptyLocaleList()
        }

        if (AppCompatDelegate.getApplicationLocales() != desiredLocales) {
            AppCompatDelegate.setApplicationLocales(desiredLocales)
        }
    }
}
