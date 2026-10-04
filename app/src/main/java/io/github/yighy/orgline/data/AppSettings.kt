package io.github.yighy.orgline.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** User preferences, stored in SharedPreferences. */
class AppSettings(context: Context) {

    data class Values(
        /** Show each note's content under its title in the notebook outline. */
        val showContentInNotebook: Boolean = false,
    )

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _values = MutableStateFlow(
        Values(showContentInNotebook = prefs.getBoolean(KEY_SHOW_CONTENT, false)),
    )
    val values: StateFlow<Values> = _values

    fun setShowContentInNotebook(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CONTENT, show).apply()
        _values.update { it.copy(showContentInNotebook = show) }
    }

    companion object {
        private const val KEY_SHOW_CONTENT = "show_content_in_notebook"
    }
}
