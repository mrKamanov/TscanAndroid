package com.example.myapplication.models

import android.graphics.Bitmap
import com.example.myapplication.models.FixedAnswer

data class BatchResult(
    val id: String,
    val filename: String,
    val workNumber: Int, // Номер работы по порядку загрузки
    val originalImage: Bitmap?,
    val processedImage: Bitmap?,
    val correctCount: Int,
    val totalQuestions: Int,
    val percentage: Double,
    val grade: Int,
    val errors: List<ErrorDetail>,
    val correctAnswers: List<Int>,
    val selectedAnswers: List<Int>,
    val contourVisualization: Bitmap? = null,
    val gridVisualization: Bitmap? = null,
    val fixedAnswers: List<FixedAnswer> = emptyList(), // Список исправлений
    val isAddedToReport: Boolean = false // Флаг добавления в отчет
) {
    data class ErrorDetail(
        val questionNumber: Int,
        val selectedAnswer: Int,
        val correctAnswer: Int
    )
} 