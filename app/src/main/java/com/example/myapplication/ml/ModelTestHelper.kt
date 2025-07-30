package com.example.myapplication.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

/**
 * Вспомогательный класс для тестирования различных форматов моделей
 */
class ModelTestHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "ModelTestHelper"
    }
    
    private val modelManager = OMRModelManager(context)
    
    /**
     * Тестирует все доступные форматы моделей
     */
    fun testAllFormats(testBitmap: Bitmap): TestResults {
        val results = mutableMapOf<OMRModelManager.ModelFormat, TestResult>()
        
        for (format in modelManager.getAvailableFormats()) {
            Log.i(TAG, "🧪 Тестирование формата: $format")
            
            try {
                // Переключаемся на формат
                modelManager.setModelFormat(format)
                
                // Ждем инициализации
                Thread.sleep(1000)
                
                if (modelManager.isModelReady()) {
                    // Тестируем предсказание
                    val startTime = System.currentTimeMillis()
                    val prediction = modelManager.predictCell(testBitmap)
                    val endTime = System.currentTimeMillis()
                    
                    val result = TestResult(
                        format = format,
                        success = true,
                        prediction = prediction,
                        inferenceTime = endTime - startTime,
                        error = null
                    )
                    
                    results[format] = result
                    Log.i(TAG, "✅ $format: ${prediction.getDescription()} (${endTime - startTime}ms)")
                    
                } else {
                    val result = TestResult(
                        format = format,
                        success = false,
                        prediction = null,
                        inferenceTime = 0,
                        error = "Модель не готова"
                    )
                    results[format] = result
                    Log.e(TAG, "❌ $format: Модель не готова")
                }
                
            } catch (e: Exception) {
                val result = TestResult(
                    format = format,
                    success = false,
                    prediction = null,
                    inferenceTime = 0,
                    error = e.message ?: "Неизвестная ошибка"
                )
                results[format] = result
                Log.e(TAG, "❌ $format: ${e.message}")
            }
        }
        
        return TestResults(results)
    }
    
    /**
     * Сравнивает производительность форматов
     */
    fun benchmarkFormats(testBitmaps: List<Bitmap>): BenchmarkResults {
        val results = mutableMapOf<OMRModelManager.ModelFormat, BenchmarkResult>()
        
        for (format in modelManager.getAvailableFormats()) {
            Log.i(TAG, "⚡ Бенчмарк формата: $format")
            
            try {
                modelManager.setModelFormat(format)
                Thread.sleep(1000)
                
                if (modelManager.isModelReady()) {
                    val times = mutableListOf<Long>()
                    val predictions = mutableListOf<PredictionResult>()
                    
                    // Прогреваем модель
                    for (i in 0 until 3) {
                        modelManager.predictCell(testBitmaps.first())
                    }
                    
                    // Основной тест
                    for (bitmap in testBitmaps) {
                        val startTime = System.currentTimeMillis()
                        val prediction = modelManager.predictCell(bitmap)
                        val endTime = System.currentTimeMillis()
                        
                        times.add(endTime - startTime)
                        predictions.add(prediction)
                    }
                    
                    val avgTime = times.average().toLong()
                    val minTime = times.minOrNull() ?: 0
                    val maxTime = times.maxOrNull() ?: 0
                    
                    val result = BenchmarkResult(
                        format = format,
                        success = true,
                        averageTime = avgTime,
                        minTime = minTime,
                        maxTime = maxTime,
                        totalPredictions = predictions.size,
                        error = null
                    )
                    
                    results[format] = result
                    Log.i(TAG, "⚡ $format: среднее время ${avgTime}ms (мин: ${minTime}ms, макс: ${maxTime}ms)")
                    
                } else {
                    val result = BenchmarkResult(
                        format = format,
                        success = false,
                        averageTime = 0,
                        minTime = 0,
                        maxTime = 0,
                        totalPredictions = 0,
                        error = "Модель не готова"
                    )
                    results[format] = result
                }
                
            } catch (e: Exception) {
                val result = BenchmarkResult(
                    format = format,
                    success = false,
                    averageTime = 0,
                    minTime = 0,
                    maxTime = 0,
                    totalPredictions = 0,
                    error = e.message ?: "Неизвестная ошибка"
                )
                results[format] = result
                Log.e(TAG, "❌ $format: ${e.message}")
            }
        }
        
        return BenchmarkResults(results)
    }
    
    /**
     * Получает информацию о текущей модели
     */
    fun getModelInfo(): String {
        return modelManager.getModelInfo()
    }
    
    /**
     * Освобождает ресурсы
     */
    fun release() {
        modelManager.release()
    }
}

/**
 * Результат тестирования одного формата
 */
data class TestResult(
    val format: OMRModelManager.ModelFormat,
    val success: Boolean,
    val prediction: PredictionResult?,
    val inferenceTime: Long,
    val error: String?
)

/**
 * Результаты тестирования всех форматов
 */
data class TestResults(
    val results: Map<OMRModelManager.ModelFormat, TestResult>
) {
    fun getBestFormat(): OMRModelManager.ModelFormat? {
        return results.entries
            .filter { it.value.success }
            .minByOrNull { it.value.inferenceTime }
            ?.key
    }
    
    fun getSummary(): String {
        val successful = results.values.count { it.success }
        val total = results.size
        
        return """
        Результаты тестирования:
        Успешно: $successful/$total
        ${results.entries.joinToString("\n") { 
            "${it.key}: ${if (it.value.success) "✅ ${it.value.inferenceTime}ms" else "❌ ${it.value.error}"}" 
        }}
        """.trimIndent()
    }
}

/**
 * Результат бенчмарка одного формата
 */
data class BenchmarkResult(
    val format: OMRModelManager.ModelFormat,
    val success: Boolean,
    val averageTime: Long,
    val minTime: Long,
    val maxTime: Long,
    val totalPredictions: Int,
    val error: String?
)

/**
 * Результаты бенчмарка всех форматов
 */
data class BenchmarkResults(
    val results: Map<OMRModelManager.ModelFormat, BenchmarkResult>
) {
    fun getFastestFormat(): OMRModelManager.ModelFormat? {
        return results.entries
            .filter { it.value.success }
            .minByOrNull { it.value.averageTime }
            ?.key
    }
    
    fun getSummary(): String {
        val successful = results.values.count { it.success }
        val total = results.size
        
        return """
        Результаты бенчмарка:
        Успешно: $successful/$total
        ${results.entries.joinToString("\n") { 
            if (it.value.success) {
                "${it.key}: среднее ${it.value.averageTime}ms (${it.value.minTime}-${it.value.maxTime}ms)"
            } else {
                "${it.key}: ❌ ${it.value.error}"
            }
        }}
        """.trimIndent()
    }
} 