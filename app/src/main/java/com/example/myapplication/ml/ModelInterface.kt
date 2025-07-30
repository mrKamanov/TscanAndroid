package com.example.myapplication.ml

import android.graphics.Bitmap

/**
 * Интерфейс для работы с различными форматами моделей
 */
interface ModelInterface {
    
    /**
     * Инициализирует модель
     */
    fun initialize(): Boolean
    
    /**
     * Предсказывает результат для одного изображения
     */
    fun predict(bitmap: Bitmap): PredictionResult
    
    /**
     * Предсказывает результаты для батча изображений
     */
    fun predictBatch(bitmaps: List<Bitmap>): List<PredictionResult>
    
    /**
     * Освобождает ресурсы модели
     */
    fun release()
    
    /**
     * Проверяет, готова ли модель к использованию
     */
    fun isReady(): Boolean
}

/**
 * Результат предсказания модели
 */
data class PredictionResult(
    val isFilled: Boolean,
    val confidence: Float,
    val probabilities: FloatArray
) {
    /**
     * Проверяет, достаточно ли высока уверенность
     */
    fun isConfident(): Boolean {
        return confidence >= 0.8f
    }
    
    /**
     * Получает текстовое описание результата
     */
    fun getDescription(): String {
        val status = if (isFilled) "ЗАПОЛНЕНО" else "ПУСТОЕ"
        val confidencePercent = (confidence * 100).toInt()
        return "$status ($confidencePercent%)"
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PredictionResult

        if (isFilled != other.isFilled) return false
        if (confidence != other.confidence) return false
        if (!probabilities.contentEquals(other.probabilities)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isFilled.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + probabilities.contentHashCode()
        return result
    }
} 