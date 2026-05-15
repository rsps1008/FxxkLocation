package com.rsps1008.fxxklocation.util

import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LanguageHelper {
    const val LANGUAGE_CHINESE_TRADITIONAL = "zh-TW"

    fun syncWithSystemLanguage(context: android.content.Context) {
        val systemLanguageTag = LocaleManagerCompat.getSystemLocales(context)
            .toLanguageTags()
            .substringBefore(',')
        val locale = if (systemLanguageTag.isNotBlank()) {
            Locale.forLanguageTag(systemLanguageTag)
        } else {
            LocaleList.getDefault()[0]
        }
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
