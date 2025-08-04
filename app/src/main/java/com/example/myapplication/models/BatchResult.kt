package com.example.myapplication.models

import android.graphics.Bitmap

data class BatchResult(
    val id: String,
    val filename: String,
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
    val gridVisualization: Bitmap? = null
) {
    data class ErrorDetail(
        val questionNumber: Int,
        val selectedAnswer: Int,
        val correctAnswer: Int
    )
} 