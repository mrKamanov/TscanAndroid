package com.example.myapplication.ui

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.example.myapplication.R

/**
 * Класс для создания и отображения UI маркеров результатов OMR
 * Содержит методы создания галочек, крестиков и маркеров правильных ответов
 */
class MarkerRenderer(private val context: Context) {
    
    /**
     * Создает UI маркеры для отображения результатов OMR
     */
    fun createUIMarkers(
        resultsOverlay: FrameLayout,
        selectedAnswers: IntArray, 
        grading: IntArray, 
        correctAnswers: List<Int>,
        questionsCount: Int,
        choicesCount: Int
    ) {
        // Очищаем предыдущие маркеры
        resultsOverlay.removeAllViews()
        
        // Показываем overlay с результатами
        resultsOverlay.visibility = View.VISIBLE
        
        val containerWidth = resultsOverlay.width
        val containerHeight = resultsOverlay.height
        
        if (containerWidth == 0 || containerHeight == 0) {
            // Если размеры еще не известны, создаем маркеры позже
            resultsOverlay.post { 
                createUIMarkers(resultsOverlay, selectedAnswers, grading, correctAnswers, questionsCount, choicesCount) 
            }
            return
        }
        
        val cellWidth = containerWidth / choicesCount
        val cellHeight = containerHeight / questionsCount
        
        for (question in 0 until questionsCount) {
            val selectedChoice = selectedAnswers[question]
            val centerX = (selectedChoice * cellWidth) + (cellWidth / 2)
            val centerY = (question * cellHeight) + (cellHeight / 2)
            
            val isCorrect = grading[question] == 1
            // Маркер занимает 70% от меньшей стороны ячейки, но не меньше 20dp
            val markSize = maxOf(20, minOf(cellWidth, cellHeight) * 7 / 10)
            
            if (isCorrect) {
                // Зеленая галочка для правильного ответа
                val checkMark = createCheckMark(markSize)
                checkMark.layoutParams = FrameLayout.LayoutParams(
                    markSize, markSize
                ).apply {
                    leftMargin = centerX - markSize / 2
                    topMargin = centerY - markSize / 2
                }
                resultsOverlay.addView(checkMark)
                
            } else {
                // Красный крестик для неправильного ответа
                val crossMark = createCrossMark(markSize)
                crossMark.layoutParams = FrameLayout.LayoutParams(
                    markSize, markSize
                ).apply {
                    leftMargin = centerX - markSize / 2
                    topMargin = centerY - markSize / 2
                }
                resultsOverlay.addView(crossMark)
                
                // Показываем правильный ответ желтым кружком со звездочкой
                if (question < correctAnswers.size) {
                    val correctChoice = correctAnswers[question]
                    val correctCenterX = (correctChoice * cellWidth) + (cellWidth / 2)
                    val correctCenterY = (question * cellHeight) + (cellHeight / 2)
                    
                    val correctMark = createCorrectAnswerMark(markSize)
                    correctMark.layoutParams = FrameLayout.LayoutParams(
                        markSize, markSize
                    ).apply {
                        leftMargin = correctCenterX - markSize / 2
                        topMargin = correctCenterY - markSize / 2
                    }
                    resultsOverlay.addView(correctMark)
                }
            }
        }
    }
    
