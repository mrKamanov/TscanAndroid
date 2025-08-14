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
import android.widget.FrameLayout
import android.widget.ProgressBar
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
import android.content.SharedPreferences

// Импорты для работы с OpenCV (только для инициализации)
import org.opencv.android.OpenCVLoader
import android.util.Log

// Импорты для работы с ML моделью
import com.example.myapplication.ml.OMRModelManager
import com.example.myapplication.ml.PredictionResult
import com.example.myapplication.ml.FixedAnswerCallback
import com.example.myapplication.models.OMRResult
import com.example.myapplication.models.FixedAnswer
import com.example.myapplication.utils.SettingsHelper
import kotlinx.coroutines.*
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class ScanActivity : AppCompatActivity(), FixedAnswerCallback {
    // ===== ПЕРЕМЕННЫЕ ДЛЯ РАБОТЫ С КАМЕРОЙ =====
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraBound = false
    lateinit var previewView: PreviewView
    lateinit var resultOverlay: ImageView
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


    // ===== ПЕРЕМЕННЫЕ ДЛЯ РАБОТЫ С СЕТКОЙ ОТВЕТОВ =====
    private lateinit var gridOverlay: android.widget.LinearLayout
    private lateinit var editQuestions: EditText
    private lateinit var editChoices: EditText

    // ===== ПЕРЕМЕННЫЕ ДЛЯ РЕЗУЛЬТАТОВ ПРОВЕРКИ =====
    private var lastGrading = mutableListOf<Int>()
    private var lastIncorrectQuestions = mutableListOf<Map<String, Any>>()

    // ===== ПЕРЕМЕННЫЕ ДЛЯ ОБРАБОТКИ ИСПРАВЛЕНИЙ =====
    private var pendingFixedAnswers = mutableListOf<FixedAnswer>()
    private var currentFixedIndex = 0
    private var fixedAnswerDecisions = mutableMapOf<Int, Boolean>() // question -> считать ошибкой


    // ===== ПЕРЕМЕННЫЕ ДЛЯ УПРАВЛЕНИЯ ПАУЗОЙ =====
    private var isPaused = false
    private var pausedFrame: Bitmap? = null
    private var pausedResult: String? = null

    // ===== ПЕРЕМЕННЫЕ ДЛЯ UI МАРКЕРОВ =====
    private lateinit var resultsOverlay: android.widget.FrameLayout
    private var currentSelectedAnswers = IntArray(0)

    // ===== ПАРАМЕТРЫ КАМЕРЫ (UI) =====
    private var brightness: Int = 0 // -100..+100
    private var contrast: Int = 100 // 0..200
    private var saturation: Int = 100 // 0..200
    private var sharpness: Int = 50 // 0..100

    // ===== ПЕРЕМЕННЫЕ ДЛЯ РАБОТЫ С ML МОДЕЛЬЮ =====
    lateinit var omrModelManager: OMRModelManager
    var isModelReady = false

    // ===== ПЕРЕМЕННЫЕ ДЛЯ РАБОТЫ С ОТЧЕТАМИ =====
    private lateinit var reportsManager: com.example.myapplication.reports.ReportsManager

    // ===== ПЕРЕМЕННЫЕ ДЛЯ НОВОЙ ЛОГИКИ =====
    private var isContourFound = false // Найден ли контур бланка
    private var lastContourBitmap: Bitmap? = null // Последний кадр с найденным контуром
    private var isMLProcessing = false // Выполняется ли ML обработка
    private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())



    // ===== КОНСТАНТЫ ДЛЯ СОХРАНЕНИЯ =====
    companion object {
        private const val PREFS_NAME = "CameraSettings"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_CONTRAST = "contrast"
        private const val KEY_SATURATION = "saturation"
        private const val KEY_SHARPNESS = "sharpness"

        // Значения по умолчанию
        private const val DEFAULT_BRIGHTNESS = 0
        private const val DEFAULT_CONTRAST = 100
        private const val DEFAULT_SATURATION = 100
        private const val DEFAULT_SHARPNESS = 50
    }

    // ===== МЕТОДЫ СОХРАНЕНИЯ/ЗАГРУЗКИ =====
    private fun saveCameraSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_BRIGHTNESS, brightness)
            putInt(KEY_CONTRAST, contrast)
            putInt(KEY_SATURATION, saturation)
            putInt(KEY_SHARPNESS, sharpness)
            apply()
        }
        Log.d("ScanActivity", "💾 Настройки камеры сохранены")
    }

    private fun loadCameraSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        brightness = prefs.getInt(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS)
        contrast = prefs.getInt(KEY_CONTRAST, DEFAULT_CONTRAST)
        saturation = prefs.getInt(KEY_SATURATION, DEFAULT_SATURATION)
        sharpness = prefs.getInt(KEY_SHARPNESS, DEFAULT_SHARPNESS)
        Log.d("ScanActivity", "📂 Настройки камеры загружены")
    }

    private fun resetCameraSettings() {
        brightness = DEFAULT_BRIGHTNESS
        contrast = DEFAULT_CONTRAST
        saturation = DEFAULT_SATURATION
        sharpness = DEFAULT_SHARPNESS

        // Обновляем UI
        findViewById<SeekBar>(R.id.seek_brightness)?.progress = brightness + 100
        findViewById<SeekBar>(R.id.seek_contrast)?.progress = contrast
        findViewById<SeekBar>(R.id.seek_saturation)?.progress = saturation
        findViewById<SeekBar>(R.id.seek_sharpness)?.progress = sharpness * 2 // 0..100 -> 0..200

        findViewById<TextView>(R.id.value_brightness)?.text = brightness.toString()
        findViewById<TextView>(R.id.value_contrast)?.text = contrast.toString()
        findViewById<TextView>(R.id.value_saturation)?.text = saturation.toString()
        findViewById<TextView>(R.id.value_sharpness)?.text = sharpness.toString()

        // Сохраняем
        saveCameraSettings()

        android.widget.Toast.makeText(this, "Настройки сброшены", android.widget.Toast.LENGTH_SHORT).show()
    }

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

        // ===== ЗАГРУЗКА СОХРАНЕННЫХ НАСТРОЕК =====
        loadCameraSettings()

        // ===== ИНИЦИАЛИЗАЦИЯ ML МОДЕЛИ =====
        initializeMLModel()

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

        // ===== ИНИЦИАЛИЗАЦИЯ ТЕКСТА РЕЗУЛЬТАТОВ =====
        val scanResultsTextView = findViewById<TextView>(R.id.scan_results)
        scanResultsTextView?.text = android.text.Html.fromHtml("📋 <b>Ожидание результатов проверки...</b>", android.text.Html.FROM_HTML_MODE_COMPACT)

        // ===== ИНИЦИАЛИЗАЦИЯ МЕНЕДЖЕРА ОТЧЕТОВ =====
        reportsManager = com.example.myapplication.reports.ReportsManager(this)

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

        // Устанавливаем сохраненные значения
        seekBrightness?.progress = brightness + 100
        valueBrightness?.text = brightness.toString()

        seekBrightness?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueBrightness?.text = (progress - 100).toString()
                brightness = progress - 100
                if (fromUser) saveCameraSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val seekContrast = findViewById<SeekBar?>(R.id.seek_contrast)
        val valueContrast = findViewById<TextView?>(R.id.value_contrast)

        // Устанавливаем сохраненные значения
        seekContrast?.progress = contrast
        valueContrast?.text = contrast.toString()

        seekContrast?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueContrast?.text = progress.toString()
                contrast = progress
                if (fromUser) saveCameraSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val seekSaturation = findViewById<SeekBar?>(R.id.seek_saturation)
        val valueSaturation = findViewById<TextView?>(R.id.value_saturation)

        // Устанавливаем сохраненные значения
        seekSaturation?.progress = saturation
        valueSaturation?.text = saturation.toString()

        seekSaturation?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueSaturation?.text = progress.toString()
                saturation = progress
                if (fromUser) saveCameraSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val seekSharpness = findViewById<SeekBar?>(R.id.seek_sharpness)
        val valueSharpness = findViewById<TextView?>(R.id.value_sharpness)

        // Устанавливаем сохраненные значения
        seekSharpness?.progress = sharpness * 2 // 0..100 -> 0..200
        valueSharpness?.text = sharpness.toString()

        seekSharpness?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueSharpness?.text = (progress / 2).toString()
                sharpness = progress / 2
                if (fromUser) saveCameraSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // ===== КНОПКА СБРОСА НАСТРОЕК =====
        val btnResetSettings = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_reset_camera_settings)
        btnResetSettings?.setOnClickListener {
            resetCameraSettings()
        }

        // ===== КНОПКИ УПРАВЛЕНИЯ ПОД ВИДЕОПОТОКОМ =====
        // --- Кнопки управления под видеопотоком ---
        val btnToggleGrid = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_toggle_grid)
        val btnUpdateAnswers = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_update_answers)


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
            // Показываем/скрываем результаты
            val showResults = resultsOverlay.visibility == View.GONE
            resultsOverlay.visibility = if (showResults && currentSelectedAnswers.isNotEmpty()) View.VISIBLE else View.GONE

            btnToggleProcessing.setIconResource(
                if (showResults) R.drawable.ic_check_circle else R.drawable.ic_cancel
            )
            btnToggleProcessing.backgroundTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(
                    if (showResults) R.color.success else R.color.error,
                    theme
                )
            )
            btnToggleProcessing.contentDescription = if (showResults) "Скрыть результаты" else "Показать результаты"

            val message = if (showResults) "Результаты показаны" else "Результаты скрыты"
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        }

        btnUpdateAnswers.setOnClickListener {
            gridManager.updateCorrectAnswers()
            android.widget.Toast.makeText(this, "Правильные ответы обновлены", android.widget.Toast.LENGTH_SHORT).show()

            // Сбрасываем результаты при обновлении ответов
            findViewById<TextView>(R.id.scan_results)?.text = android.text.Html.fromHtml("📋 <b>Ожидание результатов проверки...</b>", android.text.Html.FROM_HTML_MODE_COMPACT)

            // Деактивируем кнопку добавления в отчет
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_to_report)?.isEnabled = false
        }

        val btnStopFrame = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_stop_frame)

        // Инициализируем кнопку как неактивную (бланк не найден)
        btnStopFrame.isEnabled = false
        btnStopFrame.backgroundTintList = android.content.res.ColorStateList.valueOf(
            resources.getColor(android.R.color.darker_gray, theme)
        )
        btnStopFrame.strokeWidth = 2

        btnStopFrame.setOnClickListener {
            if (isPaused) {
                // Возобновляем видео - сбрасываем все результаты
                isPaused = false
                isContourFound = false
                lastContourBitmap = null
                currentSelectedAnswers = IntArray(0)

                // Скрываем результаты и маркеры
                resultsOverlay.visibility = View.GONE

                // Сбрасываем текст результатов
                findViewById<TextView>(R.id.scan_results)?.text = android.text.Html.fromHtml("📋 <b>Ожидание результатов проверки...</b>", android.text.Html.FROM_HTML_MODE_COMPACT)

                // Деактивируем кнопку добавления в отчет
                findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_to_report)?.isEnabled = false

                // Меняем иконку кнопки на "пауза" и деактивируем
                btnStopFrame.setIconResource(R.drawable.stop_frame_button)
                btnStopFrame.contentDescription = "Остановить кадр и запустить ML обработку"
                btnStopFrame.isEnabled = false
                btnStopFrame.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    resources.getColor(android.R.color.darker_gray, theme)
                )
                btnStopFrame.strokeWidth = 2

                android.widget.Toast.makeText(this, "Поиск возобновлен", android.widget.Toast.LENGTH_SHORT).show()
            } else if (isMLProcessing) {
                android.widget.Toast.makeText(this, "ML обработка уже выполняется...", android.widget.Toast.LENGTH_SHORT).show()
            } else if (isContourFound && lastContourBitmap != null && btnStopFrame.isEnabled) {
                // Делаем паузу и запускаем ML обработку
                isPaused = true

                // Показываем зафиксированный бланк
                resultOverlay.setImageBitmap(lastContourBitmap)

                // Используем новый метод для обработки уже готового warp-бланка
                processWarpedFrameWithML(lastContourBitmap!!)

                // Меняем иконку кнопки на "возобновить"
                btnStopFrame.setIconResource(R.drawable.ic_play_arrow)
                btnStopFrame.contentDescription = "Возобновить поиск"

                android.widget.Toast.makeText(this, "Кадр зафиксирован, ML обработка началась...", android.widget.Toast.LENGTH_SHORT).show()
            } else if (!btnStopFrame.isEnabled) {
                android.widget.Toast.makeText(this, "Контур бланка не найден. Поднесите бланк к камере", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        val btnAddToReport = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_to_report)
        btnAddToReport.isEnabled = false // Изначально неактивна
        btnAddToReport.setOnClickListener {
            addToReport()
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

        // ===== НАСТРОЙКИ ПРОИЗВОДИТЕЛЬНОСТИ (в правом меню) =====
        // Эти настройки находятся в правом drawer (drawer_camera_settings.xml)
        // Обработчики будут добавлены в onCreate после инициализации drawer

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

                    // Постоянный поиск контура с помощью OpenCV (быстро)
                    val (processedBitmap, contourFound) = imageProcessor.processFrameWithOpenCV(
                        rotatedBitmap,
                        previewView.width,
                        previewView.height,
                        gridManager.getQuestionsCount(),
                        gridManager.getChoicesCount(),
                        emptyList(), // Не передаем правильные ответы для ML обработки
                        false, // Отключаем ML обработку в основном потоке
                        gridManager.isGridVisible(),

                        brightness,
                        contrast,
                        saturation,
                        sharpness
                    )

                    // Обновляем состояние контура
                    if (contourFound && !isContourFound && !isPaused) {
                        isContourFound = true
                        // Сохраняем обработанный кадр (с найденным контуром)
                        lastContourBitmap = processedBitmap.copy(processedBitmap.config ?: Bitmap.Config.ARGB_8888, true)
                        Log.d("ScanActivity", "🎯 Контур бланка найден! Готов к ML обработке")

                        // Активируем и подсвечиваем кнопку
                        runOnUiThread {
                            val btnStopFrame = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_stop_frame)
                            btnStopFrame.isEnabled = true
                            // Меняем цвет на более яркий
                            btnStopFrame.backgroundTintList = android.content.res.ColorStateList.valueOf(
                                resources.getColor(R.color.success, theme)
                            )
                            btnStopFrame.setStrokeColorResource(R.color.text_inverse)
                            btnStopFrame.strokeWidth = 6
                            Log.d("ScanActivity", "✨ Кнопка активирована и подсвечена зеленым цветом")
                        }
                    } else if (!contourFound && isContourFound && !isPaused) {
                        isContourFound = false
                        lastContourBitmap = null
                        Log.d("ScanActivity", "❌ Контур бланка потерян")

                        // Деактивируем кнопку - делаем серой
                        runOnUiThread {
                            val btnStopFrame = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_stop_frame)
                            btnStopFrame.isEnabled = false
                            btnStopFrame.backgroundTintList = android.content.res.ColorStateList.valueOf(
                                resources.getColor(android.R.color.darker_gray, theme)
                            )
                            btnStopFrame.setStrokeColorResource(R.color.text_inverse)
                            btnStopFrame.strokeWidth = 2
                            Log.d("ScanActivity", "🔴 Кнопка деактивирована и стала серой")
                        }
                    }

                    runOnUiThread {
                        resultOverlay.setImageBitmap(processedBitmap)
                    }
                }
                // Если на паузе - НЕ обрабатываем кадр вообще, просто закрываем
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

    /**
     * Запускает ML обработку уже готового warp-бланка (без поиска контуров)
     */
    private fun processWarpedFrameWithML(warpedBitmap: Bitmap) {
        if (isMLProcessing) return

        isMLProcessing = true
        Log.d("ScanActivity", "🚀 Начинаем ML обработку warp-бланка...")

        // Показываем прогресс-бар
        runOnUiThread {
            findViewById<FrameLayout>(R.id.ml_progress_container).visibility = View.VISIBLE
            findViewById<ProgressBar>(R.id.ml_progress_bar).progress = 0
            findViewById<TextView>(R.id.ml_progress_text).text = "Подготовка warp-бланка..."
        }

        processingScope.launch {
            try {
                val startTime = System.currentTimeMillis()

                // Обновляем прогресс - warp-бланк готов
                withContext(Dispatchers.Main) {
                    findViewById<ProgressBar>(R.id.ml_progress_bar).progress = 40
                    findViewById<TextView>(R.id.ml_progress_text).text = "Warp-бланк готов, начинаем ML анализ..."
                }

                // Небольшая задержка для визуализации
                kotlinx.coroutines.delay(300)

                // Обновляем прогресс - ML обработка
                withContext(Dispatchers.Main) {
                    findViewById<ProgressBar>(R.id.ml_progress_bar).progress = 60
                    findViewById<TextView>(R.id.ml_progress_text).text = "ML анализ ячеек..."
                }

                val omrResult = imageProcessor.processWarpedFrameWithMLAndFixed(
                    warpedBitmap,
                    gridManager.getQuestionsCount(),
                    gridManager.getChoicesCount(),
                    gridManager.getCorrectAnswers(),
                    this@ScanActivity, // FixedAnswerCallback
                ) { question, choice, isFilled ->
                    // Callback для обновления UI по мере обработки ячеек
                    runOnUiThread {
                        val correctAnswers = gridManager.getCorrectAnswers()
                        val isReferenceCell = question < correctAnswers.size && choice == correctAnswers[question]

                        if (isReferenceCell) {
                            // Эталонная ячейка - показываем прогресс этапа 1
                            val progress = 60 + (question * 30 / gridManager.getQuestionsCount())
                            findViewById<ProgressBar>(R.id.ml_progress_bar).progress = progress
                            findViewById<TextView>(R.id.ml_progress_text).text =
                                "🎯 Эталонная ячейка ${question + 1}..."

                            // СРАЗУ показываем результат для эталонной ячейки
                            val tempSelectedAnswers = IntArray(gridManager.getQuestionsCount()) { 0 }
                            tempSelectedAnswers[question] = if (isFilled) choice else 0

                            val tempGrading = IntArray(gridManager.getQuestionsCount()) { 0 }
                            if (question < correctAnswers.size) {
                                tempGrading[question] = if (tempSelectedAnswers[question] == correctAnswers[question]) 1 else 0
                            }

                            // Показываем маркеры для этой ячейки
                            markerRenderer.createUIMarkers(
                                resultsOverlay,
                                tempSelectedAnswers,
                                tempGrading,
                                correctAnswers,
                                gridManager.getQuestionsCount(),
                                gridManager.getChoicesCount()
                            )

                            Log.d("ScanActivity", "🎯 Мгновенный результат для эталонной ячейки [$question][$choice]")

                        } else {
                            // Фоновая ячейка - показываем прогресс этапа 3
                            val progress = 90 + (question * 10 / gridManager.getQuestionsCount())
                            findViewById<ProgressBar>(R.id.ml_progress_bar).progress = progress
                            findViewById<TextView>(R.id.ml_progress_text).text =
                                "🔍 Проверка ошибок: ячейка ${question + 1}-${choice + 1}..."

                            // Показываем маркеры для фоновых ячеек
                            val tempSelectedAnswers = IntArray(gridManager.getQuestionsCount()) { 0 }
                            tempSelectedAnswers[question] = if (isFilled) choice else 0

                            val tempGrading = IntArray(gridManager.getQuestionsCount()) { 0 }
                            if (question < correctAnswers.size) {
                                tempGrading[question] = if (tempSelectedAnswers[question] == correctAnswers[question]) 1 else 0
                            }

                            markerRenderer.createUIMarkers(
                                resultsOverlay,
                                tempSelectedAnswers,
                                tempGrading,
                                correctAnswers,
                                gridManager.getQuestionsCount(),
                                gridManager.getChoicesCount()
                            )

                            Log.d("ScanActivity", "🔍 Фоновый результат для ячейки [$question][$choice]")
                        }
                    }
                }

                val processingTime = System.currentTimeMillis() - startTime

                // Обновляем прогресс - завершение
                withContext(Dispatchers.Main) {
                    findViewById<ProgressBar>(R.id.ml_progress_bar).progress = 100
                    findViewById<TextView>(R.id.ml_progress_text).text = "Завершение..."
                }

                // Небольшая задержка для визуализации
                kotlinx.coroutines.delay(300)

                if (omrResult != null) {
                    Log.d("ScanActivity", "✅ ML обработка warp-бланка завершена за ${processingTime}мс")

                    // Обновляем UI в главном потоке
                    withContext(Dispatchers.Main) {
                        updateUIWithResult(omrResult)
                        findViewById<FrameLayout>(R.id.ml_progress_container).visibility = View.GONE
                        android.widget.Toast.makeText(this@ScanActivity, "Обработка завершена за ${processingTime}мс", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d("ScanActivity", "⚠️ ML обработка warp-бланка завершена за ${processingTime}мс, но результат пустой")
                    withContext(Dispatchers.Main) {
                        findViewById<FrameLayout>(R.id.ml_progress_container).visibility = View.GONE
                        android.widget.Toast.makeText(this@ScanActivity, "Обработка завершена, но результат не найден", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ScanActivity", "❌ Ошибка ML обработки warp-бланка: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    findViewById<FrameLayout>(R.id.ml_progress_container).visibility = View.GONE
                    android.widget.Toast.makeText(this@ScanActivity, "Ошибка обработки: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                isMLProcessing = false
            }
        }
    }

    /**
     * Запускает ML обработку кадра (асинхронная) - приоритетная обработка эталонных ячеек
     */
    private fun processFrameWithML(bitmap: Bitmap) {
        if (isMLProcessing) return

        isMLProcessing = true
        Log.d("ScanActivity", "🚀 Начинаем ML обработку кадра...")

        // Проверяем активную модель
        val activeModel = com.example.myapplication.utils.ModelSettingsHelper.getActiveModel(this)
        val inputSize = com.example.myapplication.utils.ModelSettingsHelper.getActiveModelInputSize(this)
        Log.i("ScanActivity", "📊 Активная модель: $activeModel, размер входа: ${inputSize}×${inputSize}")

        // Показываем прогресс-бар
        runOnUiThread {
            findViewById<FrameLayout>(R.id.ml_progress_container).visibility = View.VISIBLE
            findViewById<ProgressBar>(R.id.ml_progress_bar).progress = 0
            findViewById<TextView>(R.id.ml_progress_text).text = "Подготовка..."
        }

        processingScope.launch {
            try {
                val startTime = System.currentTimeMillis()

                // Обновляем прогресс - поиск контура
                withContext(Dispatchers.Main) {
                    findViewById<ProgressBar>(R.id.ml_progress_bar).progress = 20
                    findViewById<TextView>(R.id.ml_progress_text).text = "Поиск контура бланка..."
                }

                // Небольшая задержка для визуализации
                kotlinx.coroutines.delay(500)

                // Обновляем прогресс - ML обработка
                withContext(Dispatchers.Main) {
                    findViewById<ProgressBar>(R.id.ml_progress_bar).progress = 40
                    findViewById<TextView>(R.id.ml_progress_text).text = "ML анализ ячеек..."
                }

                val omrResult = imageProcessor.processFrameWithML(
                    bitmap,
                    gridManager.getQuestionsCount(),
                    gridManager.getChoicesCount(),
                    gridManager.getCorrectAnswers(),
                    { question, choice, isFilled ->
                        // Callback для обновления UI по мере обработки ячеек
                        runOnUiThread {
                            val correctAnswers = gridManager.getCorrectAnswers()
                            val isReferenceCell = (question < correctAnswers.size) && (choice == correctAnswers[question])

                            if (isReferenceCell) {
                                // Эталонная ячейка - показываем прогресс этапа 1
                                val progress = 40 + ((question * 60) / gridManager.getQuestionsCount())
                                findViewById<ProgressBar>(R.id.ml_progress_bar).progress = progress
                                findViewById<TextView>(R.id.ml_progress_text).text =
                                    "🎯 Эталонная ячейка ${(question + 1)}..."

                                // СРАЗУ показываем результат для эталонной ячейки
                                val tempSelectedAnswers = IntArray(gridManager.getQuestionsCount()) { 0 }
                                tempSelectedAnswers[question] = if (isFilled) choice else 0

                                val tempGrading = IntArray(gridManager.getQuestionsCount()) { 0 }
                                if (question < correctAnswers.size) {
                                    tempGrading[question] = if (tempSelectedAnswers[question] == correctAnswers[question]) 1 else 0
                                }

                                // Показываем маркеры для этой ячейки
                                markerRenderer.createUIMarkers(
                                    resultsOverlay,
                                    tempSelectedAnswers,
                                    tempGrading,
                                    correctAnswers,
                                    gridManager.getQuestionsCount(),
                                    gridManager.getChoicesCount()
                                )

                                Log.d("ScanActivity", "🎯 Мгновенный результат для эталонной ячейки [$question][$choice]")

                            } else {
                                // Фоновая ячейка - показываем прогресс этапа 3
                                val progress = 80 + ((question * 20) / gridManager.getQuestionsCount())
                                findViewById<ProgressBar>(R.id.ml_progress_bar).progress = progress
                                findViewById<TextView>(R.id.ml_progress_text).text =
                                    "🔍 Проверка ошибок: ячейка ${(question + 1)}-${(choice + 1)}..."

                                // Показываем маркеры для фоновых ячеек
                                val tempSelectedAnswers = IntArray(gridManager.getQuestionsCount()) { 0 }
                                tempSelectedAnswers[question] = if (isFilled) choice else 0

                                val tempGrading = IntArray(gridManager.getQuestionsCount()) { 0 }
                                if (question < correctAnswers.size) {
                                    tempGrading[question] = if (tempSelectedAnswers[question] == correctAnswers[question]) 1 else 0
                                }

                                markerRenderer.createUIMarkers(
                                    resultsOverlay,
                                    tempSelectedAnswers,
                                    tempGrading,
                                    correctAnswers,
                                    gridManager.getQuestionsCount(),
                                    gridManager.getChoicesCount()
                                )

                                Log.d("ScanActivity", "🔍 Фоновый результат для ячейки [$question][$choice]")
                            }
                        }
                    }
                )




                    val processingTime = System.currentTimeMillis() - startTime

                // Обновляем прогресс - завершение
                withContext(Dispatchers.Main) {
                    findViewById<ProgressBar>(R.id.ml_progress_bar).progress = 100
                    findViewById<TextView>(R.id.ml_progress_text).text = "Завершение..."
                }

                // Небольшая задержка для визуализации
                kotlinx.coroutines.delay(300)

                if (omrResult != null) {
                    Log.d("ScanActivity", "✅ ML обработка завершена за ${processingTime}мс")

                    // Обновляем UI в главном потоке
                    withContext(Dispatchers.Main) {
                        updateUIWithResult(omrResult)
                        findViewById<FrameLayout>(R.id.ml_progress_container).visibility = View.GONE
                        android.widget.Toast.makeText(this@ScanActivity, "Обработка завершена за ${processingTime}мс", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d("ScanActivity", "⚠️ ML обработка завершена за ${processingTime}мс, но результат пустой")
                    withContext(Dispatchers.Main) {
                        findViewById<FrameLayout>(R.id.ml_progress_container).visibility = View.GONE
                        android.widget.Toast.makeText(this@ScanActivity, "Обработка завершена, но результат не найден", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ScanActivity", "❌ Ошибка ML обработки: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    findViewById<FrameLayout>(R.id.ml_progress_container).visibility = View.GONE
                    android.widget.Toast.makeText(this@ScanActivity, "Ошибка обработки: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                isMLProcessing = false
            }
        }
    }
    /**
     * Обновляет UI с результатами обработки
     */
    private fun updateUIWithResult(omrResult: OMRResult) {
        Log.d("ScanActivity", "🔄 Обновляем UI с результатами: ${omrResult.grading.contentToString()}")
        currentSelectedAnswers = omrResult.selectedAnswers

        // Сохраняем результаты для отчета
        lastGrading.clear()
        lastGrading.addAll(omrResult.grading.toList())
        lastIncorrectQuestions.clear()
        lastIncorrectQuestions.addAll(omrResult.incorrectQuestions)

        markerRenderer.createUIMarkers(
            resultsOverlay,
            omrResult.selectedAnswers,
            omrResult.grading,
            omrResult.correctAnswers,
            gridManager.getQuestionsCount(),
            gridManager.getChoicesCount()
        )
        updateResultsUI(omrResult.grading, omrResult.incorrectQuestions)

        // Показываем результаты только если не на паузе
        if (!isPaused) {
            resultsOverlay.visibility = View.VISIBLE
        }

        // Активируем кнопку добавления в отчет
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_to_report)?.isEnabled = true
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraBound = false
    }

    // ===== ИСПОЛЬЗОВАНИЕ МОДУЛЕЙ =====
    val imageProcessor = com.example.myapplication.processing.ImageProcessor()
    private lateinit var markerRenderer: com.example.myapplication.ui.MarkerRenderer
    lateinit var gridManager: com.example.myapplication.ui.GridManager

    // ===== МЕТОДЫ УПРАВЛЕНИЯ ПАУЗОЙ И ОТЧЕТАМИ =====
    private fun updateResultsUI(grading: IntArray, incorrectQuestions: List<Map<String, Any>>) {
        runOnUiThread {
            try {
                Log.d("ScanActivity", "Обновляем UI результатов: grading=${grading.contentToString()}, incorrectQuestions=$incorrectQuestions")

                val correctCount = grading.sum()
                val questionsCount = gridManager.getQuestionsCount()
                val score = if (questionsCount > 0) (correctCount.toFloat() / questionsCount) * 100 else 0f

                // Создаем краткий текст результатов
                val resultText = buildString {
                    appendLine("📊 <b>Краткие результаты</b>")
                    appendLine()
                    appendLine("✅ Правильных ответов: <b>$correctCount из $questionsCount</b>")
                    appendLine("📈 Процент выполнения: <b>${String.format("%.1f", score)}%</b>")
                    appendLine()
                    appendLine("💡 Нажмите 'Добавить в отчет' для подробной информации")
                }

                // Обновляем TextView с результатами (поддерживает HTML)
                findViewById<TextView>(R.id.scan_results)?.text = android.text.Html.fromHtml(resultText, android.text.Html.FROM_HTML_MODE_COMPACT)

                // Сохраняем результат для паузы
                pausedResult = resultText

                Log.d("ScanActivity", "Результаты обновлены: $correctCount/$questionsCount (${String.format("%.1f", score)}%)")
            } catch (e: Exception) {
                Log.e("ScanActivity", "Ошибка обновления UI результатов: ${e.message}", e)
                // Показываем базовую информацию об ошибке
                findViewById<TextView>(R.id.scan_results)?.text = android.text.Html.fromHtml(
                    "❌ <b>Ошибка отображения результатов</b><br/>Попробуйте еще раз",
                    android.text.Html.FROM_HTML_MODE_COMPACT
                )
            }
        }
    }



    private fun addToReport() {
        Log.d("ScanActivity", "🔍 Попытка добавления в отчет...")
        Log.d("ScanActivity", "lastGrading.size: ${lastGrading.size}, currentSelectedAnswers.size: ${currentSelectedAnswers.size}")

        if (lastGrading.isNotEmpty() && currentSelectedAnswers.isNotEmpty()) {
            try {
                // Создаем OMRResult из текущих данных
                val omrResult = OMRResult(
                    selectedAnswers = currentSelectedAnswers,
                    grading = lastGrading.toIntArray(),
                    incorrectQuestions = lastIncorrectQuestions,
                    correctAnswers = gridManager.getCorrectAnswers().toList()
                )

                // Генерируем название работы
                val workNumber = reportsManager.getReports().size + 1
                val title = "Работа $workNumber"

                Log.d("ScanActivity", "📋 Создаем отчет: $title")
                Log.d("ScanActivity", "selectedAnswers: ${currentSelectedAnswers.contentToString()}")
                Log.d("ScanActivity", "grading: ${lastGrading.toIntArray().contentToString()}")
                Log.d("ScanActivity", "correctAnswers: ${gridManager.getCorrectAnswers().toList()}")

                // Добавляем в отчет
                val report = reportsManager.addReport(omrResult, title)

                // Принудительно сохраняем
                reportsManager.forceSave()

                android.widget.Toast.makeText(
                    this,
                    "✅ Результат добавлен в отчет: $title",
                    android.widget.Toast.LENGTH_LONG
                ).show()

                Log.d("ScanActivity", "📋 Добавлен в отчет: $title (оценка: ${report.grade})")

            } catch (e: Exception) {
                Log.e("ScanActivity", "❌ Ошибка добавления в отчет: ${e.message}", e)
                android.widget.Toast.makeText(
                    this,
                    "❌ Ошибка добавления в отчет: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Log.w("ScanActivity", "❌ Нет результатов для добавления в отчет")
            android.widget.Toast.makeText(this, "Нет результатов для добавления в отчет", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Инициализирует ML модель
     */
    private fun initializeMLModel() {
        Log.d("ScanActivity", "🔧 Начинаем инициализацию ML модели")

        try {
            Log.d("ScanActivity", "📦 Создаем OMRModelManager")
            omrModelManager = OMRModelManager(this)
            OMRModelManager.setGlobalInstance(omrModelManager)

            // Проверяем готовность модели
            Log.d("ScanActivity", "🔍 Проверяем готовность модели")
            if (omrModelManager.isModelReady()) {
                isModelReady = true
                Log.i("ScanActivity", "✅ ML модель инициализирована успешно")

                // Передаем ML модель в ImageProcessor
                Log.d("ScanActivity", "🔗 Передаем ML модель в ImageProcessor")
                imageProcessor.setMLModel(omrModelManager)

                // Показываем информацию о модели
                Log.i("ScanActivity", omrModelManager.getModelInfo())
            } else {
                isModelReady = false
                Log.e("ScanActivity", "❌ ML модель не загружена")
            }
        } catch (e: Exception) {
            isModelReady = false
            Log.e("ScanActivity", "❌ Ошибка инициализации ML модели: ${e.message}")
        }

        Log.d("ScanActivity", "🏁 Инициализация ML модели завершена: isModelReady=$isModelReady")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ===== РЕАЛИЗАЦИЯ FixedAnswerCallback =====

    override fun onFixedAnswerDetected(fixedAnswer: FixedAnswer, onUserDecision: (Boolean) -> Unit) {
        Log.d("ScanActivity", "⚠️ Обнаружено исправление в вопросе ${fixedAnswer.questionNumber}")

        // Проверяем настройку игнорирования исправлений
        val ignoreFixedAnswers = com.example.myapplication.utils.SettingsHelper.getIgnoreFixedAnswers(this)

        if (ignoreFixedAnswers) {
            // Если включено автоигнорирование - считаем исправление правильным
            Log.d("ScanActivity", "✅ Автоигнорирование включено: исправление считается правильным")
            onUserDecision(false) // false = не считать ошибкой
        } else {
            // Если выключено - показываем диалог для ручного решения
            runOnUiThread {
                showFixedAnswerDialog(fixedAnswer, onUserDecision)
            }
        }
    }

    override fun onAllFixedAnswersProcessed(finalResult: OMRResult) {
        Log.d("ScanActivity", "✅ Все исправления обработаны, обновляем UI")

        runOnUiThread {
            // Обновляем UI с финальными результатами
            updateUIWithResult(finalResult)
            Toast.makeText(this, "Обработка исправлений завершена", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Показывает диалог для принятия решения об исправлении
     */
    private fun showFixedAnswerDialog(fixedAnswer: FixedAnswer, onUserDecision: (Boolean) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_fixed_answer, null)

        // Заполняем данные
        dialogView.findViewById<TextView>(R.id.tvQuestionNumber).text = fixedAnswer.questionNumber.toString()

        // Показываем номер ответа (1-based нумерация как в приложении)
        dialogView.findViewById<TextView>(R.id.tvChoiceNumber).text = fixedAnswer.choiceNumber.toString()

        // Показываем превью ячейки
        val imageView = dialogView.findViewById<ImageView>(R.id.ivCellPreview)
        if (fixedAnswer.cellBitmap != null) {
            imageView.setImageBitmap(fixedAnswer.cellBitmap)
        }

        // Создаем диалог
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Убираем белые углы
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Обработчики кнопок
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNo).setOnClickListener {
            // НЕТ - оставить правильным
            onUserDecision(false)
            dialog.dismiss()
            Log.d("ScanActivity", "✅ Пользователь решил оставить вопрос ${fixedAnswer.questionNumber} правильным")
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnYes).setOnClickListener {
            // ДА - считать ошибкой
            onUserDecision(true)
            dialog.dismiss()
            Log.d("ScanActivity", "❌ Пользователь решил считать вопрос ${fixedAnswer.questionNumber} ошибкой")
        }

        dialog.show()
    }

    /**
     * Освобождает ресурсы
     */
    override fun onDestroy() {
        super.onDestroy()

        // Отменяем все корутины
        processingScope.cancel()

        // Освобождаем ресурсы ML модели
        if (::omrModelManager.isInitialized) {
            omrModelManager.release()
        }

        // Очищаем кэш
        lastContourBitmap?.recycle()
        lastContourBitmap = null
        pausedFrame?.recycle()
        pausedFrame = null
    }
} 