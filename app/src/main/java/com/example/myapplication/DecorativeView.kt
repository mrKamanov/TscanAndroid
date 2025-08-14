package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.math.cos

class DecorativeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var time = 0f

    private val animator = object : Runnable {
        override fun run() {
            time += 0.04f
            invalidate()
            postDelayed(this, 16)
        }
    }

    private val letters1 = listOf("✓", "А", "Б", "В", "Г", "Д", "✕")
    private val letters2 = listOf("✓", "1", "2", "3", "✕")
    private val iconsTopLeft = listOf("◊", "◆", "◇")
    private val iconsBottomRight = listOf("●", "○", "◐")

    init {
        post(animator)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Нижняя левая группа букв
        val baseY1 = h * 0.85f + 20f * sin(time)
        val baseX1 = w * 0.12f
        paint.textSize = 48f
        letters1.forEachIndexed { i, s ->
            paint.color = when (s) {
                "✓" -> Color.parseColor("#A3BE8C")
                "✕" -> Color.parseColor("#BF616A")
                else -> Color.parseColor("#81A1C1")
            }
            canvas.drawText(s, baseX1 + i * 48f, baseY1 + 12f * sin(time + i), paint)
        }

        // Верхняя правая группа цифр
        val baseY2 = h * 0.18f + 10f * sin(time + 1)
        val baseX2 = w * 0.65f
        paint.textSize = 36f
        letters2.forEachIndexed { i, s ->
            paint.color = when (s) {
                "✓" -> Color.parseColor("#A3BE8C")
                "✕" -> Color.parseColor("#BF616A")
                else -> Color.parseColor("#81A1C1")
            }
            canvas.drawText(s, baseX2 + i * 36f, baseY2 + 8f * sin(time + i), paint)
        }



        // Основная сетка 4x4 в центре
        drawGrid(canvas, w, h, 0.5f, 0.5f, 1.0f, -12f)
        
        // Вторая сетка - маленькая, в левом верхнем углу, наклонена в другую сторону
        drawGrid(canvas, w, h, 0.2f, 0.2f, 0.6f, 15f)
        
        // Третья сетка - средняя, в правом нижнем углу, наклонена в третью сторону
        drawGrid(canvas, w, h, 0.8f, 0.85f, 0.8f, -8f)
    }
    
    private fun drawGrid(canvas: Canvas, w: Float, h: Float, centerXPercent: Float, centerYPercent: Float, scale: Float, baseRotation: Float) {
        // Сохраняем состояние canvas для поворота
        canvas.save()
        
        // Центр для поворота с учетом позиции
        val centerX = w * centerXPercent
        val centerY = h * centerYPercent
        
        // Плавное покачивание бланка с разной скоростью для каждого
        val rotationAngle = baseRotation + 2f * sin(time * (0.5f + scale * 0.3f))
        val floatOffset = 3f * sin(time * (0.3f + scale * 0.2f))
        
        canvas.translate(centerX, centerY)
        canvas.rotate(rotationAngle)
        canvas.translate(-centerX, -centerY + floatOffset)
        
        // Размеры бланка с масштабированием
        val baseGridSize = 320f
        val gridSize = baseGridSize * scale
        val cellSize = gridSize / 4f
        val startX = centerX - gridSize / 2f
        val startY = centerY - gridSize / 2f
        
        // Фон бланка с масштабированными отступами
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#2E3440")
        val padding = 30f * scale
        val backgroundRect = RectF(startX - padding, startY - padding, startX + gridSize + padding, startY + gridSize + padding)
        canvas.drawRoundRect(backgroundRect, 12f * scale, 12f * scale, paint)
        
        // Обводка бланка
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = Color.parseColor("#81A1C1")
        canvas.drawRoundRect(backgroundRect, 12f * scale, 12f * scale, paint)
        
        // Рисуем сетку
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * scale
        paint.color = Color.parseColor("#81A1C1")
        
        // Вертикальные линии
        for (i in 0..4) {
            canvas.drawLine(startX + i * cellSize, startY, startX + i * cellSize, startY + gridSize, paint)
        }
        
        // Горизонтальные линии
        for (i in 0..4) {
            canvas.drawLine(startX, startY + i * cellSize, startX + gridSize, startY + i * cellSize, paint)
        }
        
        // Рисуем круги с нумерацией - только 4 ряда!
        for (row in 0..3) {
            for (col in 0..3) {
                val circleX = startX + col * cellSize + cellSize / 2f
                val circleY = startY + row * cellSize + cellSize / 2f
                val radius = 24f * scale
                
                // Цвета для разных вопросов
                val questionColor = when (row + 1) {
                    1 -> Color.parseColor("#A3BE8C") // Зеленый
                    2 -> Color.parseColor("#81A1C1") // Синий
                    3 -> Color.parseColor("#EBCB8B") // Оранжевый
                    4 -> Color.parseColor("#B48EAD") // Розовый
                    else -> Color.parseColor("#81A1C1")
                }
                
                // Обводка круга
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                paint.color = questionColor
                canvas.drawCircle(circleX, circleY, radius, paint)
                
                // Нумерация с масштабированием
                paint.style = Paint.Style.FILL
                paint.textSize = 12f * scale
                paint.color = Color.parseColor("#D8DEE9")
                val text = "${row + 1}.${col + 1}"
                val textBounds = android.graphics.Rect()
                paint.getTextBounds(text, 0, text.length, textBounds)
                canvas.drawText(text, circleX - textBounds.width() / 2f, circleY + textBounds.height() / 2f, paint)
                
                // Анимация штриховки и галочек для выбранных ячеек
                val selectedCells = listOf(
                    Pair(0, 1), // 1.2
                    Pair(1, 3), // 2.4
                    Pair(2, 0), // 3.1
                    Pair(3, 3)  // 4.4
                )
                
                if (selectedCells.contains(Pair(row, col))) {
                    val animationPhase = (time * 0.3f + (row * 2 + col) * 0.5f) % 6f
                    
                    // Фаза 0-2: появление штриховки
                    if (animationPhase < 2f) {
                        val hatchOpacity = (animationPhase / 2f).coerceIn(0f, 1f)
                        paint.style = Paint.Style.FILL
                        paint.color = Color.parseColor("#2E3440")
                        paint.alpha = (hatchOpacity * 180).toInt()
                        
                        // Рисуем штриховку
                        val hatchSpacing = 3f * scale
                        for (i in 0..(radius * 2 / hatchSpacing).toInt()) {
                            val offset = i * hatchSpacing - radius
                            val angle = 45f * Math.PI / 180f
                            val x1 = circleX + offset * cos(angle).toFloat()
                            val y1 = circleY + offset * sin(angle).toFloat()
                            val x2 = circleX + offset * cos(angle + Math.PI).toFloat()
                            val y2 = circleY + offset * sin(angle + Math.PI).toFloat()
                            
                            if (x1 >= startX && x1 <= startX + gridSize && y1 >= startY && y1 <= startY + gridSize) {
                                canvas.drawLine(x1, y1, x2, y2, paint)
                            }
                        }
                    }
                    
                    // Фаза 2-4: появление галочки
                    if (animationPhase >= 2f && animationPhase < 4f) {
                        val checkOpacity = ((animationPhase - 2f) / 2f).coerceIn(0f, 1f)
                        paint.style = Paint.Style.FILL
                        paint.color = Color.parseColor("#A3BE8C")
                        paint.alpha = (checkOpacity * 255).toInt()
                        paint.textSize = 28f * scale
                        
                        // Рисуем галочку
                        canvas.drawText("✓", circleX - 10f * scale, circleY + 8f * scale, paint)
                    }
                }
            }
        }
        
        // Восстанавливаем состояние canvas
        canvas.restore()
    }
} 