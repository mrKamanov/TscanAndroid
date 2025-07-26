package com.example.myapplication.ui

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.widget.Toast
import android.util.Log
import com.example.myapplication.R

/**
 * Класс для управления сеткой ответов
 * Содержит методы создания, настройки и обновления сетки
 */
class GridManager(
    private val context: Context,
    private val gridOverlay: LinearLayout
) {
    
    companion object {
        private const val TAG = "GridManager"
    }
    
    // Переменные для работы с сеткой
    private var questionsCount = 5
    private var choicesCount = 5
    private var correctAnswers = mutableListOf<Int>()
    private var isGridVisible = false
    
    /**
     * Применяет настройки сетки
     */
    fun applyGridSettings(newQuestions: Int, newChoices: Int): Boolean {
        try {
            if (newQuestions < 1 || newQuestions > 50) {
                Toast.makeText(context, "Количество вопросов должно быть от 1 до 50", Toast.LENGTH_SHORT).show()
                return false
            }
            
            if (newChoices < 1 || newChoices > 10) {
                Toast.makeText(context, "Количество вариантов должно быть от 1 до 10", Toast.LENGTH_SHORT).show()
                return false
            }
            
            questionsCount = newQuestions
            choicesCount = newChoices
            correctAnswers.clear()
            
            // Пересоздаем сетку с новыми параметрами
            createAnswersGrid()
            
            Toast.makeText(context, "Настройки сетки применены", Toast.LENGTH_SHORT).show()
            return true
            
        } catch (e: NumberFormatException) {
            Toast.makeText(context, "Введите корректные числа", Toast.LENGTH_SHORT).show()
            return false
        }
    }
    
    /**
     * Создает сетку ответов
     */
    fun createAnswersGrid() {
        gridOverlay.removeAllViews()
        
        // Получаем размеры контейнера камеры
        val containerWidth = gridOverlay.width
        val containerHeight = gridOverlay.height
        
        if (containerWidth == 0 || containerHeight == 0) {
            // Если размеры еще не известны, создаем сетку позже
            gridOverlay.post { createAnswersGrid() }
            return
        }
        
        val cellWidth = containerWidth / choicesCount
        val cellHeight = containerHeight / questionsCount
        
        // Создаем контейнер для сетки с границами
        val gridContainer = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        // Создаем фон с границами сетки
        val gridBackground = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = createGridBackground(containerWidth, containerHeight, cellWidth, cellHeight)
        }
        gridContainer.addView(gridBackground)
        
        // Создаем ячейки для выбора ответов
        for (questionIndex in 0 until questionsCount) {
            for (choiceIndex in 0 until choicesCount) {
                val cellView = TextView(context).apply {
                    text = "${questionIndex + 1}.${choiceIndex + 1}"
                    textSize = 12f
                    setTextColor(context.resources.getColor(R.color.text_inverse, context.theme))
                    gravity = android.view.Gravity.CENTER
                    background = android.graphics.drawable.ColorDrawable(
                        android.graphics.Color.argb(50, 255, 255, 255)
                    )
                    
                    layoutParams = FrameLayout.LayoutParams(
                        cellWidth,
                        cellHeight
                    ).apply {
                        leftMargin = choiceIndex * cellWidth
                        topMargin = questionIndex * cellHeight
                    }
                    
                    // Сохраняем информацию о вопросе и варианте
                    tag = Pair(questionIndex, choiceIndex)
                    
                    // Обработчик клика для выбора ответа
                    setOnClickListener {
                        // Убираем выделение со всех ячеек в этом вопросе
                        for (i in 0 until choicesCount) {
                            val otherCell = gridContainer.findViewWithTag<TextView>(Pair(questionIndex, i))
                            otherCell?.background = android.graphics.drawable.ColorDrawable(
                                android.graphics.Color.argb(50, 255, 255, 255)
                            )
                        }
                        
                        // Выделяем выбранную ячейку
                        background = android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.argb(150, 0, 255, 0)
                        )
                    }
                }
                
                gridContainer.addView(cellView)
            }
        }
        
        gridOverlay.addView(gridContainer)
    }
    
    /**
     * Создает фон с границами сетки
     */
    private fun createGridBackground(width: Int, height: Int, cellWidth: Int, cellHeight: Int): Drawable {
        return object : Drawable() {
            override fun draw(canvas: Canvas) {
                val paint = Paint().apply {
                    color = android.graphics.Color.argb(200, 255, 79, 0)
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                }
                
                // Рисуем вертикальные линии
                for (i in 0..choicesCount) {
                    val x = i * cellWidth.toFloat()
                    canvas.drawLine(x, 0f, x, height.toFloat(), paint)
                }
                
                // Рисуем горизонтальные линии
                for (i in 0..questionsCount) {
                    val y = i * cellHeight.toFloat()
                    canvas.drawLine(0f, y, width.toFloat(), y, paint)
                }
            }
            
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }
    
    /**
     * Обновляет правильные ответы на основе выбранных ячеек
     */
    fun updateCorrectAnswers(): Boolean {
        correctAnswers.clear()
        var allQuestionsAnswered = true
        
        // Проходим по всем вопросам
        for (questionIndex in 0 until questionsCount) {
            var questionAnswered = false
            
            // Ищем выбранную ячейку для текущего вопроса
            for (choiceIndex in 0 until choicesCount) {
                // Ищем в контейнере сетки
                val gridContainer = gridOverlay.getChildAt(0) as? FrameLayout
                val cellView = gridContainer?.findViewWithTag<TextView>(Pair(questionIndex, choiceIndex))
                if (cellView != null) {
                    val background = cellView.background as? android.graphics.drawable.ColorDrawable
                    if (background?.color == android.graphics.Color.argb(150, 0, 255, 0)) {
                        correctAnswers.add(choiceIndex)
                        questionAnswered = true
                        break
                    }
                }
            }
            
            if (!questionAnswered) {
                allQuestionsAnswered = false
                break
            }
        }
        
        if (allQuestionsAnswered) {
            Toast.makeText(context, "Правильные ответы обновлены", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Правильные ответы: $correctAnswers")
            return true
        } else {
            Toast.makeText(context, "Выберите ответ для каждого вопроса", Toast.LENGTH_SHORT).show()
            return false
        }
    }
    
    /**
     * Устанавливает видимость сетки
     */
    fun setGridVisible(visible: Boolean) {
        isGridVisible = visible
        gridOverlay.visibility = if (visible) View.VISIBLE else View.GONE
    }
    
    /**
     * Возвращает текущую видимость сетки
     */
    fun isGridVisible(): Boolean = isGridVisible
    
    /**
     * Возвращает количество вопросов
     */
    fun getQuestionsCount(): Int = questionsCount
    
    /**
     * Возвращает количество вариантов ответов
     */
    fun getChoicesCount(): Int = choicesCount
    
    /**
     * Возвращает правильные ответы
     */
    fun getCorrectAnswers(): List<Int> = correctAnswers.toList()
    
    /**
     * Устанавливает количество вопросов
     */
    fun setQuestionsCount(count: Int) {
        questionsCount = count
    }
    
    /**
     * Устанавливает количество вариантов ответов
     */
    fun setChoicesCount(count: Int) {
        choicesCount = count
    }
} 