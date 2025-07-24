package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

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
    private val iconsTopLeft = listOf("📝", "✎", "📋")
    private val iconsBottomRight = listOf("📚", "🎓", "✏️")

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

        // Верхняя левая группа иконок
        val baseY3 = h * 0.13f + 10f * sin(time + 2)
        val baseX3 = w * 0.08f
        paint.textSize = 54f
        paint.color = Color.parseColor("#88C0D0")
        iconsTopLeft.forEachIndexed { i, s ->
            canvas.drawText(s, baseX3 + i * 54f, baseY3, paint)
        }

        // Нижняя правая группа иконок
        val baseY4 = h * 0.92f + 10f * sin(time + 3)
        val baseX4 = w * 0.7f
        paint.textSize = 54f
        paint.color = Color.parseColor("#88C0D0")
        iconsBottomRight.forEachIndexed { i, s ->
            canvas.drawText(s, baseX4 + i * 54f, baseY4, paint)
        }

        // Фоновые кружки
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.parseColor("#81A1C1")
        canvas.drawOval(RectF(w*0.2f, h*0.2f, w*0.5f, h*0.5f), paint)
        canvas.drawOval(RectF(w*0.6f, h*0.6f, w*0.9f, h*0.9f), paint)
        paint.style = Paint.Style.FILL
    }
} 