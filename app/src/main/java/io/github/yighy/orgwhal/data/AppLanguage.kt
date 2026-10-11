package io.github.yighy.orgwhal.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app in English whatever the phone's language, when chosen in the settings. From Android 13
 * the system keeps it (and shows it in its own settings); before, it is kept here and applied to
 * each context.
 */
object AppLanguage {
    private const val ENGLISH = "en"
    private const val PREFS = "settings"
    private const val KEY = "language"

    fun isEnglish(context: Context): Boolean {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.language
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }
        return tag == ENGLISH
    }

    /** English with [on], else the phone's language; the screen is redrawn in it. */
    fun setEnglish(activity: Activity, on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (on) LocaleList.forLanguageTags(ENGLISH) else LocaleList.getEmptyLocaleList()
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, ENGLISH.takeIf { on }).commit()
            activity.recreate()
        }
    }

    /** Before Android 13, [base] in English when chosen (for attachBaseContext). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        // Dates and times follow too
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
