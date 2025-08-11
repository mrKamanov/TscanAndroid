package com.example.myapplication.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Утилита для работы с настройками выбора модели
 */
object ModelSettingsHelper {
    
    private const val PREFS_NAME = "ModelSettings"
    private const val KEY_ACTIVE_MODEL = "active_model"
    
    private const val MODEL_64 = "64"
    private const val MODEL_128 = "128"
    private const val MODEL_256 = "256"
    
    /**
     * Получает активную модель из настроек
     */
    fun getActiveModel(context: Context): String {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(KEY_ACTIVE_MODEL, MODEL_128) ?: MODEL_128
    }
    
    /**
     * Получает путь к файлу активной модели
     */
    fun getActiveModelPath(context: Context): String {
        return when (getActiveModel(context)) {
            MODEL_64 -> "models/omr_model_64.onnx"
            MODEL_128 -> "models/omr_model_best.onnx"
            MODEL_256 -> "models/omr_model_256.onnx"
            else -> "models/omr_model_best.onnx" // fallback на 128
        }
    }
    
    /**
     * Получает размер входа для активной модели
     */
    fun getActiveModelInputSize(context: Context): Int {
        return when (getActiveModel(context)) {
            MODEL_64 -> 64
            MODEL_128 -> 128
            MODEL_256 -> 256
            else -> 128 // fallback на 128
        }
    }
    
    /**
     * Получает название активной модели для отображения
     */
    fun getActiveModelDisplayName(context: Context): String {
        return when (getActiveModel(context)) {
            MODEL_64 -> "64×64"
            MODEL_128 -> "128×128"
            MODEL_256 -> "256×256"
            else -> "128×128"
        }
    }
}
