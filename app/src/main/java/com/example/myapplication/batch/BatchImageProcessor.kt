package com.example.myapplication.batch

import android.graphics.Bitmap
import android.util.Log
import com.example.myapplication.ml.OMRModelManager
import com.example.myapplication.ml.FixedAnswerCallback
import com.example.myapplication.models.OMRResult
import com.example.myapplication.processing.ImageProcessor
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Обработчик изображений для пакетной обработки
 * Отделен от основного ImageProcessor для избежания конфликтов
 */
class BatchImageProcessor {
    
    companion object {
        private const val TAG = "BatchImageProcessor"
    }
    
    private var mlModel: OMRModelManager? = null
    
    /**
     * Устанавливает ML модель
     */
    fun setMLModel(model: OMRModelManager) {
        mlModel = model
    }
    
    /**
     * Обрабатывает изображение с ML моделью
     */
    fun processFrameWithML(
        bitmap: Bitmap,
        questionsCount: Int,
        choicesCount: Int,
        correctAnswers: List<Int>,
        onProgressUpdate: ((Int, Int, Boolean) -> Unit)? = null,
        fixedAnswerCallback: FixedAnswerCallback? = null
    ): OMRResult? {
        try {
            if (mlModel == null) {
                Log.e(TAG, "❌ ML модель не установлена")
                return null
            }
            
            Log.d(TAG, "🚀 Начинаем ML обработку изображения для пакетной обработки...")
            Log.d(TAG, "📋 Параметры: вопросы=$questionsCount, варианты=$choicesCount, правильных ответов=${correctAnswers.size}")
            
            // Используем основной ImageProcessor для обработки
            val imageProcessor = ImageProcessor()
            imageProcessor.setMLModel(mlModel!!)
            
            // Обрабатываем изображение с поддержкой исправлений
            val result = imageProcessor.processFrameWithML(
                bitmap,
                questionsCount,
                choicesCount,
                correctAnswers,
                onProgressUpdate,
                fixedAnswerCallback
            )
            
            if (result != null) {
                Log.d(TAG, "✅ ML обработка завершена успешно")
                Log.d(TAG, "📊 Результат: ${result.selectedAnswers.size} ответов, ${result.fixedAnswers.size} исправлений")
            } else {
                Log.w(TAG, "⚠️ ML обработка не вернул результат")
            }
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка ML обработки: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Обрабатывает тестовый бланк с приоритетом (как в реальном времени)
     * Примечание: используем обычную обработку, так как приоритетная приватная
     */
    fun processTestSheetWithPriority(
        bitmap: Bitmap,
        questionsCount: Int,
        choicesCount: Int,
        correctAnswers: List<Int>,
        onProgressUpdate: ((Int, Int, Boolean) -> Unit)? = null,
        fixedAnswerCallback: FixedAnswerCallback? = null
    ): OMRResult? {
        try {
            Log.d(TAG, "🔍 Обработка тестового бланка: ${bitmap.width}x${bitmap.height}")
            
            // Используем обычную обработку вместо приватной приоритетной
            return processFrameWithML(
                bitmap,
                questionsCount,
                choicesCount,
                correctAnswers,
                onProgressUpdate,
                fixedAnswerCallback
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка обработки тестового бланка: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
}
