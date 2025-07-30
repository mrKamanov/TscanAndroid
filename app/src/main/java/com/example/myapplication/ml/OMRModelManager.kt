package com.example.myapplication.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.json.JSONObject
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Менеджер для работы с OMR моделями в различных форматах
 * Поддерживает PyTorch, ONNX и TensorFlow Lite
 */
class OMRModelManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OMRModelManager"
        
        // Пути к моделям
        private const val PYTORCH_MODEL = "omr_model.pt"
        private const val ONNX_MODEL = "models/omr_model_optimized.onnx"
        private const val TFLITE_MODEL = "models/omr_model.tflite"
        
        // Конфигурационные файлы
        private const val OLD_CONFIG_FILE = "model_config.json"
        private const val NEW_CONFIG_FILE = "models/model_metadata.json"
        
        private const val INPUT_SIZE = 224
        private const val CONFIDENCE_THRESHOLD = 0.8f
    }
    
    // Поддерживаемые форматы моделей
    enum class ModelFormat {
        PYTORCH,
        ONNX,
        TFLITE
    }
    
    private var currentFormat: ModelFormat = ModelFormat.ONNX // По умолчанию используем ONNX (оптимизированную модель)
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
        currentFormat = format
        loadModel()
        Log.i(TAG, "🔄 Переключение на формат: $format")
    }
    
    /**
     * Загружает модель в выбранном формате
     */
    private fun loadModel() {
        try {
            // Освобождаем предыдущую модель
            modelInterface?.release()
            
            when (currentFormat) {
                ModelFormat.PYTORCH -> loadPyTorchModel()
                ModelFormat.ONNX -> loadOnnxModel()
                ModelFormat.TFLITE -> loadTfLiteModel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка загрузки модели формата $currentFormat: ${e.message}")
            // Fallback на PyTorch если доступен
            if (currentFormat != ModelFormat.PYTORCH) {
                Log.i(TAG, "🔄 Попытка fallback на PyTorch модель")
                currentFormat = ModelFormat.PYTORCH
                loadPyTorchModel()
            }
        }
    }
    
    /**
     * Загружает PyTorch модель
     */
    private fun loadPyTorchModel() {
        try {
            Log.i(TAG, "🔄 Начинаем загрузку PyTorch модели...")
            
            if (modelConfig == null) {
                Log.e(TAG, "❌ Конфигурация модели не загружена")
                return
            }
            
            Log.i(TAG, "📁 Путь к модели: $PYTORCH_MODEL")
            Log.i(TAG, "⚙️ Конфигурация: ${modelConfig!!.modelName} v${modelConfig!!.version}")
            
            modelInterface = PyTorchModel(context, PYTORCH_MODEL, modelConfig!!)
            val success = modelInterface?.initialize() ?: false
            
            if (success) {
                Log.i(TAG, "✅ PyTorch модель загружена успешно")
            } else {
                Log.e(TAG, "❌ Не удалось инициализировать PyTorch модель")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка загрузки PyTorch модели: ${e.message}")
            e.printStackTrace()
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
            
            modelInterface = OnnxModel(context, ONNX_MODEL, modelConfig!!)
            val success = modelInterface?.initialize() ?: false
            
            if (success) {
                Log.i(TAG, "✅ ONNX модель загружена успешно")
            } else {
                Log.e(TAG, "❌ Не удалось инициализировать ONNX модель")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка загрузки ONNX модели: ${e.message}")
        }
    }
    
    /**
     * Загружает TensorFlow Lite модель
     */
    private fun loadTfLiteModel() {
        try {
            if (modelConfig == null) {
                Log.e(TAG, "❌ Конфигурация модели не загружена")
                return
            }
            
            modelInterface = TfLiteModel(context, TFLITE_MODEL, modelConfig!!)
            val success = modelInterface?.initialize() ?: false
            
            if (success) {
                Log.i(TAG, "✅ TensorFlow Lite модель загружена успешно")
            } else {
                Log.e(TAG, "❌ Не удалось инициализировать TFLite модель")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка загрузки TFLite модели: ${e.message}")
        }
    }
    
    /**
     * Загружает конфигурацию модели
     */
    private fun loadConfig() {
        try {
            Log.i(TAG, "🔄 Загружаем конфигурацию модели...")
            
            // Сначала пробуем новый конфигурационный файл
            val configString = try {
                Log.i(TAG, "📁 Пробуем новый конфиг: $NEW_CONFIG_FILE")
                context.assets.open(NEW_CONFIG_FILE).bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                // Fallback на старый файл
                Log.i(TAG, "📁 Fallback на старый конфиг: $OLD_CONFIG_FILE")
                context.assets.open(OLD_CONFIG_FILE).bufferedReader().use { it.readText() }
            }
            
            Log.i(TAG, "📄 Конфигурация прочитана, парсим JSON...")
            val json = JSONObject(configString)
            
            // Парсим новый формат конфигурации
            val modelInfo = json.optJSONObject("model_info")
            val input = json.optJSONObject("input")
            val output = json.optJSONObject("output")
            val preprocessing = json.optJSONObject("preprocessing")
            val modelConfig = json.optJSONObject("model_config")
            
            // Используем новый формат если доступен, иначе старый
            val config = try {
                if (modelConfig != null) {
                    Log.i(TAG, "🆕 Используем новый формат конфигурации")
                    parseNewConfig(json)
                } else {
                    Log.i(TAG, "📋 Используем старый формат конфигурации")
                    parseOldConfig(json)
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Ошибка парсинга нового конфига, fallback на старый: ${e.message}")
                parseOldConfig(json)
            }
            
            this.modelConfig = config
            Log.i(TAG, "✅ Конфигурация загружена: ${config.modelName} (версия ${config.version})")
            
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
            return PredictionResult(isFilled = false, confidence = 0f, probabilities = floatArrayOf(0f, 0f))
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
                PredictionResult(isFilled = false, confidence = 0f, probabilities = floatArrayOf(0f, 0f)) 
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
            """
            Модель: ${config.modelName}
            Версия: ${config.version}
            Формат: $currentFormat
            Размер входа: ${config.inputSize[0]}x${config.inputSize[1]}
            Классы: ${config.classNames.joinToString(", ")}
            Порог уверенности: ${config.confidenceThreshold * 100}%
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
        return ModelFormat.values().toList()
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

 