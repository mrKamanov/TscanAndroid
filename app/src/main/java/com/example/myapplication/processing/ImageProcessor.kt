package com.example.myapplication.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.core.Core
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import java.io.ByteArrayOutputStream
import com.example.myapplication.models.OMRResult
import com.example.myapplication.ml.OMRModelManager
import com.example.myapplication.ml.PredictionResult

/**
 * Класс для обработки изображений с использованием OpenCV
 * Содержит методы конвертации, поворота и обработки кадров
 */
class ImageProcessor {
    
    companion object {
        private const val TAG = "ImageProcessor"
    }
    
    // ML модель для обработки ячеек
    private var omrModelManager: OMRModelManager? = null
    
    /**
     * Устанавливает ML модель
     */
    fun setMLModel(modelManager: OMRModelManager) {
        this.omrModelManager = modelManager
    }
    
    /**
     * Проверяет, готова ли ML модель
     */
    fun isMLModelReady(): Boolean {
        return omrModelManager?.isModelReady() == true
    }
    
    /**
     * Конвертирует ImageProxy в Bitmap
     */
    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    /**
     * Поворачивает Bitmap на указанный угол
     */
    fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return src
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /**
     * Основная обработка кадра с помощью OpenCV (только визуализация)
     * Включает поиск контура бланка, перспективное преобразование
     */
    fun processFrameWithOpenCV(
        inputBitmap: Bitmap, 
        targetWidth: Int, 
        targetHeight: Int,
        questionsCount: Int = 5,
        choicesCount: Int = 5,
        correctAnswers: List<Int> = emptyList(),
        isProcessingEnabled: Boolean = true,
        isGridVisible: Boolean = false,
        overlayMode: Boolean = false,
        brightness: Int = 0, // -100..+100
        contrast: Int = 100, // 0..200
        saturation: Int = 100, // 0..200
        sharpness: Int = 50 // 0..100
    ): Pair<Bitmap, Boolean> {
        var omrResult: OMRResult? = null
        val inputMat = Mat()
        Utils.bitmapToMat(inputBitmap, inputMat)
        // === Применяем настройки ===
        // Яркость и контраст
        val alpha = contrast / 100.0 // 1.0 — без изменений
        val beta = brightness.toDouble() // 0 — без изменений
        inputMat.convertTo(inputMat, -1, alpha, beta)
        // Насыщенность
        if (saturation != 100) {
            val hsv = Mat()
            Imgproc.cvtColor(inputMat, hsv, Imgproc.COLOR_BGR2HSV)
            val channels = ArrayList<Mat>(3)
            org.opencv.core.Core.split(hsv, channels)
            channels[1].convertTo(channels[1], -1, saturation / 100.0, 0.0)
            org.opencv.core.Core.merge(channels, hsv)
            Imgproc.cvtColor(hsv, inputMat, Imgproc.COLOR_HSV2BGR)
            channels.forEach { it.release() }
            hsv.release()
        }
        // Резкость
        if (sharpness != 50) {
            val kernelSize = 3
            val kernel = Mat(kernelSize, kernelSize, org.opencv.core.CvType.CV_32F)
            val k = (sharpness - 50) / 50.0 // -1..+1
            val base = 1.0 + k * 2.0 // 1..3 (усиление), 1..-1 (размытие)
            val arr = floatArrayOf(
                0f, -1f, 0f,
                -1f, base.toFloat(), -1f,
                0f, -1f, 0f
            )
            kernel.put(0, 0, arr)
            Imgproc.filter2D(inputMat, inputMat, inputMat.depth(), kernel)
            kernel.release()
        }
        
        // Получаем размеры входного изображения
        val inputWidth = inputMat.cols()
        val inputHeight = inputMat.rows()
        
        // 1. Поиск контура бланка (наибольший прямоугольник)
        val gray = Mat()
        Imgproc.cvtColor(inputMat, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        val edged = Mat()
        Imgproc.Canny(gray, edged, 75.0, 200.0)
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edged, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        var maxArea = 0.0
        var pageContour: MatOfPoint? = null
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > 1000) {
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true), true)
                if (approx.total() == 4L && area > maxArea) {
                    maxArea = area
                    pageContour = MatOfPoint(*approx.toArray())
                }
            }
        }
            
            if (pageContour != null) {
            val pts = pageContour.toArray()
            val sortedPts = sortPoints(pts)
            
            // Используем размеры входного изображения для warp
            val dstSize = minOf(inputWidth, inputHeight)
            val srcMat = MatOfPoint2f(*sortedPts)
            val dstMat = MatOfPoint2f(
                org.opencv.core.Point(0.0, 0.0),
                org.opencv.core.Point(dstSize - 1.0, 0.0),
                org.opencv.core.Point(dstSize - 1.0, dstSize - 1.0),
                org.opencv.core.Point(0.0, dstSize - 1.0)
            )
            val perspectiveTransform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
            val warp = Mat()
            Imgproc.warpPerspective(inputMat, warp, perspectiveTransform, Size(dstSize.toDouble(), dstSize.toDouble()))

            // Проверяем бланк если есть правильные ответы
            if (correctAnswers.isNotEmpty() && correctAnswers.size == questionsCount && isProcessingEnabled) {
                omrResult = processTestSheet(warp, questionsCount, choicesCount, correctAnswers)
                if (isGridVisible) {
                    drawGridOnWarp(warp, questionsCount, choicesCount)
                }
                
                if (overlayMode) {
                    // В режиме overlay показываем исходный кадр с выделенным контуром бланка
                    val contourMat = inputMat.clone()
                    Imgproc.drawContours(contourMat, listOf(pageContour), 0, org.opencv.core.Scalar(0.0, 255.0, 0.0), 3)
                    contourMat.copyTo(inputMat)
                    contourMat.release()
                } else {
                    // Показываем обработанный warp-бланк
                    val resizedWarp = Mat()
                    Imgproc.resize(warp, resizedWarp, Size(inputWidth.toDouble(), inputHeight.toDouble()))
                    resizedWarp.copyTo(inputMat)
                    resizedWarp.release()
                }
            } else {
                // Рисуем сетку на warp-бланке если включена
                if (isGridVisible) {
                    drawGridOnWarp(warp, questionsCount, choicesCount)
                }

                if (overlayMode) {
                    // В режиме overlay показываем исходный кадр с выделенным контуром бланка
                    val contourMat = inputMat.clone()
                    Imgproc.drawContours(contourMat, listOf(pageContour), 0, org.opencv.core.Scalar(0.0, 255.0, 0.0), 3)
                    contourMat.copyTo(inputMat)
                    contourMat.release()
                } else {
                    // Показываем только warp-бланк, но масштабируем до размеров входного изображения
                    val resizedWarp = Mat()
                    Imgproc.resize(warp, resizedWarp, Size(inputWidth.toDouble(), inputHeight.toDouble()))
                    resizedWarp.copyTo(inputMat)
                    resizedWarp.release()
                }
            }
            warp.release()
            srcMat.release()
            dstMat.release()
            perspectiveTransform.release()
        }
        
        gray.release()
        edged.release()
        hierarchy.release()
        
        // Масштабируем результат под размеры UI
                val finalMat = Mat()
                Imgproc.resize(inputMat, finalMat, Size(targetWidth.toDouble(), targetHeight.toDouble()))
                
        // Конвертируем обратно в Bitmap с целевыми размерами
                val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(finalMat, outputBitmap)
        inputMat.release()
        finalMat.release()
        val contourFound = pageContour != null
        return Pair(outputBitmap, contourFound)
    }
    
    /**
     * Обработка уже обработанного warp-бланка с ML (без поиска контуров)
     * Используется когда контур уже найден и обработан OpenCV
     */
    fun processWarpedFrameWithML(
        warpedBitmap: Bitmap,
        questionsCount: Int,
        choicesCount: Int,
        correctAnswers: List<Int>,
        onProgressUpdate: ((Int, Int, Boolean) -> Unit)? = null
    ): OMRResult? {
        try {
            Log.d(TAG, "🔍 Начинаем ML обработку warp-бланка: ${warpedBitmap.width}x${warpedBitmap.height}")
            Log.d(TAG, "🤖 ML модель готова: ${isMLModelReady()}")
            
            // Конвертируем Bitmap в Mat
            val warpMat = Mat()
            Utils.bitmapToMat(warpedBitmap, warpMat)
            
            // Обрабатываем уже готовый warp-бланк с ML
            val result = processTestSheetWithPriority(warpMat, questionsCount, choicesCount, correctAnswers, onProgressUpdate)
            
            // Освобождаем ресурсы
            warpMat.release()
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка ML обработки warp-бланка: ${e.message}", e)
            return null
        }
    }

    /**
     * Обработка кадра с ML (асинхронная) - приоритетная обработка эталонных ячеек
     */
    fun processFrameWithML(
        inputBitmap: Bitmap,
        questionsCount: Int,
        choicesCount: Int,
        correctAnswers: List<Int>,
        onProgressUpdate: ((Int, Int, Boolean) -> Unit)? = null
    ): OMRResult? {
        try {
            Log.d(TAG, "🔍 Начинаем ML обработку кадра: ${inputBitmap.width}x${inputBitmap.height}")
            Log.d(TAG, "🤖 ML модель готова: ${isMLModelReady()}")
            
            val inputMat = Mat()
            Utils.bitmapToMat(inputBitmap, inputMat)
            
            // Поиск контура бланка
            val gray = Mat()
            Imgproc.cvtColor(inputMat, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            val edged = Mat()
            Imgproc.Canny(gray, edged, 75.0, 200.0)
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(edged, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            Log.d(TAG, "📊 Найдено контуров: ${contours.size}")
            
            var maxArea = 0.0
            var pageContour: MatOfPoint? = null
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area > 1000) {
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true), true)
                    if (approx.total() == 4L && area > maxArea) {
                        maxArea = area
                        pageContour = MatOfPoint(*approx.toArray())
                        Log.d(TAG, "✅ Найден прямоугольный контур площадью: $area")
                    }
                }
            }
            
            if (pageContour != null) {
                Log.d(TAG, "🎯 Контур бланка найден, обрабатываем...")
                val pts = pageContour.toArray()
                val sortedPts = sortPoints(pts)
                
                val inputWidth = inputMat.cols()
                val inputHeight = inputMat.rows()
                val dstSize = minOf(inputWidth, inputHeight)
                val srcMat = MatOfPoint2f(*sortedPts)
                val dstMat = MatOfPoint2f(
                    org.opencv.core.Point(0.0, 0.0),
                    org.opencv.core.Point(dstSize - 1.0, 0.0),
                    org.opencv.core.Point(dstSize - 1.0, dstSize - 1.0),
                    org.opencv.core.Point(0.0, dstSize - 1.0)
                )
                val perspectiveTransform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
                val warp = Mat()
                Imgproc.warpPerspective(inputMat, warp, perspectiveTransform, Size(dstSize.toDouble(), dstSize.toDouble()))
                
                // Обрабатываем бланк с ML с приоритетной обработкой
                val result = processTestSheetWithPriority(warp, questionsCount, choicesCount, correctAnswers, onProgressUpdate)
                
                // Освобождаем ресурсы
                warp.release()
                srcMat.release()
                dstMat.release()
                perspectiveTransform.release()
                
                return result
            } else {
                Log.w(TAG, "⚠️ Контур бланка не найден! Найдено контуров: ${contours.size}, максимальная площадь: $maxArea")
                
                // Попробуем обработать весь кадр как бланк (fallback)
                Log.d(TAG, "🔄 Пробуем обработать весь кадр как бланк...")
                val result = processTestSheet(inputMat, questionsCount, choicesCount, correctAnswers)
                
                // Освобождаем ресурсы
                gray.release()
                edged.release()
                hierarchy.release()
                inputMat.release()
                
                return result
            }
            
            // Освобождаем ресурсы
            gray.release()
            edged.release()
            hierarchy.release()
            inputMat.release()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка ML обработки кадра: ${e.message}", e)
        }
        
        Log.d(TAG, "❌ ML обработка завершена без результата")
        return null
    }
    
    /**
     * Обработка тестового бланка с OMR
     */
    private fun processTestSheet(warpMat: Mat, questionsCount: Int, choicesCount: Int, correctAnswers: List<Int>): OMRResult {
        try {
            Log.d(TAG, "📋 Обрабатываем тестовый бланк: ${warpMat.cols()}x${warpMat.rows()}, вопросы: $questionsCount, варианты: $choicesCount")
            
            // Конвертируем Mat в Bitmap для ML обработки
            val warpBitmap = Bitmap.createBitmap(warpMat.cols(), warpMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warpMat, warpBitmap)
            
            // Разделяем изображение на ячейки
            val cellWidth = warpMat.cols() / choicesCount
            val cellHeight = warpMat.rows() / questionsCount
            
            // Массив для хранения результатов ML для каждой ячейки
            val mlResults = Array(questionsCount) { Array(choicesCount) { false } }
            
            // Собираем все ячейки для батч-обработки
            val cellBitmaps = mutableListOf<Bitmap>()
            val cellPositions = mutableListOf<Pair<Int, Int>>()
            
            for (question in 0 until questionsCount) {
                for (choice in 0 until choicesCount) {
                    val x = choice * cellWidth
                    val y = question * cellHeight
                    
                    // Извлекаем ячейку как Bitmap
                    val cellBitmap = Bitmap.createBitmap(warpBitmap, x, y, cellWidth, cellHeight)
                    cellBitmaps.add(cellBitmap)
                    cellPositions.add(Pair(question, choice))
                }
            }
            
            // Батч-обработка всех ячеек
            if (isMLModelReady()) {
                try {
                    Log.d(TAG, "🚀 Батч-обработка ${cellBitmaps.size} ячеек...")
                    val startTime = System.currentTimeMillis()
                    
                    // Обрабатываем все ячейки одним батчем
                    val predictions = omrModelManager!!.predictCellsBatch(cellBitmaps)
                    
                    val processingTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "⚡ Батч-обработка завершена за ${processingTime}мс")
                    
                    // Заполняем результаты
                    for (i in cellBitmaps.indices) {
                        val (question, choice) = cellPositions[i]
                        val prediction = predictions[i]
                        mlResults[question][choice] = prediction.isFilled
                        Log.d(TAG, "🎯 ML: Ячейка [$question][$choice] = ${prediction.getDescription()}")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Ошибка батч ML предсказания: ${e.message}")
                    // Fallback к обработке по одной ячейке
                    for (i in cellBitmaps.indices) {
                        val (question, choice) = cellPositions[i]
                        val cellBitmap = cellBitmaps[i]
                        
                        try {
                            val prediction = omrModelManager!!.predictCell(cellBitmap)
                            mlResults[question][choice] = prediction.isFilled
                            Log.d(TAG, "🎯 ML (fallback): Ячейка [$question][$choice] = ${prediction.getDescription()}")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Ошибка ML предсказания для ячейки [$question][$choice]: ${e.message}")
                            mlResults[question][choice] = false
                        }
                    }
                }
            } else {
                // Fallback к старой логике, если ML модель не готова
                Log.d(TAG, "🔄 Используем fallback логику (OpenCV)")
                for (i in cellBitmaps.indices) {
                    val (question, choice) = cellPositions[i]
                    val cellBitmap = cellBitmaps[i]
                    
                    val cellMat = Mat()
                    Utils.bitmapToMat(cellBitmap, cellMat)
                    val grayCell = Mat()
                    Imgproc.cvtColor(cellMat, grayCell, Imgproc.COLOR_BGR2GRAY)
                    val threshCell = Mat()
                    Imgproc.threshold(grayCell, threshCell, 170.0, 255.0, Imgproc.THRESH_BINARY_INV)
                    val pixelCount = Core.countNonZero(threshCell)
                    mlResults[question][choice] = pixelCount > (cellWidth * cellHeight * 0.1) // 10% заполненности
                    
                    // Освобождаем ресурсы
                    cellMat.release()
                    grayCell.release()
                    threshCell.release()
                }
            }
            
            // Освобождаем ресурсы
            cellBitmaps.forEach { it.recycle() }
            
            // Освобождаем ресурсы
            warpBitmap.recycle()
            
            // Определяем выбранные ответы (заполненные ячейки в строке)
            val selectedAnswers = IntArray(questionsCount)
            for (question in 0 until questionsCount) {
                var selectedChoice = 0
                for (choice in 0 until choicesCount) {
                    if (mlResults[question][choice]) {
                        selectedChoice = choice
                        break
                    }
                }
                selectedAnswers[question] = selectedChoice
            }
            
            // Проверяем правильность ответов
            val grading = IntArray(questionsCount)
            val incorrectQuestions = mutableListOf<Map<String, Any>>()
            
            for (question in 0 until questionsCount) {
                if (question < correctAnswers.size) {
                    grading[question] = if (selectedAnswers[question] == correctAnswers[question]) 1 else 0
                    
                    if (grading[question] == 0) {
                        incorrectQuestions.add(mapOf(
                            "question_number" to (question + 1),
                            "selected_answer" to (selectedAnswers[question] + 1),
                            "correct_answer" to (correctAnswers[question] + 1)
                        ))
                    }
                }
            }
            
            val result = OMRResult(
                selectedAnswers = selectedAnswers,
                grading = grading,
                incorrectQuestions = incorrectQuestions,
                correctAnswers = correctAnswers
            )
            
            val correctCount = grading.sum()
            Log.d(TAG, "✅ Обработка завершена: $correctCount/$questionsCount правильных ответов")
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки тестового бланка: ${e.message}", e)
            return OMRResult(
                selectedAnswers = IntArray(questionsCount) { 0 },
                grading = IntArray(questionsCount) { 0 },
                incorrectQuestions = emptyList(),
                correctAnswers = correctAnswers
            )
        }
    }
    
    /**
     * Обработка тестового бланка с приоритетной обработкой эталонных ячеек
     * НОВАЯ ЛОГИКА: Сначала эталонные ячейки → мгновенный результат → фоновая проверка ошибок
     */
    private fun processTestSheetWithPriority(
        warpMat: Mat, 
        questionsCount: Int, 
        choicesCount: Int, 
        correctAnswers: List<Int>,
        onProgressUpdate: ((Int, Int, Boolean) -> Unit)?
    ): OMRResult {
        try {
            Log.d(TAG, "📋 Приоритетная обработка тестового бланка: ${warpMat.cols()}x${warpMat.rows()}")
            
            // Конвертируем Mat в Bitmap для ML обработки
            val warpBitmap = Bitmap.createBitmap(warpMat.cols(), warpMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warpMat, warpBitmap)
            
            // Разделяем изображение на ячейки
            val cellWidth = warpMat.cols() / choicesCount
            val cellHeight = warpMat.rows() / questionsCount
            
            // Массив для хранения результатов ML для каждой ячейки
            val mlResults = Array(questionsCount) { Array(choicesCount) { false } }
            
            // ===== ЭТАП 1: ОБРАБОТКА ТОЛЬКО ЭТАЛОННЫХ ЯЧЕЕК =====
            Log.d(TAG, "🚀 ЭТАП 1: Обрабатываем только эталонные ячейки...")
            
            val referenceCells = mutableListOf<Triple<Int, Int, Bitmap>>()
            
            // Собираем только эталонные ячейки (правильные ответы)
            for (question in 0 until questionsCount) {
                if (question < correctAnswers.size) {
                    val correctChoice = correctAnswers[question]
                    val x = correctChoice * cellWidth
                    val y = question * cellHeight
                    
                    // Извлекаем эталонную ячейку как Bitmap
                    val cellBitmap = Bitmap.createBitmap(warpBitmap, x, y, cellWidth, cellHeight)
                    referenceCells.add(Triple(question, correctChoice, cellBitmap))
                    
                    Log.d(TAG, "🎯 Эталонная ячейка [$question][$correctChoice] добавлена в приоритетный батч")
                }
            }
            
            // Обрабатываем эталонные ячейки
            if (isMLModelReady() && referenceCells.isNotEmpty()) {
                try {
                    val startTime = System.currentTimeMillis()
                    
                    // Подготавливаем батч эталонных ячеек
                    val referenceBitmaps = referenceCells.map { it.third }
                    
                    // Обрабатываем эталонные ячейки одним батчем
                    val referencePredictions = omrModelManager!!.predictCellsBatch(referenceBitmaps)
                    
                    val referenceTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "⚡ Эталонные ячейки обработаны за ${referenceTime}мс")
                    
                    // Заполняем результаты эталонных ячеек и сразу отрисовываем
                    for (i in referenceCells.indices) {
                        val (question, choice, _) = referenceCells[i]
                        val prediction = referencePredictions[i]
                        
                        mlResults[question][choice] = prediction.isFilled
                        
                        // СРАЗУ отрисовываем результат для эталонной ячейки
                        onProgressUpdate?.invoke(question, choice, prediction.isFilled)
                        
                        Log.d(TAG, "✅ Эталонная ячейка [$question][$choice] = ${prediction.getDescription()}")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Ошибка обработки эталонных ячеек: ${e.message}")
                }
            }
            
            // ===== ЭТАП 2: ПРОВЕРЯЕМ РЕЗУЛЬТАТ ЭТАЛОННЫХ ЯЧЕЕК =====
            Log.d(TAG, "🔍 ЭТАП 2: Проверяем результат эталонных ячеек...")
            
            val selectedAnswers = IntArray(questionsCount) { -1 } // -1 = не обработано
            val grading = IntArray(questionsCount) { -1 } // -1 = не обработано
            val incorrectQuestions = mutableListOf<Int>() // Номера вопросов с ошибками
            
            // Заполняем результаты только для эталонных ячеек
            for (question in 0 until questionsCount) {
                if (question < correctAnswers.size) {
                    val correctChoice = correctAnswers[question]
                    selectedAnswers[question] = correctChoice // Предполагаем правильный ответ
                    grading[question] = if (mlResults[question][correctChoice]) 1 else 0
                    
                    if (grading[question] == 0) {
                        incorrectQuestions.add(question)
                        Log.d(TAG, "❌ Вопрос $question неправильный - нужна фоновая проверка")
                    } else {
                        Log.d(TAG, "✅ Вопрос $question правильный")
                    }
                }
            }
            
            // ===== ЭТАП 3: ФОНОВАЯ ПРОВЕРКА ТОЛЬКО ОШИБОЧНЫХ ВОПРОСОВ =====
            if (incorrectQuestions.isNotEmpty()) {
                Log.d(TAG, "🔄 ЭТАП 3: Фоновая проверка ${incorrectQuestions.size} вопросов с ошибками...")
                
                // Собираем все ячейки для вопросов с ошибками
                val errorCells = mutableListOf<Triple<Int, Int, Bitmap>>()
                
                for (question in incorrectQuestions) {
                    for (choice in 0 until choicesCount) {
                        val x = choice * cellWidth
                        val y = question * cellHeight
                        
                        // Извлекаем ячейку как Bitmap
                        val cellBitmap = Bitmap.createBitmap(warpBitmap, x, y, cellWidth, cellHeight)
                        errorCells.add(Triple(question, choice, cellBitmap))
                    }
                }
                
                // Обрабатываем ячейки с ошибками
                if (isMLModelReady() && errorCells.isNotEmpty()) {
                    try {
                        val errorStartTime = System.currentTimeMillis()
                        
                        // Подготавливаем батч ячеек с ошибками
                        val errorBitmaps = errorCells.map { it.third }
                        
                        // Обрабатываем ячейки с ошибками батчем
                        val errorPredictions = omrModelManager!!.predictCellsBatch(errorBitmaps)
                        
                        val errorTime = System.currentTimeMillis() - errorStartTime
                        Log.d(TAG, "⚡ Ячейки с ошибками обработаны за ${errorTime}мс")
                        
                        // Обновляем результаты для ячеек с ошибками
                        for (i in errorCells.indices) {
                            val (question, choice, _) = errorCells[i]
                            val prediction = errorPredictions[i]
                            
                            mlResults[question][choice] = prediction.isFilled
                            
                            // Отрисовываем результат для ячеек с ошибками
                            onProgressUpdate?.invoke(question, choice, prediction.isFilled)
                            
                            Log.d(TAG, "🔍 Ошибочная ячейка [$question][$choice] = ${prediction.getDescription()}")
                        }
                        
                        // Пересчитываем правильные ответы для вопросов с ошибками
                        for (question in incorrectQuestions) {
                            var selectedChoice = -1
                            for (choice in 0 until choicesCount) {
                                if (mlResults[question][choice]) {
                                    selectedChoice = choice
                                    break
                                }
                            }
                            
                            if (selectedChoice != -1) {
                                selectedAnswers[question] = selectedChoice
                                grading[question] = if (selectedChoice == correctAnswers[question]) 1 else 0
                                
                                Log.d(TAG, "🔄 Вопрос $question: выбран ответ $selectedChoice, правильный ${correctAnswers[question]}")
                            }
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Ошибка фоновой проверки: ${e.message}")
                    }
                }
                
                // Освобождаем ресурсы ячеек с ошибками
                errorCells.forEach { it.third.recycle() }
            }
            
            // ===== ФИНАЛЬНАЯ ОБРАБОТКА =====
            // Заполняем непроверенные вопросы значением по умолчанию
            for (question in 0 until questionsCount) {
                if (selectedAnswers[question] == -1) {
                    selectedAnswers[question] = 0
                    grading[question] = 0
                }
            }
            
            // Создаем финальный список неправильных вопросов
            val finalIncorrectQuestions = mutableListOf<Map<String, Any>>()
            for (question in 0 until questionsCount) {
                if (question < correctAnswers.size && grading[question] == 0) {
                    finalIncorrectQuestions.add(mapOf(
                        "question_number" to (question + 1),
                        "selected_answer" to (selectedAnswers[question] + 1),
                        "correct_answer" to (correctAnswers[question] + 1)
                    ))
                }
            }
            
            val result = OMRResult(
                selectedAnswers = selectedAnswers,
                grading = grading,
                incorrectQuestions = finalIncorrectQuestions,
                correctAnswers = correctAnswers
            )
            
            val correctCount = grading.count { it == 1 }
            Log.d(TAG, "✅ Приоритетная обработка завершена: $correctCount/$questionsCount правильных ответов")
            Log.d(TAG, "📊 Эталонных ячеек: ${referenceCells.size}, вопросов с ошибками: ${incorrectQuestions.size}")
            
            // Освобождаем ресурсы
            referenceCells.forEach { it.third.recycle() }
            warpBitmap.recycle()
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка приоритетной обработки тестового бланка: ${e.message}", e)
            return OMRResult(
                selectedAnswers = IntArray(questionsCount) { 0 },
                grading = IntArray(questionsCount) { 0 },
                incorrectQuestions = emptyList(),
                correctAnswers = correctAnswers
            )
        }
    }
    
    /**
     * Рисование сетки на изображении
     */
    private fun drawGridOnWarp(warpMat: Mat, questionsCount: Int, choicesCount: Int) {
        val width = warpMat.cols()
        val height = warpMat.rows()
        val cellWidth = width / choicesCount
        val cellHeight = height / questionsCount
        
        // Рисуем горизонтальные линии
        for (i in 0..questionsCount) {
            val y = (i * cellHeight).toInt()
            Imgproc.line(warpMat, 
                org.opencv.core.Point(0.0, y.toDouble()), 
                org.opencv.core.Point(width.toDouble(), y.toDouble()), 
                org.opencv.core.Scalar(255.0, 79.0, 0.0), 2)
        }
        
        // Рисуем вертикальные линии
        for (i in 0..choicesCount) {
            val x = (i * cellWidth).toInt()
            Imgproc.line(warpMat, 
                org.opencv.core.Point(x.toDouble(), 0.0), 
                org.opencv.core.Point(x.toDouble(), height.toDouble()), 
                org.opencv.core.Scalar(255.0, 79.0, 0.0), 2)
        }
    }

    /**
     * Сортировка точек контура: [top-left, top-right, bottom-right, bottom-left]
     */
    private fun sortPoints(pts: Array<org.opencv.core.Point>): Array<org.opencv.core.Point> {
        val sorted = pts.sortedWith(compareBy({ it.y + it.x }, { it.y - it.x }))
        val result = Array(4) { org.opencv.core.Point() }
        result[0] = sorted[0] // top-left
        result[2] = sorted[3] // bottom-right
        val remain = sorted.subList(1, 3)
        if (remain[0].x > remain[1].x) {
            result[1] = remain[0] // top-right
            result[3] = remain[1] // bottom-left
        } else {
            result[1] = remain[1]
            result[3] = remain[0]
        }
        return result
    }
} 