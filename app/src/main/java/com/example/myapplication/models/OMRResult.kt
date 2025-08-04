package com.example.myapplication.models

import android.graphics.Bitmap

/**
 * Результаты OMR обработки тестового бланка
 */
data class OMRResult(
    val selectedAnswers: IntArray,
    val grading: IntArray,
    val incorrectQuestions: List<Map<String, Any>>,
    val correctAnswers: List<Int>,
    val visualization: Bitmap? = null,
    val gridVisualization: Bitmap? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OMRResult

        if (!selectedAnswers.contentEquals(other.selectedAnswers)) return false
        if (!grading.contentEquals(other.grading)) return false
        if (incorrectQuestions != other.incorrectQuestions) return false
        if (correctAnswers != other.correctAnswers) return false

        return true
    }

    override fun hashCode(): Int {
        var result = selectedAnswers.contentHashCode()
        result = 31 * result + grading.contentHashCode()
        result = 31 * result + incorrectQuestions.hashCode()
        result = 31 * result + correctAnswers.hashCode()
        return result
    }
} 