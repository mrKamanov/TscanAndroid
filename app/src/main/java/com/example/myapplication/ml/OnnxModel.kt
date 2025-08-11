package com.example.myapplication.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import ai.onnxruntime.*
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Реализация для работы с ONNX моделью
 */
class OnnxModel(
    private val context: Context,
    private val modelPath: String,
    private val config: ModelConfig
) : ModelInterface {
    
    companion object {
        private const val TAG = "OnnxModel"
    }
    
    private var session: OrtSession? = null
    private var environment: OrtEnvironment? = null
    private var inputName: String? = null
    private var outputName: String? = null
    
    // Кэш для ускорения
    private val preprocessedCache = mutableMapOf<String, FloatArray>()
    
    /**
     * Получает размер входа для текущей модели
     */
    private fun getInputSize(): Int {
        return com.example.myapplication.utils.ModelSettingsHelper.getActiveModelInputSize(context)
    }
    
    override fun initialize(): Boolean {
        try {
            Log.i(TAG, "🔄 Инициализация ONNX модели...")
            Log.i(TAG, "📁 Путь к модели: $modelPath")
            
            // Всегда копируем модель заново (для обновления при смене модели)
            val tempFile = File(context.cacheDir, "omr_model_current.onnx")
            Log.i(TAG, "📁 Копируем ONNX модель во временную директорию...")
            context.assets.open(modelPath).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.i(TAG, "📊 Размер файла модели: ${tempFile.length()} байт")
            
            // Инициализируем ONNX Runtime с GPU ускорением
            environment = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            
            // Устанавливаем оптимизации для CPU и пытаемся использовать NNAPI
            sessionOptions.setIntraOpNumThreads(4) // Используем 4 потока
            sessionOptions.setInterOpNumThreads(2) // 2 потока для межоперационных вычислений
            
            // Пытаемся использовать GPU ускорение
            try {
                val providers = OrtEnvironment.getAvailableProviders()
                val providerNames = providers.map { it.name }
                Log.i(TAG, "📋 Доступные провайдеры: ${providerNames.joinToString(", ")}")
                
                // Для Android ONNX Runtime используем другой подход
                if (providers.contains(OrtProvider.NNAPI)) {
                    // NNAPI доступен - используем его
                    Log.i(TAG, "🚀 NNAPI ускорение доступно")
                } else {
                    Log.w(TAG, "⚠️ NNAPI недоступен, используем CPU")
                }
                
                // Дополнительные оптимизации для Android
                sessionOptions.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.PARALLEL)
                
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ GPU ускорение недоступно, используем CPU: ${e.message}")
            }
            
            Log.i(TAG, "🚀 CPU оптимизации активированы")
            
            // Загружаем модель
            session = environment!!.createSession(tempFile.absolutePath, sessionOptions)
            
            // Получаем имена входов и выходов
            val inputNames = session!!.inputNames
            val outputNames = session!!.outputNames
            
            inputName = inputNames.iterator().next()
            outputName = outputNames.iterator().next()
            
            Log.i(TAG, "✅ ONNX модель инициализирована успешно")
            Log.i(TAG, "📥 Вход: $inputName")
            Log.i(TAG, "📤 Выход: $outputName")
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации ONNX модели: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    override fun predict(bitmap: Bitmap): PredictionResult {
        if (!isReady()) {
            Log.e(TAG, "❌ ONNX модель не готова")
            return PredictionResult(isFilled = false, confidence = 0f, probabilities = floatArrayOf(0f, 0f, 0f), predictedClass = "no", isFixed = false)
        }
        
        try {
            val inputSize = getInputSize()
            Log.d(TAG, "🔍 Предсказание для изображения ${bitmap.width}×${bitmap.height} с размером входа ${inputSize}×${inputSize}")
            
            // Предобрабатываем изображение
            val inputData = preprocessImage(bitmap)

            // Преобразуем в NCHW Array для ONNX
            val inputArray = Array(1) { // batch
                Array(3) { ch ->
                    Array(inputSize) { y ->
                        FloatArray(inputSize) { x ->
                            inputData[ch * inputSize * inputSize + y * inputSize + x]
                        }
                    }
                }
            }

            // Создаем тензор
            val inputTensor = OnnxTensor.createTensor(environment!!, inputArray)
            Log.d(TAG, "📊 Создан входной тензор: ${inputTensor.info.shape.contentToString()}")

            // Выполняем предсказание
            val inputs = mapOf(inputName!! to inputTensor)
            val output = session!!.run(inputs)

            // Получаем результат
            val outputTensor = output[outputName!!].get() as OnnxTensor
            val outputArray = outputTensor.value as Array<FloatArray>
            val probabilities = outputArray[0]

            // Постобрабатываем результат
            return postprocessResults(probabilities)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка предсказания ONNX модели: ${e.message}")
            e.printStackTrace()
            return PredictionResult(isFilled = false, confidence = 0f, probabilities = floatArrayOf(0f, 0f, 0f), predictedClass = "no", isFixed = false)
        }
    }
    
    override fun predictBatch(bitmaps: List<Bitmap>): List<PredictionResult> {
        if (!isReady()) {
            Log.e(TAG, "❌ ONNX модель не готова")
            return List(bitmaps.size) { 
                PredictionResult(isFilled = false, confidence = 0f, probabilities = floatArrayOf(0f, 0f, 0f), predictedClass = "no", isFixed = false) 
            }
        }
        
        val startTime = System.currentTimeMillis()
        
        // Оптимизированная батч-обработка
        val results = bitmaps.map { bitmap ->
            predict(bitmap)
        }
        
        val endTime = System.currentTimeMillis()
        val avgTime = (endTime - startTime) / bitmaps.size.toFloat()
        
        Log.i(TAG, "⚡ Батч-обработка ${bitmaps.size} изображений за ${endTime - startTime}мс (среднее: ${String.format("%.1f", avgTime)}мс на изображение)")
        return results
    }
    
    override fun release() {
        try {
            session?.close()
            environment?.close()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Ошибка при освобождении ONNX модели: ${e.message}")
        } finally {
            session = null
            environment = null
            inputName = null
            outputName = null
            
            // Очищаем кэш
            preprocessedCache.clear()
            
            Log.i(TAG, "🔧 ONNX модель освобождена")
        }
    }
    
    override fun isReady(): Boolean {
        return session != null && environment != null
    }
    
    /**
     * Перезагружает ONNX сессию (при изменении модели)
     */
    fun reloadSession() {
        Log.i(TAG, "🔄 Перезагрузка ONNX сессии...")
        try {
            // Освобождаем старую сессию
            session?.close()
            session = null
            inputName = null
            outputName = null
            
            // Очищаем кэш
            preprocessedCache.clear()
            
            // Пересоздаем сессию
            initialize()
            Log.i(TAG, "✅ ONNX сессия перезагружена")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка перезагрузки ONNX сессии: ${e.message}")
        }
    }
    
    /**
     * Предобрабатывает изображение для ONNX модели с кэшированием
     */
    private fun preprocessImage(bitmap: Bitmap): FloatArray {
        // Создаем ключ кэша на основе хеша изображения
        val cacheKey = bitmap.hashCode().toString()
        
        // Проверяем кэш
        preprocessedCache[cacheKey]?.let { cached ->
            return cached
        }
        
        val inputSize = getInputSize()
        Log.d(TAG, "🖼️ Предобработка изображения: ${bitmap.width}×${bitmap.height} → ${inputSize}×${inputSize}")
        
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputData = FloatArray(inputSize * inputSize * 3)
        
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = resizedBitmap.getPixel(x, y)
                
                // Нормализуем RGB значения
                val r = (Color.red(pixel) / 255f - config.mean[0]) / config.std[0]
                val g = (Color.green(pixel) / 255f - config.mean[1]) / config.std[1]
                val b = (Color.blue(pixel) / 255f - config.mean[2]) / config.std[2]
                
                // ONNX ожидает формат NCHW (batch, channels, height, width)
                val index = y * inputSize + x
                inputData[index] = r
                inputData[index + inputSize * inputSize] = g
                inputData[index + 2 * inputSize * inputSize] = b
            }
        }
        
        // Сохраняем в кэш (ограничиваем размер кэша)
        if (preprocessedCache.size < 10) {
            preprocessedCache[cacheKey] = inputData
        }
        
        return inputData
    }
    
    /**
     * Постобрабатывает результаты ONNX модели
     * Классы: 0 = no (пустая), 1 = yes (заполненная), 2 = fixed (исправленная)
     */
    private fun postprocessResults(probabilities: FloatArray): PredictionResult {
        // Применяем softmax (если нужно)
        val maxRawProb = probabilities.maxOrNull() ?: 0f
        val expProbs = probabilities.map { Math.exp((it - maxRawProb).toDouble()).toFloat() }
        val sumExp = expProbs.sum()
        val softmaxProbs = expProbs.map { it / sumExp }.toFloatArray()

        // Определяем результат
        val noProb = softmaxProbs[0]      // no (пустая)
        val yesProb = softmaxProbs[1]     // yes (заполненная)
        val fixedProb = softmaxProbs[2]   // fixed (исправленная)
        
        // Определяем класс с максимальной вероятностью
        val maxProbIndex = softmaxProbs.indices.maxByOrNull { softmaxProbs[it] } ?: 0
        val maxProb = softmaxProbs[maxProbIndex]
        
        // Проверяем уверенность в предсказании
        val isConfident = maxProb > config.confidenceThreshold
        
        // Логика определения заполненности:
        // - yes = заполнена чисто
        // - fixed = требует решения пользователя (пока считаем незаполненной)
        // - no = пустая
        val isFilled = when {
            isConfident && maxProbIndex == 1 -> true  // yes = заполнена
            else -> false  // no или fixed = не заполнена (для fixed будет спрашивать пользователя)
        }

        // Определяем класс и состояние
        val classNames = arrayOf("no", "yes", "fixed")
        val predictedClass = if (isConfident) classNames[maxProbIndex] else "no"
        val isFixed = isConfident && maxProbIndex == 2

        return PredictionResult(
            isFilled = isFilled,
            confidence = maxProb,
            probabilities = softmaxProbs,
            predictedClass = predictedClass,
            isFixed = isFixed
        )
    }
} 