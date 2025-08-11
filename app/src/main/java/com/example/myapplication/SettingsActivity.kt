package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val PREFS_NAME = "ModelSettings"
        private const val KEY_ACTIVE_MODEL = "active_model"
        private const val MODEL_64 = "64"
        private const val MODEL_128 = "128"
        private const val MODEL_256 = "256"
    }
    
    private lateinit var btnModel64: MaterialButton
    private lateinit var btnModel128: MaterialButton
    private lateinit var btnModel256: MaterialButton
    private lateinit var tvSelectionInfo: TextView
    private lateinit var sharedPreferences: SharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Инициализация SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Инициализация views
        initViews()
        
        // Загрузка сохраненных настроек
        loadSavedSettings()
        
        // Настройка слушателей
        setupListeners()
    }
    
    private fun initViews() {
        btnModel64 = findViewById(R.id.btnModel64)
        btnModel128 = findViewById(R.id.btnModel128)
        btnModel256 = findViewById(R.id.btnModel256)
        tvSelectionInfo = findViewById(R.id.tvSelectionInfo)
        
        // Кнопка назад
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }
    
    private fun loadSavedSettings() {
        val activeModel = sharedPreferences.getString(KEY_ACTIVE_MODEL, MODEL_128) // По умолчанию 128
        
        // Сбрасываем все кнопки
        updateButtonState(btnModel64, false)
        updateButtonState(btnModel128, false)
        updateButtonState(btnModel256, false)
        
        // Устанавливаем активную модель
        when (activeModel) {
            MODEL_64 -> updateButtonState(btnModel64, true)
            MODEL_128 -> updateButtonState(btnModel128, true)
            MODEL_256 -> updateButtonState(btnModel256, true)
        }
        
        updateSelectionInfo()
    }
    
    private fun setupListeners() {
        btnModel64.setOnClickListener {
            // Отключаем другие кнопки
            updateButtonState(btnModel128, false)
            updateButtonState(btnModel256, false)
            // Активируем эту кнопку
            updateButtonState(btnModel64, true)
            // Сохраняем выбор
            saveActiveModel(MODEL_64)
            updateSelectionInfo()
        }
        
        btnModel128.setOnClickListener {
            // Отключаем другие кнопки
            updateButtonState(btnModel64, false)
            updateButtonState(btnModel256, false)
            // Активируем эту кнопку
            updateButtonState(btnModel128, true)
            // Сохраняем выбор
            saveActiveModel(MODEL_128)
            updateSelectionInfo()
        }
        
        btnModel256.setOnClickListener {
            // Отключаем другие кнопки
            updateButtonState(btnModel64, false)
            updateButtonState(btnModel128, false)
            // Активируем эту кнопку
            updateButtonState(btnModel256, true)
            // Сохраняем выбор
            saveActiveModel(MODEL_256)
            updateSelectionInfo()
        }
    }
    
    private fun saveActiveModel(model: String) {
        sharedPreferences.edit()
            .putString(KEY_ACTIVE_MODEL, model)
            .apply()
        
        // Показываем уведомление об изменении модели
        val modelName = when (model) {
            MODEL_64 -> "64×64"
            MODEL_128 -> "128×128"
            MODEL_256 -> "256×256"
            else -> "128×128"
        }
        
        // Перезагружаем модель в OMRModelManager
        try {
            com.example.myapplication.ml.OMRModelManager.reloadGlobalModel()
            android.util.Log.i("SettingsActivity", "🔄 Модель перезагружена: $modelName")
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "❌ Ошибка перезагрузки модели: ${e.message}")
        }
        
        android.widget.Toast.makeText(
            this, 
            "Активная модель изменена на: $modelName", 
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun updateSelectionInfo() {
        val activeModel = sharedPreferences.getString(KEY_ACTIVE_MODEL, MODEL_128) ?: MODEL_128
        val displayName = when (activeModel) {
            MODEL_64 -> "64×64"
            MODEL_128 -> "128×128"
            MODEL_256 -> "256×256"
            else -> "не выбрана"
        }
        
        tvSelectionInfo.text = "Активна модель: $displayName"
    }
    
    /**
     * Обновляет состояние кнопки (активная/неактивная)
     */
    private fun updateButtonState(button: MaterialButton, isActive: Boolean) {
        if (isActive) {
            // Активная кнопка - красивый зеленый фон #16A34AFF, белый текст
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#16A34AFF")
            )
            button.setTextColor(getColor(android.R.color.white))
            button.strokeColor = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#16A34AFF")
            )
        } else {
            // Неактивная кнопка - прозрачный фон, серый текст
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(
                getColor(com.example.myapplication.R.color.surface)
            )
            button.setTextColor(getColor(com.example.myapplication.R.color.text))
            button.strokeColor = android.content.res.ColorStateList.valueOf(
                getColor(com.example.myapplication.R.color.secondary)
            )
        }
    }
    
    /**
     * Получает активную модель (для использования в других частях приложения)
     */
    fun getActiveModel(): String {
        return sharedPreferences.getString(KEY_ACTIVE_MODEL, MODEL_128) ?: MODEL_128
    }
}
