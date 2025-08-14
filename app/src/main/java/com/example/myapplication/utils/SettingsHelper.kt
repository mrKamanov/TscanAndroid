package com.example.myapplication.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Утилита для получения настроек приложения
 */
object SettingsHelper {
    
    private const val PREFS_NAME = "ModelSettings"
    private const val KEY_IGNORE_FIXED_ANSWERS = "ignore_fixed_answers"
    
    /**
     * Получает настройку игнорирования исправлений
     * @param context Контекст приложения
     * @return true если исправления должны игнорироваться автоматически
     */
    fun getIgnoreFixedAnswers(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(KEY_IGNORE_FIXED_ANSWERS, false)
    }
}
