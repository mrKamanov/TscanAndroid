package com.example.myapplication

// ===== ИМПОРТЫ =====
// Импорты для работы с UI компонентами
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
import android.widget.ImageView

// Импорты для работы с камерой (CameraX)
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

// Импорты для работы с OpenCV (только для инициализации)
import org.opencv.android.OpenCVLoader
import android.util.Log

class ScanActivity : AppCompatActivity() {
    // ===== ПЕРЕМЕННЫЕ ДЛЯ РАБОТЫ С КАМЕРОЙ =====
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
    
    // ===== ПЕРЕМЕННЫЕ ДЛЯ РАБОТЫ С СЕТКОЙ ОТВЕТОВ =====
    private lateinit var gridOverlay: android.widget.LinearLayout
    private lateinit var editQuestions: EditText
    private lateinit var editChoices: EditText
    
    // ===== ПЕРЕМЕННЫЕ ДЛЯ РЕЗУЛЬТАТОВ ПРОВЕРКИ =====
    private var lastGrading = mutableListOf<Int>()
    private var lastIncorrectQuestions = mutableListOf<Map<String, Any>>()
    private var isProcessingEnabled = true
    
    // ===== ПЕРЕМЕННЫЕ ДЛЯ УПРАВЛЕНИЯ ПАУЗОЙ =====
    private var isPaused = false
    private var pausedFrame: Bitmap? = null
    private var pausedResult: String? = null
    
    // ===== ПЕРЕМЕННЫЕ ДЛЯ UI МАРКЕРОВ =====
    private lateinit var resultsOverlay: android.widget.FrameLayout
    private var currentSelectedAnswers = IntArray(0)

    // ===== ИНИЦИАЛИЗАЦИЯ АКТИВНОСТИ =====
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        // ===== ИНИЦИАЛИЗАЦИЯ OPENCV И НАСТРОЙКА ПОЛНОЭКРАННОГО РЕЖИМА =====
        // Инициализация OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("ScanActivity", "Ошибка инициализации OpenCV")
        }

        // Полноэкранный режим (immersive mode)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // ===== ИНИЦИАЛИЗАЦИЯ UI ЭЛЕМЕНТОВ =====
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
        
        // ===== НАСТРОЙКА НАЧАЛЬНЫХ ЗНАЧЕНИЙ И СОЗДАНИЕ СЕТКИ =====
        // Инициализация модулей
        markerRenderer = com.example.myapplication.ui.MarkerRenderer(this)
        gridManager = com.example.myapplication.ui.GridManager(this, gridOverlay)
        
        // Установка начальных значений
        editQuestions.setText(gridManager.getQuestionsCount().toString())
        editChoices.setText(gridManager.getChoicesCount().toString())
        
        // Создание начальной сетки
        gridManager.createAnswersGrid()

        // ===== ОБРАБОТЧИКИ КНОПОК УПРАВЛЕНИЯ КАМЕРОЙ =====
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
        // ===== СЛАЙДЕРЫ НАСТРОЕК КАМЕРЫ =====
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

        // ===== КНОПКИ УПРАВЛЕНИЯ ПОД ВИДЕОПОТОКОМ =====
        // --- Кнопки управления под видеопотоком ---
        val btnToggleGrid = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_toggle_grid)
        val btnUpdateAnswers = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_update_answers)
        val btnOverlayMode = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_overlay_mode)

        btnToggleGrid.setOnClickListener {
            val newVisibility = !gridManager.isGridVisible()
            gridManager.setGridVisible(newVisibility)
            btnToggleGrid.setIconResource(
                if (newVisibility) R.drawable.ic_grid_on else R.drawable.ic_grid_off
            )
            // Обновляем title кнопки
            btnToggleGrid.contentDescription = if (newVisibility) "Скрыть сетку" else "Показать сетку"
            
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
            if (gridManager.updateCorrectAnswers()) {
                // Включаем обработку после обновления ответов
                isProcessingEnabled = true
            }
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
            try {
                val newQuestions = editQuestions.text.toString().toInt()
                val newChoices = editChoices.text.toString().toInt()
                
                if (gridManager.applyGridSettings(newQuestions, newChoices)) {
                    // Обновляем видимость сетки
                    gridManager.setGridVisible(gridManager.isGridVisible())
                }
            } catch (e: NumberFormatException) {
                android.widget.Toast.makeText(this, "Введите корректные числа", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // CameraX provider init (но не запуск)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
        }, ContextCompat.getMainExecutor(this))
    }

    // ===== МЕТОДЫ РАБОТЫ С КАМЕРОЙ =====
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
                    val bitmap = imageProcessor.imageProxyToBitmap(imageProxy)
                    val rotatedBitmap = imageProcessor.rotateBitmap(bitmap, rotationDegrees)
                    // Обработка с помощью нативной OpenCV
                    val (processedBitmap, omrResult) = imageProcessor.processFrameWithOpenCV(
                        rotatedBitmap, 
                        previewView.width, 
                        previewView.height,
                        gridManager.getQuestionsCount(),
                        gridManager.getChoicesCount(),
                        gridManager.getCorrectAnswers(),
                        isProcessingEnabled,
                        gridManager.isGridVisible(),
                        overlayMode
                    )
                    
                    // Если есть результаты OMR, создаем маркеры
                    if (omrResult != null && isProcessingEnabled) {
                        currentSelectedAnswers = omrResult.selectedAnswers
                        markerRenderer.createUIMarkers(
                            resultsOverlay,
                            omrResult.selectedAnswers, 
                            omrResult.grading, 
                            omrResult.correctAnswers,
                            gridManager.getQuestionsCount(),
                            gridManager.getChoicesCount()
                        )
                        updateResultsUI(omrResult.grading, omrResult.incorrectQuestions)
                    }
                    
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

    // ===== ИСПОЛЬЗОВАНИЕ МОДУЛЕЙ =====
    private val imageProcessor = com.example.myapplication.processing.ImageProcessor()
    private lateinit var markerRenderer: com.example.myapplication.ui.MarkerRenderer
    private lateinit var gridManager: com.example.myapplication.ui.GridManager
    

    

    

    
    // ===== МЕТОДЫ УПРАВЛЕНИЯ ПАУЗОЙ И ОТЧЕТАМИ =====
    private fun updateResultsUI(grading: IntArray, incorrectQuestions: List<Map<String, Any>>) {
        runOnUiThread {
            val correctCount = grading.sum()
            val questionsCount = gridManager.getQuestionsCount()
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



    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    

    

} 