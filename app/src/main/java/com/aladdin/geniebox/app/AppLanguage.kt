package com.aladdin.geniebox.app

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

object AppLanguage {
    private const val PREFERENCES = "app_language"
    private const val LANGUAGE = "language_tag"
    const val CHINESE = "zh-TW"
    const val SIMPLIFIED_CHINESE = "zh-CN"
    const val ENGLISH = "en"

    private lateinit var applicationContext: Context
    @Volatile private lateinit var localizedContext: Context

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        localizedContext = wrap(applicationContext)
    }

    fun selectedTag(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LANGUAGE, CHINESE) ?: CHINESE

    fun wrap(context: Context): Context {
        val configuration = Configuration()
        configuration.setLocale(Locale.forLanguageTag(selectedTag(context)))
        return context.createConfigurationContext(configuration)
    }

    fun select(tag: String) {
        require(tag == CHINESE || tag == SIMPLIFIED_CHINESE || tag == ENGLISH)
        applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(LANGUAGE, tag).apply()
        localizedContext = wrap(applicationContext)
    }

    fun text(@StringRes id: Int, vararg args: Any?): String =
        if (args.isEmpty()) localizedContext.getString(id)
        else localizedContext.getString(id, *args)

    fun selectAndRecreate(context: Context, tag: String) {
        if (selectedTag(context) == tag) return
        var host = context
        while (host is android.content.ContextWrapper && host !is android.app.Activity) {
            host = host.baseContext
        }
        val activity = host as? android.app.Activity ?: return
        select(tag)
        activity.recreate()
    }
}
