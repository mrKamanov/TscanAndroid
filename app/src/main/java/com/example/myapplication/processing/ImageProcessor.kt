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

/**
 * Класс для обработки изображений с использованием OpenCV
 * Содержит методы конвертации, поворота и обработки кадров
 */
class ImageProcessor {
    
    companion object {
        private const val TAG = "ImageProcessor"
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
     * Основная обработка кадра с помощью OpenCV
     * Включает поиск контура бланка, перспективное преобразование и OMR обработку
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
        overlayMode: Boolean = false
    ): Pair<Bitmap, OMRResult?> {
        var omrResult: OMRResult? = null
        val inputMat = Mat()
        Utils.bitmapToMat(inputBitmap, inputMat)
        
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
        return Pair(outputBitmap, omrResult)
    }
    
    /**
     * Обработка тестового бланка с OMR
     */
    private fun processTestSheet(warpMat: Mat, questionsCount: Int, choicesCount: Int, correctAnswers: List<Int>): OMRResult {
        try {
            // Конвертируем в оттенки серого
            val grayMat = Mat()
            Imgproc.cvtColor(warpMat, grayMat, Imgproc.COLOR_BGR2GRAY)
            
            // Бинаризация для выделения заполненных областей
            val threshMat = Mat()
            Imgproc.threshold(grayMat, threshMat, 170.0, 255.0, Imgproc.THRESH_BINARY_INV)
            
            // Разделяем изображение на ячейки
            val cellWidth = warpMat.cols() / choicesCount
            val cellHeight = warpMat.rows() / questionsCount
            
            // Массив для хранения количества пикселей в каждой ячейке
            val pixelValues = Array(questionsCount) { IntArray(choicesCount) }
            
            // Подсчитываем пиксели в каждой ячейке
            for (question in 0 until questionsCount) {
                for (choice in 0 until choicesCount) {
                    val x = choice * cellWidth
                    val y = question * cellHeight
                    val cell = threshMat.submat(y, y + cellHeight, x, x + cellWidth)
                    pixelValues[question][choice] = Core.countNonZero(cell)
                    cell.release()
                }
            }
            
            // Определяем выбранные ответы (максимум пикселей в строке)
            val selectedAnswers = IntArray(questionsCount)
            for (question in 0 until questionsCount) {
                var maxPixels = 0
                var selectedChoice = 0
                for (choice in 0 until choicesCount) {
                    if (pixelValues[question][choice] > maxPixels) {
                        maxPixels = pixelValues[question][choice]
                        selectedChoice = choice
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
            
            // Освобождаем ресурсы
            grayMat.release()
            threshMat.release()
            
            return OMRResult(
                selectedAnswers = selectedAnswers,
                grading = grading,
                incorrectQuestions = incorrectQuestions,
                correctAnswers = correctAnswers
            )
            
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