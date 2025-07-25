package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import android.util.Log
import org.opencv.core.Core
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f

class ScanActivity : AppCompatActivity() {
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraBound = false
    private lateinit var previewView: PreviewView
    private lateinit var resultOverlay: ImageView
    private lateinit var btnStartCamera: Button
    private lateinit var btnStopCamera: Button
    private lateinit var btnHideVideo: Button
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                android.widget.Toast.makeText(this, "Требуется разрешение на камеру", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    private var overlayMode = false
    
    // Переменные для работы с сеткой
    private var questionsCount = 5
    private var choicesCount = 5
    private var correctAnswers = mutableListOf<Int>()
    private var isGridVisible = false
    private lateinit var gridOverlay: android.widget.LinearLayout
    private lateinit var editQuestions: EditText
    private lateinit var editChoices: EditText
    
    // Переменные для результатов проверки
    private var lastGrading = mutableListOf<Int>()
    private var lastIncorrectQuestions = mutableListOf<Map<String, Any>>()
    private var isProcessingEnabled = true
    
    // Переменные для управления паузой
    private var isPaused = false
    private var pausedFrame: Bitmap? = null
    private var pausedResult: String? = null
    
    // Переменные для UI маркеров
    private lateinit var resultsOverlay: android.widget.FrameLayout
    private var currentSelectedAnswers = IntArray(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        // Инициализация OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("ScanActivity", "Ошибка инициализации OpenCV")
        }

        // Полноэкранный режим (immersive mode)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        previewView = findViewById(R.id.camera_preview)
        resultOverlay = findViewById(R.id.result_overlay)
        btnStartCamera = findViewById(R.id.btn_drawer_start_camera)
        btnStopCamera = findViewById(R.id.btn_drawer_stop_camera)
        btnHideVideo = findViewById(R.id.btn_drawer_hide_video)
        val btnCameraSettings = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_camera_settings)
        val btnCloseCameraSettings = findViewById<ImageButton>(R.id.btn_close_camera_settings)
        
        // Инициализация элементов сетки
        gridOverlay = findViewById(R.id.grid_overlay)
        resultsOverlay = findViewById(R.id.results_overlay)
        editQuestions = findViewById(R.id.edit_questions)
        editChoices = findViewById(R.id.edit_choices)
        
        // Установка начальных значений
        editQuestions.setText(questionsCount.toString())
        editChoices.setText(choicesCount.toString())
        
        // Создание начальной сетки
        createAnswersGrid()

        btnStartCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        btnStopCamera.setOnClickListener {
            stopCamera()
        }
        btnHideVideo.setOnClickListener {
            if (previewView.visibility == View.VISIBLE) {
                previewView.visibility = View.INVISIBLE
                resultOverlay.visibility = View.INVISIBLE
                btnHideVideo.text = "Показать видео"
            } else {
                previewView.visibility = View.VISIBLE
                resultOverlay.visibility = View.VISIBLE
                btnHideVideo.text = "Скрыть видео"
            }
        }
        // Слайдеры настроек камеры
        val seekBrightness = findViewById<SeekBar?>(R.id.seek_brightness)
        val valueBrightness = findViewById<TextView?>(R.id.value_brightness)
        seekBrightness?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Яркость: от -100 до +100
                valueBrightness?.text = (progress - 100).toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val seekContrast = findViewById<SeekBar?>(R.id.seek_contrast)
        val valueContrast = findViewById<TextView?>(R.id.value_contrast)
        seekContrast?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Контраст: 0..200
                valueContrast?.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val seekSaturation = findViewById<SeekBar?>(R.id.seek_saturation)
        val valueSaturation = findViewById<TextView?>(R.id.value_saturation)
        seekSaturation?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Насыщенность: 0..200
                valueSaturation?.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val seekSharpness = findViewById<SeekBar?>(R.id.seek_sharpness)
        val valueSharpness = findViewById<TextView?>(R.id.value_sharpness)
        seekSharpness?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Резкость: 0..100
                valueSharpness?.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // --- Кнопки управления под видеопотоком ---
        val btnToggleGrid = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_toggle_grid)
        val btnUpdateAnswers = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_update_answers)
        val btnOverlayMode = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_overlay_mode)

        btnToggleGrid.setOnClickListener {
            isGridVisible = !isGridVisible
            gridOverlay.visibility = if (isGridVisible) View.VISIBLE else View.GONE
            btnToggleGrid.setIconResource(
                if (isGridVisible) R.drawable.ic_grid_on else R.drawable.ic_grid_off
            )
            // Обновляем title кнопки
            btnToggleGrid.contentDescription = if (isGridVisible) "Скрыть сетку" else "Показать сетку"
            
            // Скрываем маркеры результатов при переключении сетки
            resultsOverlay.visibility = View.GONE
        }

        val btnToggleProcessing = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_toggle_processing)
        btnToggleProcessing.setOnClickListener {
            isProcessingEnabled = !isProcessingEnabled
            btnToggleProcessing.setIconResource(
                if (isProcessingEnabled) R.drawable.ic_check_circle else R.drawable.ic_cancel
            )
            btnToggleProcessing.backgroundTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(
                    if (isProcessingEnabled) R.color.success else R.color.error,
                    theme
                )
            )
            btnToggleProcessing.contentDescription = if (isProcessingEnabled) "Выключить проверку" else "Включить проверку"
            
            // Скрываем/показываем маркеры результатов
            resultsOverlay.visibility = if (isProcessingEnabled && currentSelectedAnswers.isNotEmpty()) View.VISIBLE else View.GONE
            
            val message = if (isProcessingEnabled) "Проверка включена" else "Проверка выключена"
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        }

        btnUpdateAnswers.setOnClickListener {
            updateCorrectAnswers()
        }

        val btnStopFrame = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_stop_frame)
        btnStopFrame.setOnClickListener {
            togglePause()
        }

        val btnAddToReport = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_to_report)
        btnAddToReport.isEnabled = false // Изначально неактивна
        btnAddToReport.setOnClickListener {
            addToReport()
        }



        btnOverlayMode.setOnClickListener {
            overlayMode = !overlayMode
            btnOverlayMode.setIconResource(
                if (overlayMode) R.drawable.ic_toggle_on else R.drawable.ic_layers
            )
            
            // Скрываем маркеры результатов при переключении режимов
            resultsOverlay.visibility = View.GONE
        }

        btnCameraSettings.setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.END)
        }
        btnCloseCameraSettings.setOnClickListener {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END)
        }

        // --- Кнопки навигации из бокового меню ---
        val btnNavHome = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_nav_home)
        val btnNavReports = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_nav_reports)

        btnNavHome.setOnClickListener {
            // Переход на главный экран
            val intent = android.content.Intent(this, MainActivity::class.java)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }
        btnNavReports.setOnClickListener {
            // Переход на экран отчётов (если нет ReportsActivity — TODO)
            try {
                val intent = android.content.Intent(this, ReportsActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Экран отчётов не реализован", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        // Обработчик кнопки "Применить" для сетки
        val btnApplyGridSettings = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_apply_grid_settings)
        btnApplyGridSettings.setOnClickListener {
            applyGridSettings()
        }

        // CameraX provider init (но не запуск)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCamera() {
        if (cameraProvider == null || cameraBound) return
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this), { imageProxy ->
            try {
                if (!isPaused) {
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees.toFloat()
                    val bitmap = imageProxyToBitmap(imageProxy)
                    val rotatedBitmap = rotateBitmap(bitmap, rotationDegrees)
                    // Обработка с помощью нативной OpenCV
                    val processedBitmap = processFrameWithOpenCV(rotatedBitmap, previewView.width, previewView.height)
                    
                    // Сохраняем кадр для паузы
                    if (isPaused && pausedFrame == null) {
                        pausedFrame = processedBitmap
                    }
                    
                    runOnUiThread {
                        resultOverlay.setImageBitmap(processedBitmap)
                    }
                } else {
                    // В режиме паузы показываем сохраненный кадр
                    pausedFrame?.let { frame ->
                        runOnUiThread {
                            resultOverlay.setImageBitmap(frame)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ScanActivity", "Ошибка обработки кадра: ${e.message}", e)
            } finally {
                imageProxy.close()
            }
        })
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cameraProvider?.unbindAll()
        cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
        cameraBound = true
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraBound = false
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
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

    private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return src
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun processFrameWithOpenCV(inputBitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
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
                val processedWarp = processTestSheet(warp)
                drawGridOnWarp(processedWarp)
                
                if (overlayMode) {
                    // В режиме overlay показываем исходный кадр с выделенным контуром бланка
                    val contourMat = inputMat.clone()
                    Imgproc.drawContours(contourMat, listOf(pageContour), 0, org.opencv.core.Scalar(0.0, 255.0, 0.0), 3)
                    contourMat.copyTo(inputMat)
                    contourMat.release()
                } else {
                    // Показываем обработанный warp-бланк
                    val resizedWarp = Mat()
                    Imgproc.resize(processedWarp, resizedWarp, Size(inputWidth.toDouble(), inputHeight.toDouble()))
                    resizedWarp.copyTo(inputMat)
                    resizedWarp.release()
                }
            } else {
                // Рисуем сетку на warp-бланке если включена
                drawGridOnWarp(warp)

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
        return outputBitmap
    }
    
    private fun processTestSheet(warpMat: Mat): Mat {
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
            
            // Сохраняем результаты
            lastGrading.clear()
            lastGrading.addAll(grading.toList())
            lastIncorrectQuestions.clear()
            lastIncorrectQuestions.addAll(incorrectQuestions)
            
            // Создаем UI маркеры вместо рисования на изображении
            currentSelectedAnswers = selectedAnswers
            createUIMarkers(selectedAnswers, grading, correctAnswers)
            
            // Обновляем UI с результатами
            updateResultsUI(grading, incorrectQuestions)
            
            // Освобождаем ресурсы
            grayMat.release()
            threshMat.release()
            
            return warpMat
            
        } catch (e: Exception) {
            Log.e("ScanActivity", "Ошибка обработки тестового бланка: ${e.message}", e)
            return warpMat
        }
    }
    
    private fun createUIMarkers(selectedAnswers: IntArray, grading: IntArray, correctAnswers: List<Int>) {
        runOnUiThread {
            // Очищаем предыдущие маркеры
            resultsOverlay.removeAllViews()
            
            // Показываем overlay с результатами
            resultsOverlay.visibility = View.VISIBLE
            
            val containerWidth = resultsOverlay.width
            val containerHeight = resultsOverlay.height
            
            if (containerWidth == 0 || containerHeight == 0) {
                // Если размеры еще не известны, создаем маркеры позже
                resultsOverlay.post { createUIMarkers(selectedAnswers, grading, correctAnswers) }
                return@runOnUiThread
            }
            
            val cellWidth = containerWidth / choicesCount
            val cellHeight = containerHeight / questionsCount
            
            for (question in 0 until questionsCount) {
                val selectedChoice = selectedAnswers[question]
                val centerX = (selectedChoice * cellWidth) + (cellWidth / 2)
                val centerY = (question * cellHeight) + (cellHeight / 2)
                
                val isCorrect = grading[question] == 1
                // Маркер занимает 70% от меньшей стороны ячейки, но не меньше 20dp
                val markSize = maxOf(20, minOf(cellWidth, cellHeight) * 7 / 10)
                
                if (isCorrect) {
                    // Зеленая галочка для правильного ответа
                    val checkMark = createCheckMark(markSize)
                    checkMark.layoutParams = android.widget.FrameLayout.LayoutParams(
                        markSize, markSize
                    ).apply {
                        leftMargin = centerX - markSize / 2
                        topMargin = centerY - markSize / 2
                    }
                    resultsOverlay.addView(checkMark)
                    
                } else {
                    // Красный крестик для неправильного ответа
                    val crossMark = createCrossMark(markSize)
                    crossMark.layoutParams = android.widget.FrameLayout.LayoutParams(
                        markSize, markSize
                    ).apply {
                        leftMargin = centerX - markSize / 2
                        topMargin = centerY - markSize / 2
                    }
                    resultsOverlay.addView(crossMark)
                    
                    // Показываем правильный ответ желтым кружком со звездочкой
                    if (question < correctAnswers.size) {
                        val correctChoice = correctAnswers[question]
                        val correctCenterX = (correctChoice * cellWidth) + (cellWidth / 2)
                        val correctCenterY = (question * cellHeight) + (cellHeight / 2)
                        
                        val correctMark = createCorrectAnswerMark(markSize)
                        correctMark.layoutParams = android.widget.FrameLayout.LayoutParams(
                            markSize, markSize
                        ).apply {
                            leftMargin = correctCenterX - markSize / 2
                            topMargin = correctCenterY - markSize / 2
                        }
                        resultsOverlay.addView(correctMark)
                    }
                }
            }
        }
    }
    
    private fun createCheckMark(size: Int): android.view.View {
        return android.view.View(this).apply {
            // Создаем кастомный Drawable с галочкой
            background = object : android.graphics.drawable.Drawable() {
                override fun draw(canvas: android.graphics.Canvas) {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.rgb(76, 175, 80) // Зеленый фон
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    
                    // Рисуем круг
                    val centerX = bounds.width() / 2f
                    val centerY = bounds.height() / 2f
                    val radius = minOf(bounds.width(), bounds.height()) / 2f - 4f
                    canvas.drawCircle(centerX, centerY, radius, paint)
                    
                    // Рисуем белую обводку
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.color = android.graphics.Color.WHITE
                    paint.strokeWidth = size / 16f
                    canvas.drawCircle(centerX, centerY, radius, paint)
                    
                    // Рисуем галочку
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.color = android.graphics.Color.WHITE
                    paint.strokeWidth = size / 12f
                    paint.strokeCap = android.graphics.Paint.Cap.ROUND
                    
                    val checkSize = radius * 0.6f
                    val startX = centerX - checkSize / 2
                    val startY = centerY
                    val midX = centerX - checkSize / 6
                    val midY = centerY + checkSize / 3
                    val endX = centerX + checkSize / 2
                    val endY = centerY - checkSize / 3
                    
                    // Рисуем галочку двумя линиями
                    canvas.drawLine(startX, startY, midX, midY, paint)
                    canvas.drawLine(midX, midY, endX, endY, paint)
                }
                
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            }
        }
    }
    
    private fun createCrossMark(size: Int): android.view.View {
        return android.view.View(this).apply {
            // Создаем кастомный Drawable с крестиком
            background = object : android.graphics.drawable.Drawable() {
                override fun draw(canvas: android.graphics.Canvas) {
                    val paint = android.graphics.Paint().apply {
                        color = resources.getColor(R.color.error, theme) // Красный фон
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    
                    // Рисуем круг
                    val centerX = bounds.width() / 2f
                    val centerY = bounds.height() / 2f
                    val radius = minOf(bounds.width(), bounds.height()) / 2f - 4f
                    canvas.drawCircle(centerX, centerY, radius, paint)
                    
                    // Рисуем белую обводку
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.color = android.graphics.Color.WHITE
                    paint.strokeWidth = size / 16f
                    canvas.drawCircle(centerX, centerY, radius, paint)
                    
                    // Рисуем крестик
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.color = android.graphics.Color.WHITE
                    paint.strokeWidth = size / 12f
                    paint.strokeCap = android.graphics.Paint.Cap.ROUND
                    
                    val crossSize = radius * 0.6f
                    val leftX = centerX - crossSize / 2
                    val rightX = centerX + crossSize / 2
                    val topY = centerY - crossSize / 2
                    val bottomY = centerY + crossSize / 2
                    
                    // Рисуем крестик двумя линиями
                    canvas.drawLine(leftX, topY, rightX, bottomY, paint)
                    canvas.drawLine(leftX, bottomY, rightX, topY, paint)
                }
                
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            }
        }
    }
    
    private fun createCorrectAnswerMark(size: Int): android.view.View {
        return android.view.View(this).apply {
            // Создаем кастомный Drawable с мишенью
            background = object : android.graphics.drawable.Drawable() {
                override fun draw(canvas: android.graphics.Canvas) {
                    val centerX = bounds.width() / 2f
                    val centerY = bounds.height() / 2f
                    val maxRadius = minOf(bounds.width(), bounds.height()) / 2f - 4f
                    
                    val paint = android.graphics.Paint().apply {
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    
                    // Рисуем концентрические круги мишени (от внешнего к внутреннему)
                    // Внешний круг - белый
                    paint.color = android.graphics.Color.WHITE
                    canvas.drawCircle(centerX, centerY, maxRadius, paint)
                    
                    // Следующий круг - красный
                    paint.color = android.graphics.Color.RED
                    canvas.drawCircle(centerX, centerY, maxRadius * 0.8f, paint)
                    
                    // Следующий круг - белый
                    paint.color = android.graphics.Color.WHITE
                    canvas.drawCircle(centerX, centerY, maxRadius * 0.6f, paint)
                    
                    // Следующий круг - красный
                    paint.color = android.graphics.Color.RED
                    canvas.drawCircle(centerX, centerY, maxRadius * 0.4f, paint)
                    
                    // Центральный круг - белый
                    paint.color = android.graphics.Color.WHITE
                    canvas.drawCircle(centerX, centerY, maxRadius * 0.2f, paint)
                    
                    // Рисуем внешнюю обводку
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.color = android.graphics.Color.rgb(255, 193, 7) // Желтая обводка
                    paint.strokeWidth = size / 16f
                    canvas.drawCircle(centerX, centerY, maxRadius, paint)
                }
                
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            }
        }
    }
    
    private fun updateResultsUI(grading: IntArray, incorrectQuestions: List<Map<String, Any>>) {
        runOnUiThread {
            val correctCount = grading.sum()
            val score = if (questionsCount > 0) (correctCount.toFloat() / questionsCount) * 100 else 0f
            
            // Обновляем текст результата (если есть TextView для этого)
            val resultText = "Результат: $correctCount/$questionsCount (${String.format("%.1f", score)}%)"
            Log.d("ScanActivity", resultText)
            
            // Сохраняем результат для паузы
            pausedResult = resultText
            
            // Можно добавить TextView для отображения результатов
            // findViewById<TextView>(R.id.result_text)?.text = resultText
        }
    }
    
    private fun togglePause() {
        isPaused = !isPaused
        
        val btnStopFrame = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_stop_frame)
        val btnAddToReport = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_to_report)
        
        if (isPaused) {
            // Включаем паузу
            btnStopFrame.setIconResource(R.drawable.ic_play_arrow)
            btnStopFrame.contentDescription = "Возобновить видео"
            btnAddToReport.isEnabled = true // Активируем кнопку добавления в отчет
            
            android.widget.Toast.makeText(this, "Видео поставлено на паузу", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            // Выключаем паузу
            btnStopFrame.setIconResource(R.drawable.stop_frame_button)
            btnStopFrame.contentDescription = "Остановить кадр"
            btnAddToReport.isEnabled = false // Деактивируем кнопку добавления в отчет
            
            // Очищаем сохраненный кадр
            pausedFrame = null
            pausedResult = null
            
            android.widget.Toast.makeText(this, "Видео возобновлено", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun addToReport() {
        if (pausedResult != null && lastGrading.isNotEmpty()) {
            // Здесь можно добавить логику сохранения в отчет
            // Пока просто показываем уведомление
            android.widget.Toast.makeText(this, "Результат добавлен в отчет: $pausedResult", android.widget.Toast.LENGTH_LONG).show()
            
            // Можно добавить сохранение в SharedPreferences или базу данных
            Log.d("ScanActivity", "Добавлен в отчет: $pausedResult")
            Log.d("ScanActivity", "Оценки: $lastGrading")
            Log.d("ScanActivity", "Неправильные вопросы: $lastIncorrectQuestions")
        } else {
            android.widget.Toast.makeText(this, "Нет результатов для добавления в отчет", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun sortPoints(pts: Array<org.opencv.core.Point>): Array<org.opencv.core.Point> {
        // Сортировка точек: [top-left, top-right, bottom-right, bottom-left]
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    // Методы для работы с сеткой ответов
    
    private fun applyGridSettings() {
        try {
            val newQuestions = editQuestions.text.toString().toInt()
            val newChoices = editChoices.text.toString().toInt()
            
            if (newQuestions < 1 || newQuestions > 50) {
                android.widget.Toast.makeText(this, "Количество вопросов должно быть от 1 до 50", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            
            if (newChoices < 1 || newChoices > 10) {
                android.widget.Toast.makeText(this, "Количество вариантов должно быть от 1 до 10", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            
            questionsCount = newQuestions
            choicesCount = newChoices
            correctAnswers.clear()
            
            // Пересоздаем сетку с новыми параметрами
            createAnswersGrid()
            // Обновляем видимость сетки
            gridOverlay.visibility = if (isGridVisible) View.VISIBLE else View.GONE
            
            android.widget.Toast.makeText(this, "Настройки сетки применены", android.widget.Toast.LENGTH_SHORT).show()
            
        } catch (e: NumberFormatException) {
            android.widget.Toast.makeText(this, "Введите корректные числа", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createAnswersGrid() {
        gridOverlay.removeAllViews()
        
        // Получаем размеры контейнера камеры
        val containerWidth = gridOverlay.width
        val containerHeight = gridOverlay.height
        
        if (containerWidth == 0 || containerHeight == 0) {
            // Если размеры еще не известны, создаем сетку позже
            gridOverlay.post { createAnswersGrid() }
            return
        }
        
        val cellWidth = containerWidth / choicesCount
        val cellHeight = containerHeight / questionsCount
        
        // Создаем контейнер для сетки с границами
        val gridContainer = android.widget.FrameLayout(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        // Создаем фон с границами сетки
        val gridBackground = android.view.View(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = createGridBackground(containerWidth, containerHeight, cellWidth, cellHeight)
        }
        gridContainer.addView(gridBackground)
        
        // Создаем ячейки для выбора ответов
        for (questionIndex in 0 until questionsCount) {
            for (choiceIndex in 0 until choicesCount) {
                val cellView = android.widget.TextView(this).apply {
                    text = "${questionIndex + 1}.${choiceIndex + 1}"
                    textSize = 12f
                    setTextColor(resources.getColor(R.color.text_inverse, theme))
                    gravity = android.view.Gravity.CENTER
                    background = android.graphics.drawable.ColorDrawable(
                        android.graphics.Color.argb(50, 255, 255, 255)
                    )
                    
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        cellWidth,
                        cellHeight
                    ).apply {
                        leftMargin = choiceIndex * cellWidth
                        topMargin = questionIndex * cellHeight
                    }
                    
                    // Сохраняем информацию о вопросе и варианте
                    tag = Pair(questionIndex, choiceIndex)
                    
                    // Обработчик клика для выбора ответа
                    setOnClickListener {
                        // Убираем выделение со всех ячеек в этом вопросе
                        for (i in 0 until choicesCount) {
                            val otherCell = gridContainer.findViewWithTag<android.widget.TextView>(Pair(questionIndex, i))
                            otherCell?.background = android.graphics.drawable.ColorDrawable(
                                android.graphics.Color.argb(50, 255, 255, 255)
                            )
                        }
                        
                        // Выделяем выбранную ячейку
                        background = android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.argb(150, 0, 255, 0)
                        )
                    }
                }
                
                gridContainer.addView(cellView)
            }
        }
        
        gridOverlay.addView(gridContainer)
    }
    
    private fun createGridBackground(width: Int, height: Int, cellWidth: Int, cellHeight: Int): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            override fun draw(canvas: android.graphics.Canvas) {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(200, 255, 79, 0)
                    strokeWidth = 3f
                    style = android.graphics.Paint.Style.STROKE
                }
                
                // Рисуем вертикальные линии
                for (i in 0..choicesCount) {
                    val x = i * cellWidth.toFloat()
                    canvas.drawLine(x, 0f, x, height.toFloat(), paint)
                }
                
                // Рисуем горизонтальные линии
                for (i in 0..questionsCount) {
                    val y = i * cellHeight.toFloat()
                    canvas.drawLine(0f, y, width.toFloat(), y, paint)
                }
            }
            
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
    }
    
    private fun updateCorrectAnswers() {
        correctAnswers.clear()
        var allQuestionsAnswered = true
        
        // Проходим по всем вопросам
        for (questionIndex in 0 until questionsCount) {
            var questionAnswered = false
            
            // Ищем выбранную ячейку для текущего вопроса
            for (choiceIndex in 0 until choicesCount) {
                // Ищем в контейнере сетки
                val gridContainer = gridOverlay.getChildAt(0) as? android.widget.FrameLayout
                val cellView = gridContainer?.findViewWithTag<android.widget.TextView>(Pair(questionIndex, choiceIndex))
                if (cellView != null) {
                    val background = cellView.background as? android.graphics.drawable.ColorDrawable
                    if (background?.color == android.graphics.Color.argb(150, 0, 255, 0)) {
                        correctAnswers.add(choiceIndex)
                        questionAnswered = true
                        break
                    }
                }
            }
            
            if (!questionAnswered) {
                allQuestionsAnswered = false
                break
            }
        }
        
        if (allQuestionsAnswered) {
            android.widget.Toast.makeText(this, "Правильные ответы обновлены", android.widget.Toast.LENGTH_SHORT).show()
            Log.d("ScanActivity", "Правильные ответы: $correctAnswers")
            
            // Включаем обработку после обновления ответов
            isProcessingEnabled = true
        } else {
            android.widget.Toast.makeText(this, "Выберите ответ для каждого вопроса", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun drawGridOnWarp(warpMat: Mat) {
        if (!isGridVisible) return
        
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
} 