    /**
     * Создает маркер с галочкой для правильного ответа
     */
    private fun createCheckMark(size: Int): View {
        return View(context).apply {
            // Создаем кастомный Drawable с галочкой
            background = object : Drawable() {
                override fun draw(canvas: Canvas) {
                    val paint = Paint().apply {
                        color = android.graphics.Color.rgb(76, 175, 80) // Зеленый фон
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    
                    // Рисуем круг
                    val centerX = bounds.width() / 2f
                    val centerY = bounds.height() / 2f
                    val radius = minOf(bounds.width(), bounds.height()) / 2f - 4f
                    canvas.drawCircle(centerX, centerY, radius, paint)
                    
                    // Рисуем белую обводку
                    paint.style = Paint.Style.STROKE
                    paint.color = android.graphics.Color.WHITE
                    paint.strokeWidth = size / 16f
                    canvas.drawCircle(centerX, centerY, radius, paint)
                    
                    // Рисуем галочку
                    paint.style = Paint.Style.STROKE
                    paint.color = android.graphics.Color.WHITE
                    paint.strokeWidth = size / 12f
                    paint.strokeCap = Paint.Cap.ROUND
                    
                    val checkSize = radius * 0.6f
                    val startX = centerX - checkSize / 2
                    val startY = centerY
                    val midX = centerX - checkSize / 6
                    val midY = centerY + checkSize / 3
                    val endX = centerX + checkSize / 2
                    val endY = centerY - checkSize / 3
                    
                    // Рисуем галочку двумя линиями
                    canvas.drawLine(startX, startY, midX, midY, paint)
                    canvas.drawLine(midX, midY, endX, endY, paint)
                }
                
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
            }
        }
    }
    
    /**
     * Создает маркер с крестиком для неправильного ответа
     */
    private fun createCrossMark(size: Int): View {
        return View(context).apply {
            // Создаем кастомный Drawable с крестиком
            background = object : Drawable() {
                override fun draw(canvas: Canvas) {
                    val paint = Paint().apply {
                        color = context.resources.getColor(R.color.error, context.theme) // Красный фон
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    
                    // Рисуем круг
                    val centerX = bounds.width() / 2f
                    val centerY = bounds.height() / 2f
                    val radius = minOf(bounds.width(), bounds.height()) / 2f - 4f
                    canvas.drawCircle(centerX, centerY, radius, paint)
                    
                    // Рисуем белую обводку
                    paint.style = Paint.Style.STROKE
                    paint.color = android.graphics.Color.WHITE
                    paint.strokeWidth = size / 16f
                    canvas.drawCircle(centerX, centerY, radius, paint)
                    
                    // Рисуем крестик
                    paint.style = Paint.Style.STROKE
                    paint.color = android.graphics.Color.WHITE
                    paint.strokeWidth = size / 12f
                    paint.strokeCap = Paint.Cap.ROUND
                    
                    val crossSize = radius * 0.6f
                    val leftX = centerX - crossSize / 2
                    val rightX = centerX + crossSize / 2
                    val topY = centerY - crossSize / 2
                    val bottomY = centerY + crossSize / 2
                    
                    // Рисуем крестик двумя линиями
                    canvas.drawLine(leftX, topY, rightX, bottomY, paint)
                    canvas.drawLine(leftX, bottomY, rightX, topY, paint)
                }
                
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
            }
        }
    }
    
    /**
     * Создает маркер с мишенью для правильного ответа
     */
    private fun createCorrectAnswerMark(size: Int): View {
        return View(context).apply {
            // Создаем кастомный Drawable с мишенью
            background = object : Drawable() {
                override fun draw(canvas: Canvas) {
                    val centerX = bounds.width() / 2f
                    val centerY = bounds.height() / 2f
                    val maxRadius = minOf(bounds.width(), bounds.height()) / 2f - 4f
                    
                    val paint = Paint().apply {
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    
                    // Рисуем концентрические круги мишени (от внешнего к внутреннему)
                    // Внешний круг - белый
                    paint.color = android.graphics.Color.WHITE
                    canvas.drawCircle(centerX, centerY, maxRadius, paint)
                    
                    // Следующий круг - красный
                    paint.color = android.graphics.Color.RED
                    canvas.drawCircle(centerX, centerY, maxRadius * 0.8f, paint)
                    
                    // Следующий круг - белый
                    paint.color = android.graphics.Color.WHITE
                    canvas.drawCircle(centerX, centerY, maxRadius * 0.6f, paint)
                    
                    // Следующий круг - красный
                    paint.color = android.graphics.Color.RED
                    canvas.drawCircle(centerX, centerY, maxRadius * 0.4f, paint)
                    
                    // Центральный круг - белый
                    paint.color = android.graphics.Color.WHITE
                    canvas.drawCircle(centerX, centerY, maxRadius * 0.2f, paint)
                    
                    // Рисуем внешнюю обводку
                    paint.style = Paint.Style.STROKE
                    paint.color = android.graphics.Color.rgb(255, 193, 7) // Желтая обводка
                    paint.strokeWidth = size / 16f
                    canvas.drawCircle(centerX, centerY, maxRadius, paint)
                }
                
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
            }
        }
    }
} 