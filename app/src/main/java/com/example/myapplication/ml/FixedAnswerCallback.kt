package com.example.myapplication.ml

import com.example.myapplication.models.FixedAnswer
import com.example.myapplication.models.OMRResult

/**
 * Callback для обработки обнаруженных исправлений
 */
interface FixedAnswerCallback {
    
    /**
     * Вызывается когда обнаружено исправление в эталонной ячейке
     * @param fixedAnswer информация об исправлении
     * @param onUserDecision callback с решением пользователя (true = считать ошибкой, false = оставить правильным)
     */
    fun onFixedAnswerDetected(fixedAnswer: FixedAnswer, onUserDecision: (Boolean) -> Unit)
    
    /**
     * Вызывается когда все исправления обработаны
     * @param finalResult Финальный результат с учетом всех решений пользователя
     */
    fun onAllFixedAnswersProcessed(finalResult: OMRResult)
}
