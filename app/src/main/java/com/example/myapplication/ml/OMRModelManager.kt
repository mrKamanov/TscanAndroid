package com.example.myapplication.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.example.myapplication.ml.OnnxModel

/**
 * Менеджер для работы с OMR моделями
 * Поддерживает ONNX формат
 */
class OMRModelManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OMRModelManager"
        
        // Статический экземпляр для глобального доступа
        @Volatile
        private var globalInstance: OMRModelManager? = null
        
        /**
         * Устанавливает глобальный экземпляр
         */
        fun setGlobalInstance(instance: OMRModelManager) {
            globalInstance = instance
        }
        
        /**
         * Получает глобальный экземпляр
         */
        fun getGlobalInstance(): OMRModelManager? {
            return globalInstance
        }
        
        /**
         * Перезагружает глобальную модель
         */
        fun reloadGlobalModel() {
            globalInstance?.reloadModel()
        }
    }
    
    private val CONFIDENCE_THRESHOLD = 0.7f
    
    // Поддерживаемый формат модели
    enum class ModelFormat {
        ONNX
    }
    
    private var currentFormat: ModelFormat = ModelFormat.ONNX // Используем ONNX модель
    private var modelInterface: ModelInterface? = null
    private var modelConfig: ModelConfig? = null
    
    init {
        loadConfig()
        loadModel()
    }
    
    /**
     * Устанавливает формат модели для использования
     */
    fun setModelFormat(format: ModelFormat) {
        // У нас только ONNX формат, поэтому просто логируем
        Log.i(TAG, "ℹ️ Используется формат: $format")
    }
    
    /**
     * Перезагружает модель (например, при изменении настроек)
     */
    fun reloadModel() {
        Log.i(TAG, "🔄 Перезагрузка модели...")
        val activeModel = com.example.myapplication.utils.ModelSettingsHelper.getActiveModel(context)
        val inputSize = com.example.myapplication.utils.ModelSettingsHelper.getActiveModelInputSize(context)
        Log.i(TAG, "📊 Активная модель: $activeModel, размер входа: ${inputSize}×${inputSize}")
        
        // Перезагружаем конфигурацию для новой модели
        loadConfig()
        
        // Загружаем модель заново
        loadModel()
    }
    
    /**
     * Загружает ONNX модель
     */
    private fun loadModel() {
        try {
            // Освобождаем предыдущую модель
            modelInterface?.release()
            
            loadOnnxModel()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка загрузки ONNX модели: ${e.message}")
        }
    }
    
    /**
     * Загружает ONNX модель
     */
    private fun loadOnnxModel() {
        try {
            if (modelConfig == null) {
                Log.e(TAG, "❌ Конфигурация модели не загружена")
                return
            }
            
            // Получаем активную модель из настроек
            val activeModelPath = com.example.myapplication.utils.ModelSettingsHelper.getActiveModelPath(context)
            val activeModel = com.example.myapplication.utils.ModelSettingsHelper.getActiveModel(context)
            val inputSize = com.example.myapplication.utils.ModelSettingsHelper.getActiveModelInputSize(context)
            Log.i(TAG, "🔄 Загружаем модель: $activeModelPath (${activeModel}×${activeModel})")
            Log.i(TAG, "📊 Размер входа: ${inputSize}×${inputSize}")
            
            modelInterface = OnnxModel(context, activeModelPath, modelConfig!!)
            val success = modelInterface?.initialize() ?: false
            
            if (success) {
                Log.i(TAG, "✅ ONNX модель загружена успешно: ${activeModel}×${activeModel}")
            } else {
                Log.e(TAG, "❌ Не удалось инициализировать ONNX модель")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка загрузки ONNX модели: ${e.message}")
        }
    }
    

    
    /**
     * Загружает конфигурацию модели
     */
    private fun loadConfig() {
        try {
            Log.i(TAG, "🔄 Загружаем конфигурацию модели...")
            
            // Получаем активную модель для динамической конфигурации
            val activeModel = com.example.myapplication.utils.ModelSettingsHelper.getActiveModel(context)
            val inputSize = com.example.myapplication.utils.ModelSettingsHelper.getActiveModelInputSize(context)
            
            Log.i(TAG, "📊 Создаем конфигурацию для модели: ${activeModel}×${activeModel}")
            
            // Создаем динамическую конфигурацию на основе выбранной модели
            val config = createDynamicConfig(activeModel, inputSize)
            
            this.modelConfig = config
            Log.i(TAG, "✅ Динамическая конфигурация создана: ${config.modelName} (размер ${config.inputSize[0]}×${config.inputSize[1]})")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка загрузки конфигурации: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Парсит новый формат конфигурации
     */
    private fun parseNewConfig(json: JSONObject): ModelConfig {
        val modelInfo = json.getJSONObject("model_info")
        val modelConfig = json.getJSONObject("model_config")
        
        return ModelConfig(
            modelName = modelInfo.getString("name"),
            version = modelInfo.getString("version"),
            inputSize = intArrayOf(modelConfig.getInt("input_size"), modelConfig.getInt("input_size")),
            numClasses = modelConfig.getInt("num_classes"),
            classNames = modelConfig.getJSONArray("class_names").let { 
                Array(it.length()) { i -> it.getString(i) } 
            },
            mean = modelConfig.getJSONArray("mean").let { 
                floatArrayOf(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat()) 
            },
            std = modelConfig.getJSONArray("std").let { 
                floatArrayOf(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat()) 
            },
            confidenceThreshold = modelConfig.getDouble("confidence_threshold").toFloat()
        )
    }
    
    /**
     * Парсит старый формат конфигурации
     */
    private fun parseOldConfig(json: JSONObject): ModelConfig {
        return ModelConfig(
            modelName = json.getString("model_name"),
            version = json.getString("version"),
            inputSize = json.getJSONArray("input_size").let { 
                intArrayOf(it.getInt(0), it.getInt(1)) 
            },
            numClasses = json.getInt("num_classes"),
            classNames = json.getJSONArray("class_names").let { 
                Array(it.length()) { i -> it.getString(i) } 
            },
            mean = json.getJSONArray("mean").let { 
                floatArrayOf(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat()) 
            },
            std = json.getJSONArray("std").let { 
                floatArrayOf(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat()) 
            },
            confidenceThreshold = json.getDouble("confidence_threshold").toFloat()
        )
    }
    
    /**
     * Предсказывает, заполнена ли ячейка
     * @param cellBitmap изображение ячейки
     * @return результат предсказания
     */
    fun predictCell(cellBitmap: Bitmap): PredictionResult {
        if (modelInterface == null || !modelInterface!!.isReady()) {
            Log.e(TAG, "❌ Модель не загружена или не готова")
            return PredictionResult(isFilled = false, confidence = 0f, probabilities = floatArrayOf(0f, 0f, 0f), predictedClass = "no", isFixed = false)
        }
        
        return modelInterface!!.predict(cellBitmap)
    }
    
    /**
     * Батч-предсказание для множества ячеек
     * @param cellBitmaps список изображений ячеек
     * @return список результатов предсказания
     */
    fun predictCellsBatch(cellBitmaps: List<Bitmap>): List<PredictionResult> {
        if (modelInterface == null || !modelInterface!!.isReady()) {
            Log.e(TAG, "❌ Модель не загружена или не готова")
            return List(cellBitmaps.size) { 
                PredictionResult(isFilled = false, confidence = 0f, probabilities = floatArrayOf(0f, 0f, 0f), predictedClass = "no", isFixed = false) 
            }
        }
        
        return modelInterface!!.predictBatch(cellBitmaps)
    }
    

    
    /**
     * Проверяет, готова ли модель к использованию
     */
    fun isModelReady(): Boolean {
        return modelInterface?.isReady() == true && modelConfig != null
    }
    
    /**
     * Получает информацию о модели
     */
    fun getModelInfo(): String {
        return modelConfig?.let { config ->
            val activeModel = com.example.myapplication.utils.ModelSettingsHelper.getActiveModelDisplayName(context)
            """
            Модель: ${config.modelName}
            Версия: ${config.version}
            Формат: $currentFormat
            Размер входа: ${config.inputSize[0]}x${config.inputSize[1]}
            Классы: ${config.classNames.joinToString(", ")}
            Порог уверенности: ${config.confidenceThreshold * 100}%
            Активная модель: $activeModel
            Статус: ${if (isModelReady()) "Готова" else "Не готова"}
            """.trimIndent()
        } ?: "Модель не загружена"
    }
    
    /**
     * Получает текущий формат модели
     */
    fun getCurrentFormat(): ModelFormat {
        return currentFormat
    }
    
    /**
     * Получает список доступных форматов
     */
    fun getAvailableFormats(): List<ModelFormat> {
        return listOf(ModelFormat.ONNX)
    }
    
    /**
     * Освобождает ресурсы модели
     */
    fun release() {
        modelInterface?.release()
        modelInterface = null
        modelConfig = null
        Log.i(TAG, "🔧 Ресурсы модели освобождены")
    }
    
    /**
     * Создает динамическую конфигурацию для выбранной модели
     */
    private fun createDynamicConfig(activeModel: String, inputSize: Int): ModelConfig {
        val modelName = when (activeModel) {
            "64" -> "OMR_64x64_Model"
            "128" -> "OMR_128x128_Model"
            "256" -> "OMR_256x256_Model"
            else -> "OMR_128x128_Model"
        }
        
        val version = when (activeModel) {
            "64" -> "1.0"
            "128" -> "2.0"
            "256" -> "3.0"
            else -> "2.0"
        }
        
        return ModelConfig(
            modelName = modelName,
            version = version,
            inputSize = intArrayOf(inputSize, inputSize),
            numClasses = 3,
            classNames = arrayOf("no", "yes", "fixed"),
            mean = floatArrayOf(0.485f, 0.456f, 0.406f),
            std = floatArrayOf(0.229f, 0.224f, 0.225f),
            confidenceThreshold = CONFIDENCE_THRESHOLD
        )
    }
}

/**
 * Конфигурация модели
 */
data class ModelConfig(
    val modelName: String,
    val version: String,
    val inputSize: IntArray,
    val numClasses: Int,
    val classNames: Array<String>,
    val mean: FloatArray,
    val std: FloatArray,
    val confidenceThreshold: Float
)

 