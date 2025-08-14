package com.example.myapplication.batch

import android.content.Context
import android.util.Log
import com.example.myapplication.ml.FixedAnswerCallback
import com.example.myapplication.models.FixedAnswer
import com.example.myapplication.models.OMRResult
import com.example.myapplication.utils.SettingsHelper

/**
 * Обработчик исправлений для пакетной обработки
 * Отделен от ScanActivity для избежания конфликтов
 */
class BatchFixedAnswerProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "BatchFixedAnswerProcessor"
    }
    
    // Хранилище исправлений по файлам
    private val pendingFixedAnswers = mutableMapOf<String, MutableList<FixedAnswer>>()
    private val fixedAnswerDecisions = mutableMapOf<String, MutableMap<Int, Boolean>>()
    
    /**
     * Создает callback для обработки исправлений в конкретном файле
     */
    fun createFixedAnswerCallback(filename: String): FixedAnswerCallback {
        return object : FixedAnswerCallback {
            override fun onFixedAnswerDetected(fixedAnswer: FixedAnswer, onUserDecision: (Boolean) -> Unit) {
                Log.d(TAG, "⚠️ Обнаружено исправление в вопросе ${fixedAnswer.questionNumber} для файла $filename")
                
                // Проверяем настройку игнорирования исправлений
                val ignoreFixedAnswers = SettingsHelper.getIgnoreFixedAnswers(context)
                
                if (ignoreFixedAnswers) {
                    // Если включено автоигнорирование - считаем исправление правильным
                    Log.d(TAG, "✅ Автоигнорирование включено: исправление считается правильным")
                    onUserDecision(false) // false = не считать ошибкой
                } else {
                    // Если выключено - сохраняем для ручной обработки
                    pendingFixedAnswers.getOrPut(filename) { mutableListOf() }.add(fixedAnswer)
                    // НЕ вызываем onUserDecision здесь - ждем решения пользователя
                    Log.d(TAG, "⏳ Исправление сохранено для ручной обработки")
                }
            }
            
            override fun onAllFixedAnswersProcessed(finalResult: OMRResult) {
                Log.d(TAG, "✅ Все исправления обработаны для файла $filename")
            }
        }
    }
    
    /**
     * Получает список исправлений для файла
     */
    fun getFixedAnswers(filename: String): List<FixedAnswer> {
        return pendingFixedAnswers[filename] ?: emptyList()
    }
    
    /**
     * Проверяет, есть ли исправления для файла
     */
    fun hasFixedAnswers(filename: String): Boolean {
        return pendingFixedAnswers.containsKey(filename) && pendingFixedAnswers[filename]?.isNotEmpty() == true
    }
    
    /**
     * Обрабатывает решение пользователя по исправлению
     */
    fun handleFixedAnswerDecision(filename: String, questionNumber: Int, countAsError: Boolean) {
        fixedAnswerDecisions.getOrPut(filename) { mutableMapOf() }[questionNumber] = countAsError
        Log.d(TAG, "✅ Решение по исправлению: файл=$filename, вопрос=$questionNumber, считать ошибкой=$countAsError")
    }
    
    /**
     * Получает решение пользователя по исправлению
     */
    fun getFixedAnswerDecision(filename: String, questionNumber: Int): Boolean? {
        return fixedAnswerDecisions[filename]?.get(questionNumber)
    }
    
    /**
     * Очищает данные для файла
     */
    fun clearFileData(filename: String) {
        pendingFixedAnswers.remove(filename)
        fixedAnswerDecisions.remove(filename)
        Log.d(TAG, "🗑️ Данные очищены для файла: $filename")
    }
    
    /**
     * Очищает все данные
     */
    fun clearAllData() {
        pendingFixedAnswers.clear()
        fixedAnswerDecisions.clear()
        Log.d(TAG, "🗑️ Все данные очищены")
    }
}
