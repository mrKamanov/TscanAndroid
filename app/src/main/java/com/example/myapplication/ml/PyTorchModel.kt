package com.example.myapplication.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.IOException

/**
 * Реализация для работы с PyTorch моделью
 */
class PyTorchModel(
    private val context: Context,
    private val modelPath: String,
    private val config: ModelConfig
) : ModelInterface {
    
    companion object {
        private const val TAG = "PyTorchModel"
        private const val INPUT_SIZE = 224
    }
    
    private var model: Module? = null
    
    override fun initialize(): Boolean {
        try {
            Log.i(TAG, "🔄 Инициализация PyTorch модели...")
            
            // Проверяем наличие файла в assets
            try {
                context.assets.open(modelPath).use { input ->
                    Log.i(TAG, "✅ Файл модели найден в assets: $modelPath")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Файл модели не найден в assets: $modelPath")
                return false
            }
            
            // Копируем файл модели во временную директорию
            val tempFile = File(context.cacheDir, "omr_model.pt")
            Log.i(TAG, "📁 Копируем модель во временную директорию: ${tempFile.absolutePath}")
            
            context.assets.open(modelPath).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.i(TAG, "📊 Размер файла модели: ${tempFile.length()} байт")
            
            // Загружаем модель из временного файла
            Log.i(TAG, "🔄 Загружаем PyTorch модель...")
            model = Module.load(tempFile.absolutePath)
            
            Log.i(TAG, "✅ PyTorch модель инициализирована успешно")
            return true
            
        } catch (e: IOException) {
            Log.e(TAG, "❌ Ошибка инициализации PyTorch модели: ${e.message}")
            e.printStackTrace()
            return false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Неожиданная ошибка инициализации PyTorch модели: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    override fun predict(bitmap: Bitmap): PredictionResult {
        if (!isReady()) {
            Log.e(TAG, "❌ PyTorch модель не готова")
            return PredictionResult(isFilled = false, confidence = 0f, probabilities = floatArrayOf(0f, 0f))
        }
        
        try {
            // Предобработка изображения
            val inputTensor = preprocessImage(bitmap)
            
            // Запуск инференса
            val output = model!!.forward(IValue.from(inputTensor))
            val outputTensor = output.toTensor()
            
            // Постобработка результатов
            val result = postprocessResults(outputTensor)
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка предсказания PyTorch: ${e.message}")
            return PredictionResult(isFilled = false, confidence = 0f, probabilities = floatArrayOf(0f, 0f))
        }
    }
    
    override fun predictBatch(bitmaps: List<Bitmap>): List<PredictionResult> {
        if (!isReady()) {
            Log.e(TAG, "❌ PyTorch модель не готова")
            return List(bitmaps.size) { 
                PredictionResult(isFilled = false, confidence = 0f, probabilities = floatArrayOf(0f, 0f)) 
            }
        }
        
        try {
            // Оптимизированная предобработка всех изображений
            val inputTensors = bitmaps.map { preprocessImageOptimized(it) }
            
            // Обрабатываем все тензоры одним вызовом для ускорения
            val results = mutableListOf<PredictionResult>()
            
            for (inputTensor in inputTensors) {
                // Запуск инференса
                val output = model!!.forward(IValue.from(inputTensor))
                val outputTensor = output.toTensor()
                
                // Быстрая постобработка результатов
                val result = postprocessResultsOptimized(outputTensor)
                results.add(result)
            }
            
            return results
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка батч-предсказания PyTorch: ${e.message}")
            return List(bitmaps.size) { 
                PredictionResult(isFilled = false, confidence = 0f, probabilities = floatArrayOf(0f, 0f)) 
            }
        }
    }
    
    override fun release() {
        model = null
        Log.i(TAG, "🔧 PyTorch модель освобождена")
    }
    
    override fun isReady(): Boolean {
        return model != null
    }
    
    /**
     * Предобрабатывает изображение для PyTorch модели
     */
    private fun preprocessImage(bitmap: Bitmap): Tensor {
        // Изменяем размер до 224x224
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        
        // Нормализация с использованием ImageNet статистик
        return TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            config.mean,
            config.std
        )
    }
    
    /**
     * Оптимизированная предобработка изображения (быстрее)
     */
    private fun preprocessImageOptimized(bitmap: Bitmap): Tensor {
        // Изменяем размер до 224x224 (без создания нового объекта если размер уже правильный)
        val resizedBitmap = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        }
        
        return TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            config.mean,
            config.std
        )
    }
    
    /**
     * Постобрабатывает результаты PyTorch модели
     */
    private fun postprocessResults(outputTensor: Tensor): PredictionResult {
        val outputArray = outputTensor.dataAsFloatArray
        
        // Применяем softmax для получения вероятностей
        val probabilities = softmax(outputArray)
        
        // Определяем класс с максимальной вероятностью
        val predictedClass = if (probabilities[1] > probabilities[0]) 1 else 0
        val confidence = probabilities[predictedClass]
        val isFilled = predictedClass == 1
        
        Log.d(TAG, "Предсказание: ${if (isFilled) "ЗАПОЛНЕНО" else "ПУСТОЕ"} (уверенность: ${confidence * 100}%)")
        
        return PredictionResult(
            isFilled = isFilled,
            confidence = confidence,
            probabilities = probabilities
        )
    }
    
    /**
     * Оптимизированная постобработка результатов (быстрее, без логирования)
     */
    private fun postprocessResultsOptimized(outputTensor: Tensor): PredictionResult {
        val outputArray = outputTensor.dataAsFloatArray
        
        // Быстрый softmax без лишних операций
        val maxLogit = maxOf(outputArray[0], outputArray[1])
        val exp0 = Math.exp((outputArray[0] - maxLogit).toDouble())
        val exp1 = Math.exp((outputArray[1] - maxLogit).toDouble())
        val sumExp = exp0 + exp1
        
        val prob0 = (exp0 / sumExp).toFloat()
        val prob1 = (exp1 / sumExp).toFloat()
        
        // Определяем результат
        val isFilled = prob1 > prob0
        val confidence = if (isFilled) prob1 else prob0
        
        return PredictionResult(
            isFilled = isFilled,
            confidence = confidence,
            probabilities = floatArrayOf(prob0, prob1)
        )
    }
    
    /**
     * Применяет softmax к массиву значений
     */
    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val expLogits = logits.map { Math.exp((it - maxLogit).toDouble()) }
        val sumExpLogits = expLogits.sum()
        
        return expLogits.map { (it / sumExpLogits).toFloat() }.toFloatArray()
    }
} 