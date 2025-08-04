package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.batch.BatchCriteria
import com.example.myapplication.batch.BatchCriteriaManager
import com.example.myapplication.ml.OMRModelManager
import com.example.myapplication.models.BatchResult
import com.example.myapplication.models.OMRResult
import com.example.myapplication.processing.ImageProcessor
import com.example.myapplication.reports.ReportsManager
import org.opencv.android.OpenCVLoader
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.os.Environment
import androidx.core.content.FileProvider

class BatchActivity : AppCompatActivity() {
    private lateinit var etQuestions: EditText
    private lateinit var etChoices: EditText
    private lateinit var layoutCorrectAnswers: LinearLayout
    private lateinit var btnSelectImages: Button
    private lateinit var btnCamera: Button
    private lateinit var btnProcess: Button
    private lateinit var btnReset: Button
    private lateinit var btnSaveCriteria: Button
    private lateinit var btnLoadCriteria: Button
    private lateinit var btnApplyCriteria: Button
    private lateinit var tvSelectedCount: TextView
    private lateinit var rvResults: RecyclerView
    private lateinit var btnAddAllToReport: Button
    
    // Прогресс-бар элементы
    private lateinit var cardProgress: androidx.cardview.widget.CardView
    private lateinit var tvProgressStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressCount: TextView
    
    private lateinit var batchResultsAdapter: BatchResultsAdapter
    private lateinit var criteriaManager: BatchCriteriaManager
    private lateinit var reportsManager: ReportsManager
    private lateinit var omrModelManager: OMRModelManager
    
    private val selectedImages = mutableListOf<Uri>()
    private val radioButtons = mutableListOf<RadioButton>()
    private var currentQuestions = 5
    private var currentChoices = 4
    
    // Переменные для фотографирования
    private var currentWorkNumber = 1
    private var photoUri: Uri? = null
    private val photoWorkNames = mutableMapOf<Uri, String>() // Связываем URI с именами работ
    
    private val selectImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris?.let { uriList ->
            if (uriList.isNotEmpty()) {
                selectedImages.clear() // Очищаем предыдущий выбор
                selectedImages.addAll(uriList)
                updateSelectedCount()
                updateProcessButton()
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uri ->
                showPhotoDialog(uri)
            }
        } else {
            Toast.makeText(this, "Ошибка при фотографировании", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch)

        // Инициализация OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("BatchActivity", "Ошибка инициализации OpenCV")
        }

        // Настройка полноэкранного режима
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        initViews()
        initManagers()
        setupRecyclerView()
        setupListeners()
        createAnswersGrid()
    }

    private fun initViews() {
        etQuestions = findViewById(R.id.et_questions)
        etChoices = findViewById(R.id.et_choices)
        layoutCorrectAnswers = findViewById(R.id.layout_correct_answers)
        btnSelectImages = findViewById(R.id.btn_select_images)
        btnCamera = findViewById(R.id.btn_camera)
        btnProcess = findViewById(R.id.btn_process)
        btnReset = findViewById(R.id.btn_reset)
        btnSaveCriteria = findViewById(R.id.btn_save_criteria)
        btnLoadCriteria = findViewById(R.id.btn_load_criteria)
        btnApplyCriteria = findViewById(R.id.btn_apply_criteria)
        tvSelectedCount = findViewById(R.id.tv_selected_count)
        rvResults = findViewById(R.id.rv_results)
        btnAddAllToReport = findViewById(R.id.btn_add_all_to_report)
        
        // Инициализация прогресс-бара
        cardProgress = findViewById(R.id.card_progress)
        tvProgressStatus = findViewById(R.id.tv_progress_status)
        progressBar = findViewById(R.id.progress_bar)
        tvProgressCount = findViewById(R.id.tv_progress_count)
    }

    private fun initManagers() {
        criteriaManager = BatchCriteriaManager(this)
        reportsManager = ReportsManager(this)
        omrModelManager = OMRModelManager(this)
        
        // Проверка готовности ML модели
        Thread {
            try {
                if (omrModelManager.isModelReady()) {
                    Log.d("BatchActivity", "✅ ML модель готова к использованию")
                } else {
                    Log.w("BatchActivity", "⚠️ ML модель не готова")
                }
            } catch (e: Exception) {
                Log.e("BatchActivity", "❌ Ошибка проверки ML модели: ${e.message}")
            }
        }.start()
    }

    private fun setupRecyclerView() {
        batchResultsAdapter = BatchResultsAdapter(mutableListOf()) { result ->
            showResultDetails(result)
        }
        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = batchResultsAdapter
        
        // Оптимизация производительности
        rvResults.setHasFixedSize(true)
        rvResults.itemAnimator = null // Отключаем анимации для лучшей производительности
    }

    private fun setupListeners() {
        btnSelectImages.setOnClickListener {
            checkPermissionAndSelectImages()
        }

        btnCamera.setOnClickListener {
            checkCameraPermissionAndStart()
        }

        btnProcess.setOnClickListener {
            processImages()
        }

        btnReset.setOnClickListener {
            resetAll()
        }

        btnSaveCriteria.setOnClickListener {
            showSaveCriteriaDialog()
        }

        btnLoadCriteria.setOnClickListener {
            showLoadCriteriaDialog()
        }

        btnApplyCriteria.setOnClickListener {
            applyCurrentCriteria()
        }

        btnAddAllToReport.setOnClickListener {
            addAllResultsToReport()
        }

        // Ограничения на ввод только цифр
        etQuestions.filters = arrayOf(android.text.InputFilter { source, start, end, dest, dstart, dend ->
            val filtered = source.filter { it.isDigit() }
            if (filtered.length != source.length) {
                Toast.makeText(this@BatchActivity, "Можно вводить только цифры", Toast.LENGTH_SHORT).show()
            }
            filtered
        })

        etChoices.filters = arrayOf(android.text.InputFilter { source, start, end, dest, dstart, dend ->
            val filtered = source.filter { it.isDigit() }
            if (filtered.length != source.length) {
                Toast.makeText(this@BatchActivity, "Можно вводить только цифры", Toast.LENGTH_SHORT).show()
            }
            filtered
        })

        // Отключение контекстного меню для предотвращения вставки
        etQuestions.isLongClickable = false
        etChoices.isLongClickable = false

        // Слушатели изменения параметров сетки
        etQuestions.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Дополнительная проверка на вставку некорректных символов
                val text = s.toString()
                val filteredText = text.filter { it.isDigit() }
                if (text != filteredText) {
                    etQuestions.setText(filteredText)
                    etQuestions.setSelection(filteredText.length)
                    Toast.makeText(this@BatchActivity, "Удалены некорректные символы", Toast.LENGTH_SHORT).show()
                    return
                }
                val inputText = s.toString()
                if (inputText.isNotEmpty()) {
                    val newQuestions = inputText.toIntOrNull() ?: 5
                    
                    // Применяем ограничения
                    val limitedQuestions = when {
                        newQuestions < 1 -> {
                            Toast.makeText(this@BatchActivity, "Минимум 1 вопрос", Toast.LENGTH_SHORT).show()
                            1
                        }
                        newQuestions > 35 -> {
                            Toast.makeText(this@BatchActivity, "Максимум 35 вопросов", Toast.LENGTH_SHORT).show()
                            35
                        }
                        else -> newQuestions
                    }
                    
                    // Обновляем текст, если значение было ограничено
                    if (limitedQuestions != newQuestions) {
                        etQuestions.setText(limitedQuestions.toString())
                        etQuestions.setSelection(etQuestions.text.length)
                    }
                    
                    if (limitedQuestions != currentQuestions) {
                        currentQuestions = limitedQuestions
                        createAnswersGrid()
                    }
                }
            }
        })

        etChoices.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Дополнительная проверка на вставку некорректных символов
                val text = s.toString()
                val filteredText = text.filter { it.isDigit() }
                if (text != filteredText) {
                    etChoices.setText(filteredText)
                    etChoices.setSelection(filteredText.length)
                    Toast.makeText(this@BatchActivity, "Удалены некорректные символы", Toast.LENGTH_SHORT).show()
                    return
                }
                val inputText = s.toString()
                if (inputText.isNotEmpty()) {
                    val newChoices = inputText.toIntOrNull() ?: 4
                    
                    // Применяем ограничения
                    val limitedChoices = when {
                        newChoices < 1 -> {
                            Toast.makeText(this@BatchActivity, "Минимум 1 вариант ответа", Toast.LENGTH_SHORT).show()
                            1
                        }
                        newChoices > 9 -> {
                            Toast.makeText(this@BatchActivity, "Максимум 9 вариантов ответа", Toast.LENGTH_SHORT).show()
                            9
                        }
                        else -> newChoices
                    }
                    
                    // Обновляем текст, если значение было ограничено
                    if (limitedChoices != newChoices) {
                        etChoices.setText(limitedChoices.toString())
                        etChoices.setSelection(etChoices.text.length)
                    }
                    
                    if (limitedChoices != currentChoices) {
                        currentChoices = limitedChoices
                        createAnswersGrid()
                    }
                }
            }
        })
    }

    private fun createAnswersGrid() {
        Log.d("BatchActivity", "📐 Начинаем создание сетки ответов...")
        layoutCorrectAnswers.removeAllViews()
        radioButtons.clear()

        // Рассчитываем оптимальную высоту контейнера в зависимости от количества вопросов
        val baseHeight = 200 // Минимальная высота
        val heightPerRow = 80 // Увеличили высоту на строку для больших ячеек
        val calculatedHeight = baseHeight + (currentQuestions * heightPerRow)
        val containerHeight = calculatedHeight // Убрали ограничение максимальной высоты

        Log.d("BatchActivity", "📐 Создание сетки: вопросы=$currentQuestions, варианты=$currentChoices, высота=$containerHeight")

        // Создаем контейнер с динамической высотой
        val gridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                containerHeight
            )
            background = ContextCompat.getDrawable(this@BatchActivity, R.drawable.edit_text_background)
            setPadding(12, 12, 12, 12)
        }

        // Создаем строки
        for (row in 0 until currentQuestions) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0
                ).apply {
                    weight = 1f // Равномерное распределение по высоте
                }
            }

            // Создаем ячейки в строке
            for (col in 0 until currentChoices) {
                val cellLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ).apply {
                        weight = 1f // Равномерное распределение по ширине
                        val margin = if (currentChoices <= 5) 4 else 2 // Увеличили отступы
                        setMargins(margin, margin, margin, margin)
                    }
                    background = ContextCompat.getDrawable(this@BatchActivity, R.drawable.edit_text_background)
                    val padding = if (currentChoices <= 5) 8 else 4 // Увеличили отступы
                    setPadding(padding, padding, padding, padding)
                }

                // Нумерация ячейки (1.1, 1.2, 1.3...)
                val cellNumber = TextView(this).apply {
                    text = "${row + 1}.${col + 1}"
                    textSize = if (currentChoices <= 5) 18f else 16f // Увеличили размер текста
                    setTextColor(ContextCompat.getColor(this@BatchActivity, R.color.text_inverse))
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                cellLayout.addView(cellNumber)

                // Радио кнопка (скрытая, но функциональная)
                val radioButton = RadioButton(this).apply {
                    text = ""
                    id = View.generateViewId()
                    visibility = View.INVISIBLE // Скрываем, но оставляем функциональной
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                cellLayout.addView(radioButton)
                radioButtons.add(radioButton)

                                    // Обработчик клика на всю ячейку
                    cellLayout.setOnClickListener {
                        // Снимаем выделение со всех радио кнопок в этой строке
                        for (i in 0 until rowLayout.childCount) {
                            val child = rowLayout.getChildAt(i)
                            if (child is LinearLayout) {
                                val radio = child.getChildAt(1) // Радио кнопка - второй элемент
                                if (radio is RadioButton) {
                                    radio.isChecked = false
                                    child.background = ContextCompat.getDrawable(this@BatchActivity, R.drawable.edit_text_background)
                                }
                            }
                        }
                        // Выделяем выбранную ячейку
                        radioButton.isChecked = true
                        cellLayout.background = ContextCompat.getDrawable(this@BatchActivity, R.drawable.cell_selected_background)
                    }

                rowLayout.addView(cellLayout)
            }

            gridContainer.addView(rowLayout)
        }

        layoutCorrectAnswers.addView(gridContainer)
        Log.d("BatchActivity", "📐 Сетка создана, добавлено ${radioButtons.size} радио-кнопок")
    }

    private fun checkPermissionAndSelectImages() {
        val permissions = mutableListOf<String>()
        
        // Для Android 13+ (API 33+) используем READ_MEDIA_IMAGES
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Для старых версий Android используем READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            selectImages()
        }
    }

    private fun selectImages() {
        selectImagesLauncher.launch("image/*")
    }

    private fun checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showCameraSetupDialog()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun updateSelectedCount() {
        val count = selectedImages.size
        val text = when {
            count == 0 -> "Выбрано: 0 файлов"
            count == 1 -> "Выбрано: 1 файл"
            count < 5 -> "Выбрано: $count файла"
            else -> "Выбрано: $count файлов"
        }
        tvSelectedCount.text = text
    }

    private fun updateProcessButton() {
        btnProcess.isEnabled = selectedImages.isNotEmpty() && hasValidCriteria()
    }

    private fun hasValidCriteria(): Boolean {
        val correctAnswers = getCorrectAnswers()
        return correctAnswers.size == currentQuestions && correctAnswers.all { it >= 0 }
    }

    private fun getCorrectAnswers(): List<Int> {
        val answers = mutableListOf<Int>()
        Log.d("BatchActivity", "📋 Получаем правильные ответы: вопросы=$currentQuestions, варианты=$currentChoices, radioButtons.size=${radioButtons.size}")
        
        for (i in 0 until currentQuestions) {
            val questionStartIndex = i * currentChoices
            var selectedAnswer = -1
            for (j in 0 until currentChoices) {
                val radioButtonIndex = questionStartIndex + j
                if (radioButtonIndex < radioButtons.size) {
                    val radioButton = radioButtons[radioButtonIndex]
                    if (radioButton.isChecked) {
                        selectedAnswer = j
                        Log.d("BatchActivity", "📋 Вопрос ${i + 1}: выбран вариант ${j + 1}")
                        break
                    }
                }
            }
            answers.add(selectedAnswer)
            if (selectedAnswer == -1) {
                Log.d("BatchActivity", "📋 Вопрос ${i + 1}: не выбран")
            }
        }
        
        Log.d("BatchActivity", "📋 Полученные ответы: $answers")
        return answers
    }

    private fun processImages() {
        if (selectedImages.isEmpty()) {
            Toast.makeText(this, "Выберите изображения для обработки", Toast.LENGTH_SHORT).show()
            return
        }

        val correctAnswers = getCorrectAnswers()
        if (correctAnswers.size != currentQuestions) {
            Toast.makeText(this, "Укажите правильные ответы для всех вопросов", Toast.LENGTH_SHORT).show()
            return
        }

        // Показываем прогресс-бар
        showProgressBar(selectedImages.size)
        
        btnProcess.isEnabled = false
        btnProcess.text = "Обработка..."
        btnAddAllToReport.isEnabled = false
        btnAddAllToReport.text = "Добавить все в отчет"

        Thread {
            try {
                var processedCount = 0
                
                for (uri in selectedImages) {
                    try {
                        val bitmap = getBitmapFromUri(uri)
                        if (bitmap != null) {
                            val result = processImage(bitmap, correctAnswers, getFileName(uri))
                            processedCount++
                            
                            runOnUiThread {
                                Log.d("BatchActivity", "📝 Добавляем результат: ${result.filename}, правильных: ${result.correctCount}/${result.totalQuestions}")
                                batchResultsAdapter.addResult(result)
                                updateProgress(processedCount, selectedImages.size)
                                
                                // Принудительно обновляем адаптер
                                batchResultsAdapter.notifyDataSetChanged()
                                
                                // Принудительно обновляем layout
                                rvResults.requestLayout()
                                
                                // Прокручиваем к последнему элементу
                                rvResults.smoothScrollToPosition(batchResultsAdapter.itemCount - 1)
                                
                                Log.d("BatchActivity", "📊 Всего результатов в адаптере: ${batchResultsAdapter.itemCount}")
                                Log.d("BatchActivity", "📏 Высота RecyclerView: ${rvResults.height}")
                            }
                        } else {
                            processedCount++
                            runOnUiThread {
                                updateProgress(processedCount, selectedImages.size)
                                
                                // Создаем результат с ошибкой загрузки
                                val errorResult = BatchResult(
                                    id = UUID.randomUUID().toString(),
                                    filename = getFileName(uri),
                                    originalImage = null,
                                    processedImage = null,
                                    correctCount = 0,
                                    totalQuestions = currentQuestions,
                                    percentage = 0.0,
                                    grade = 0,
                                    errors = listOf(BatchResult.ErrorDetail(1, 0, 1)),
                                    correctAnswers = correctAnswers,
                                    selectedAnswers = List(currentQuestions) { 0 }
                                )
                                
                                Log.d("BatchActivity", "❌ Ошибка загрузки изображения: ${getFileName(uri)}")
                                batchResultsAdapter.addResult(errorResult)
                                Toast.makeText(this@BatchActivity, "Не удалось загрузить изображение: ${getFileName(uri)}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        processedCount++
                        runOnUiThread {
                            updateProgress(processedCount, selectedImages.size)
                            Toast.makeText(this@BatchActivity, "Ошибка обработки изображения: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                
                runOnUiThread {
                    hideProgressBar()
                    btnProcess.isEnabled = true
                    btnProcess.text = "Начать обработку"
                    btnAddAllToReport.isEnabled = true
                    Toast.makeText(this, "Обработка завершена", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    hideProgressBar()
                    btnProcess.isEnabled = true
                    btnProcess.text = "Начать обработку"
                    Toast.makeText(this, "Критическая ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun processImage(bitmap: Bitmap, correctAnswers: List<Int>, filename: String): BatchResult {
        try {
            // Используем ImageProcessor для обработки изображения
            val imageProcessor = ImageProcessor()
            
            // Устанавливаем ML модель
            imageProcessor.setMLModel(omrModelManager)
            
            // Используем ML для получения результатов (с OpenCV для поиска контуров)
            val omrResult = imageProcessor.processFrameWithML(
                bitmap,
                currentQuestions,
                currentChoices,
                correctAnswers
            )
            
            if (omrResult != null) {
                // Получаем реальные результаты из OMRResult
                val detectedAnswers = omrResult.selectedAnswers
                
                // Подсчитываем результаты
                var correctCount = 0
                val errors = mutableListOf<BatchResult.ErrorDetail>()
                
                Log.d("BatchActivity", "📊 Обработка результатов: detectedAnswers.size=${detectedAnswers.size}, currentQuestions=$currentQuestions, correctAnswers.size=${correctAnswers.size}")
                
                for (i in 0 until detectedAnswers.size) {
                    if (i < correctAnswers.size && detectedAnswers[i] == correctAnswers[i]) {
                        correctCount++
                    } else {
                        errors.add(
                            BatchResult.ErrorDetail(
                                questionNumber = i + 1,
                                selectedAnswer = detectedAnswers[i] + 1,
                                correctAnswer = if (i < correctAnswers.size) correctAnswers[i] + 1 else 0
                            )
                        )
                    }
                }

                val percentage = (correctCount.toDouble() / detectedAnswers.size) * 100
                val grade = calculateGradeForBatch(percentage.toInt(), detectedAnswers.size)

                Log.d("BatchActivity", "📊 Результаты обработки: $correctCount/${detectedAnswers.size} = ${String.format("%.1f", percentage)}%, оценка: $grade")

                return BatchResult(
                    id = UUID.randomUUID().toString(),
                    filename = filename,
                    originalImage = bitmap,
                    processedImage = bitmap, // Используем оригинальное изображение
                    correctCount = correctCount,
                    totalQuestions = detectedAnswers.size,
                    percentage = percentage,
                    grade = grade,
                    errors = errors,
                    correctAnswers = correctAnswers,
                    selectedAnswers = detectedAnswers.toList(),
                    contourVisualization = omrResult.visualization,
                    gridVisualization = omrResult.gridVisualization
                )
            } else {
                // Если ML не сработал (контур не найден), возвращаем результат с ошибкой
                Log.w("BatchActivity", "⚠️ Контур не найден для файла: $filename")
                return BatchResult(
                    id = UUID.randomUUID().toString(),
                    filename = filename,
                    originalImage = bitmap,
                    processedImage = null,
                    correctCount = 0,
                    totalQuestions = currentQuestions,
                    percentage = 0.0,
                    grade = 0, // Специальная оценка для ошибки
                    errors = listOf(BatchResult.ErrorDetail(1, 0, correctAnswers.firstOrNull() ?: 1)),
                    correctAnswers = correctAnswers,
                    selectedAnswers = List(currentQuestions) { 0 }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Возвращаем результат с ошибкой
            return BatchResult(
                id = UUID.randomUUID().toString(),
                filename = filename,
                originalImage = bitmap,
                processedImage = null,
                correctCount = 0,
                totalQuestions = currentQuestions,
                percentage = 0.0,
                grade = 2,
                errors = listOf(BatchResult.ErrorDetail(1, 0, correctAnswers.firstOrNull() ?: 1)),
                correctAnswers = correctAnswers,
                selectedAnswers = List(currentQuestions) { 0 }
            )
        }
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(uri: Uri): String {
        // Сначала проверяем, есть ли сохраненное имя работы для этого URI
        photoWorkNames[uri]?.let { workName ->
            return workName
        }
        
        // Если нет сохраненного имени, получаем имя файла из MediaStore
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex) ?: "image.jpg"
        } ?: "image.jpg"
    }

    private fun showResultDetails(result: BatchResult) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.batch_result_details_dialog, null)
        
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()

        // Настраиваем элементы диалога
        val ivResultImage = dialogView.findViewById<ImageView>(R.id.iv_result_image)
        val tvImageCaption = dialogView.findViewById<TextView>(R.id.tv_image_caption)
        val tvCorrectCount = dialogView.findViewById<TextView>(R.id.tv_correct_count)
        val tvTotalQuestions = dialogView.findViewById<TextView>(R.id.tv_total_questions)
        val tvPercentage = dialogView.findViewById<TextView>(R.id.tv_percentage)
        val tvGrade = dialogView.findViewById<TextView>(R.id.tv_grade)
        val layoutErrorDetails = dialogView.findViewById<LinearLayout>(R.id.layout_error_details)
        val btnClose = dialogView.findViewById<ImageView>(R.id.btn_close)
        val btnAddToReport = dialogView.findViewById<Button>(R.id.btn_add_to_report)

        // Устанавливаем данные - показываем визуализацию контура и сетки
        when {
            result.contourVisualization != null -> {
                ivResultImage.setImageBitmap(result.contourVisualization)
                tvImageCaption.text = "Найденный контур (зеленая рамка)"
            }
            result.gridVisualization != null -> {
                ivResultImage.setImageBitmap(result.gridVisualization)
                tvImageCaption.text = "Сетка обработки (красные линии)"
            }
            result.processedImage != null -> {
                ivResultImage.setImageBitmap(result.processedImage)
                tvImageCaption.text = "Обработанное изображение"
            }
            result.originalImage != null -> {
                ivResultImage.setImageBitmap(result.originalImage)
                tvImageCaption.text = "Исходное изображение"
            }
            else -> {
                ivResultImage.setImageResource(android.R.drawable.ic_menu_camera)
                tvImageCaption.text = "Изображение недоступно"
            }
        }
        

        
        // Специальная обработка для ошибок
        if (result.grade == 0) {
            tvCorrectCount.text = "ОШИБКА: Контур не найден"
            tvTotalQuestions.text = "Всего: ${result.totalQuestions}"
            tvPercentage.text = "Процент: 0.0%"
            tvGrade.text = "Оценка: -"
        } else {
            tvCorrectCount.text = "Правильно: ${result.correctCount}"
            tvTotalQuestions.text = "Всего: ${result.totalQuestions}"
            tvPercentage.text = "Процент: ${String.format("%.1f", result.percentage)}%"
            tvGrade.text = "Оценка: ${result.grade}"
        }

        // Показываем детали ошибок
        layoutErrorDetails.removeAllViews()
        if (result.grade == 0) {
            val errorText = TextView(this).apply {
                text = "Контур бланка не найден. Проверьте качество изображения и убедитесь, что бланк четко виден на фотографии."
                setTextColor(ContextCompat.getColor(this@BatchActivity, R.color.error))
                textSize = 14f
                typeface = ResourcesCompat.getFont(this@BatchActivity, R.font.krabuler)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
            }
            layoutErrorDetails.addView(errorText)
        } else if (result.errors.isEmpty()) {
            val noErrorsText = TextView(this).apply {
                text = "✅ Ошибок нет"
                setTextColor(ContextCompat.getColor(this@BatchActivity, R.color.success_green))
                textSize = 14f
                typeface = ResourcesCompat.getFont(this@BatchActivity, R.font.krabuler)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
            }
            layoutErrorDetails.addView(noErrorsText)
        } else {
            result.errors.forEach { error ->
                val errorCard = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    background = ContextCompat.getDrawable(this@BatchActivity, R.drawable.edit_text_background)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 4, 0, 4)
                    }
                    setPadding(12, 8, 12, 8)
                }

                val errorIcon = TextView(this).apply {
                    text = "❌"
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 8, 0)
                    }
                }

                val errorText = TextView(this).apply {
                    text = "Вопрос ${error.questionNumber}: выбрано ${error.selectedAnswer}, правильно ${error.correctAnswer}"
                    setTextColor(ContextCompat.getColor(this@BatchActivity, R.color.text_inverse))
                    textSize = 14f
                    typeface = ResourcesCompat.getFont(this@BatchActivity, R.font.krabuler)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                errorCard.addView(errorIcon)
                errorCard.addView(errorText)
                layoutErrorDetails.addView(errorCard)
            }
        }

        // Обработчики кнопок
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        btnAddToReport.setOnClickListener {
            val omrResult = OMRResult(
                selectedAnswers = result.selectedAnswers.toIntArray(),
                grading = IntArray(result.totalQuestions) { i ->
                    if (result.errors.any { it.questionNumber == i + 1 }) 0 else 1
                },
                correctAnswers = result.correctAnswers,
                incorrectQuestions = result.errors.map { error ->
                    mapOf(
                        "question" to error.questionNumber,
                        "selected" to error.selectedAnswer,
                        "correct" to error.correctAnswer
                    )
                }
            )
            reportsManager.addReport(omrResult, "Пакетная обработка: ${result.filename}")
            Toast.makeText(this, "Добавлено в отчет", Toast.LENGTH_SHORT).show()
            btnAddToReport.isEnabled = false
            btnAddToReport.text = "Добавлено"
        }



        dialog.show()
    }

    private fun addAllResultsToReport() {
        val results = batchResultsAdapter.getResults()
        if (results.isEmpty()) {
            Toast.makeText(this, "Нет результатов для добавления в отчет", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("BatchActivity", "📊 Добавляем ${results.size} результатов в отчет")
        Log.d("BatchActivity", "📊 currentQuestions: $currentQuestions")
        Log.d("BatchActivity", "📊 Все результаты:")
        results.forEachIndexed { index, result ->
            Log.d("BatchActivity", "📊   Результат $index: ${result.filename}, totalQuestions: ${result.totalQuestions}, correctCount: ${result.correctCount}")
        }

        var addedCount = 0
        results.forEach { result ->
            if (result.grade > 0) { // Добавляем только успешно обработанные результаты
                // Создаем массив grading: 1 для правильных ответов, 0 для неправильных
                val grading = IntArray(result.selectedAnswers.size) { questionIndex ->
                    if (result.errors.any { it.questionNumber == questionIndex + 1 }) 0 else 1
                }
                
                Log.d("BatchActivity", "📊 Формируем OMRResult для ${result.filename}:")
                Log.d("BatchActivity", "   - totalQuestions: ${result.totalQuestions}")
                Log.d("BatchActivity", "   - selectedAnswers.size: ${result.selectedAnswers.size}")
                Log.d("BatchActivity", "   - selectedAnswers: ${result.selectedAnswers}")
                Log.d("BatchActivity", "   - grading: ${grading.contentToString()}")
                Log.d("BatchActivity", "   - correctAnswers: ${result.correctAnswers}")
                Log.d("BatchActivity", "   - errors: ${result.errors}")
                Log.d("BatchActivity", "   - grading.size: ${grading.size}")
                
                val omrResult = OMRResult(
                    selectedAnswers = result.selectedAnswers.toIntArray(),
                    grading = grading, // Массив правильных/неправильных ответов
                    incorrectQuestions = result.errors.map { mapOf(
                        "question" to it.questionNumber,
                        "selected" to it.selectedAnswer,
                        "correct" to it.correctAnswer
                    ) },
                    correctAnswers = result.correctAnswers
                )
                reportsManager.addReport(omrResult, "Пакетная обработка: ${result.filename}")
                addedCount++
            }
        }

        if (addedCount > 0) {
            Toast.makeText(this, "Добавлено $addedCount результатов в отчет", Toast.LENGTH_SHORT).show()
            btnAddAllToReport.isEnabled = false
            btnAddAllToReport.text = "Добавлено"
        } else {
            Toast.makeText(this, "Нет успешно обработанных результатов для добавления", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSaveCriteriaDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.batch_save_criteria_dialog, null)
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()

        val etCriteriaName = dialogView.findViewById<EditText>(R.id.et_criteria_name)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val name = etCriteriaName.text.toString()
            if (name.isNotEmpty()) {
                val correctAnswers = getCorrectAnswers()
                if (correctAnswers.size == currentQuestions) {
                    val criteria = BatchCriteria(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        questions = currentQuestions,
                        choices = currentChoices,
                        correctAnswers = correctAnswers
                    )
                    criteriaManager.addCriteria(criteria)
                    Toast.makeText(this, "Критерии сохранены", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, "Укажите правильные ответы для всех вопросов", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Введите название критериев", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showLoadCriteriaDialog() {
        val criteriaList = criteriaManager.getCriteriaList()
        Log.d("BatchActivity", "📋 Открываем диалог загрузки критериев, найдено: ${criteriaList.size}")
        if (criteriaList.isEmpty()) {
            Log.d("BatchActivity", "❌ Нет сохраненных критериев")
            Toast.makeText(this, "Нет сохраненных критериев", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.batch_load_criteria_dialog, null)
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()

        val layoutCriteriaList = dialogView.findViewById<LinearLayout>(R.id.layout_criteria_list)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_load)
        val btnDelete = dialogView.findViewById<Button>(R.id.btn_delete_criteria)

        var selectedCriteria: BatchCriteria? = null

        // Создаем список критериев
        Log.d("BatchActivity", "📋 Создаем элементы списка для ${criteriaList.size} критериев")
        criteriaList.forEach { criteria ->
            Log.d("BatchActivity", "📋 Создаем элемент для критериев: ${criteria.name}")
            val criteriaItem = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
                background = ContextCompat.getDrawable(this@BatchActivity, R.drawable.edit_text_background)
                setPadding(12, 12, 12, 12)
            }

            val radioButton = RadioButton(this).apply {
                id = View.generateViewId()
                setTextColor(ContextCompat.getColor(this@BatchActivity, R.color.text_inverse))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 8, 0)
                }
            }

            val criteriaInfo = TextView(this).apply {
                text = "${criteria.name} (${criteria.questions} вопросов, ${criteria.choices} вариантов)"
                setTextColor(ContextCompat.getColor(this@BatchActivity, R.color.text_inverse))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    weight = 1f
                }
            }

            val deleteButton = ImageView(this).apply {
                setImageResource(R.drawable.ic_delete_criteria)
                background = ContextCompat.getDrawable(this@BatchActivity, R.drawable.delete_button_background)
                layoutParams = LinearLayout.LayoutParams(
                    48,
                    48
                ).apply {
                    setMargins(12, 0, 0, 0)
                }
                setOnClickListener {
                    Log.d("BatchActivity", "🗑️ Удаляем критерии через крестик: ${criteria.name}")
                    criteriaManager.deleteCriteria(criteria.id)
                    Toast.makeText(this@BatchActivity, "Критерии удалены", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    // Переоткрываем диалог для обновления списка
                    showLoadCriteriaDialog()
                }
            }

            criteriaItem.addView(radioButton)
            criteriaItem.addView(criteriaInfo)
            criteriaItem.addView(deleteButton)
            Log.d("BatchActivity", "📋 Добавлен элемент в список: ${criteria.name}")

            criteriaItem.setOnClickListener {
                // Снимаем выделение со всех радио кнопок
                for (i in 0 until layoutCriteriaList.childCount) {
                    val child = layoutCriteriaList.getChildAt(i)
                    if (child is LinearLayout) {
                        val radio = child.getChildAt(0)
                        if (radio is RadioButton) {
                            radio.isChecked = false
                        }
                    }
                }
                radioButton.isChecked = true
                selectedCriteria = criteria
                Log.d("BatchActivity", "📋 Выбраны критерии: ${criteria.name}")
                
                // Сразу загружаем критерии
                loadCriteria(criteria)
                dialog.dismiss()
            }

            radioButton.setOnClickListener {
                // Снимаем выделение со всех радио кнопок
                for (i in 0 until layoutCriteriaList.childCount) {
                    val child = layoutCriteriaList.getChildAt(i)
                    if (child is LinearLayout) {
                        val radio = child.getChildAt(0)
                        if (radio is RadioButton) {
                            radio.isChecked = false
                        }
                    }
                }
                radioButton.isChecked = true
                selectedCriteria = criteria
                Log.d("BatchActivity", "📋 Выбраны критерии (через RadioButton): ${criteria.name}")
                
                // Сразу загружаем критерии
                loadCriteria(criteria)
                dialog.dismiss()
            }

            layoutCriteriaList.addView(criteriaItem)
            Log.d("BatchActivity", "📋 Элемент добавлен в layout, всего элементов: ${layoutCriteriaList.childCount}")
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        // Удаление теперь происходит через крестики рядом с каждым критерием

        // Загрузка теперь происходит по клику на критерии

        dialog.show()
    }

    private fun loadCriteria(criteria: BatchCriteria) {
        Log.d("BatchActivity", "📥 Загружаем критерии: ${criteria.name}")
        Log.d("BatchActivity", "📥 Вопросов: ${criteria.questions}, вариантов: ${criteria.choices}")
        Log.d("BatchActivity", "📥 Правильные ответы: ${criteria.correctAnswers}")
        
        currentQuestions = criteria.questions
        currentChoices = criteria.choices
        
        etQuestions.setText(currentQuestions.toString())
        etChoices.setText(currentChoices.toString())
        
        Log.d("BatchActivity", "📥 Создаем сетку ответов...")
        createAnswersGrid()
        
        // Ждем немного, чтобы сетка создалась
        layoutCorrectAnswers.post {
            Log.d("BatchActivity", "📥 Сетка создана, устанавливаем ответы...")
            Log.d("BatchActivity", "📥 Размер radioButtons: ${radioButtons.size}")
            
            // Устанавливаем правильные ответы
            criteria.correctAnswers.forEachIndexed { index, answer ->
                if (index < currentQuestions && answer >= 0 && answer < currentChoices) {
                    val radioButtonIndex = index * currentChoices + answer
                    if (radioButtonIndex < radioButtons.size) {
                        radioButtons[radioButtonIndex].isChecked = true
                        
                        // Обновляем фон ячейки
                        val rowLayout = layoutCorrectAnswers.getChildAt(0) as? LinearLayout
                        if (rowLayout != null && index < rowLayout.childCount) {
                            val row = rowLayout.getChildAt(index) as? LinearLayout
                            if (row != null && answer < row.childCount) {
                                val cellLayout = row.getChildAt(answer) as? LinearLayout
                                if (cellLayout != null) {
                                    cellLayout.background = ContextCompat.getDrawable(this@BatchActivity, R.drawable.cell_selected_background)
                                    Log.d("BatchActivity", "📥 Обновлен фон ячейки для вопроса ${index + 1}, варианта ${answer + 1}")
                                }
                            }
                        }
                        
                        Log.d("BatchActivity", "📥 Установлен ответ для вопроса ${index + 1}: вариант ${answer + 1} (индекс: $radioButtonIndex)")
                    } else {
                        Log.e("BatchActivity", "❌ Индекс $radioButtonIndex выходит за пределы radioButtons (размер: ${radioButtons.size})")
                    }
                } else {
                    Log.e("BatchActivity", "❌ Некорректные данные: index=$index, answer=$answer, questions=$currentQuestions, choices=$currentChoices")
                }
            }
            
            Log.d("BatchActivity", "📥 Все ответы установлены")
        }
        
        Toast.makeText(this, "Критерии загружены", Toast.LENGTH_SHORT).show()
    }

    private fun applyCurrentCriteria() {
        val correctAnswers = getCorrectAnswers()
        if (correctAnswers.size == currentQuestions) {
            Toast.makeText(this, "Критерии применены", Toast.LENGTH_SHORT).show()
            updateProcessButton()
        } else {
            Toast.makeText(this, "Укажите правильные ответы для всех вопросов", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetAll() {
        selectedImages.clear()
        batchResultsAdapter.clearResults()
        updateSelectedCount()
        updateProcessButton()
        
        // Сбрасываем кнопку добавления в отчет
        btnAddAllToReport.isEnabled = false
        btnAddAllToReport.text = "Добавить все в отчет"
        
        // Сбрасываем номер работы и очищаем карту имен
        currentWorkNumber = 1
        photoWorkNames.clear()
        
        createAnswersGrid()
    }

    private fun calculateGradeForBatch(percentage: Int, totalQuestions: Int): Int {
        val currentCriteria = reportsManager.getCurrentCriteria()
        if (currentCriteria == null) {
            Log.w("BatchActivity", "⚠️ Критерии не найдены, используем дефолтную оценку 2")
            return 2
        }
        
        val result = when (currentCriteria.type) {
            ReportsManager.CriteriaType.PERCENTAGE -> {
                val percentageDouble = percentage.toDouble()
                currentCriteria.criteria.entries.find { (_, range) ->
                    percentageDouble in range
                }?.key ?: 2
            }
            ReportsManager.CriteriaType.POINTS -> {
                val correctCount = (percentage * totalQuestions / 100.0).toInt()
                val points = correctCount.toDouble()
                currentCriteria.criteria.entries.find { (_, range) ->
                    points in range
                }?.key ?: 2
            }
        }
        
        Log.d("BatchActivity", "🎯 Расчет оценки: процент=$percentage%, тип=${currentCriteria.type}, результат=$result")
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    selectImages()
                } else {
                    Toast.makeText(this, "Разрешение необходимо для выбора изображений", Toast.LENGTH_SHORT).show()
                }
            }
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    showCameraSetupDialog()
                } else {
                    Toast.makeText(this, "Разрешение на камеру необходимо для фотографирования", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showProgressBar(totalImages: Int) {
        cardProgress.visibility = View.VISIBLE
        progressBar.max = totalImages
        progressBar.progress = 0
        tvProgressCount.text = "0 из $totalImages"
        tvProgressStatus.text = "Обработка изображений..."
    }

    private fun updateProgress(current: Int, total: Int) {
        progressBar.progress = current
        tvProgressCount.text = "$current из $total"
        
        // Обновляем статус в зависимости от прогресса
        val percentage = (current * 100) / total
        when {
            percentage < 25 -> tvProgressStatus.text = "Обработка изображений..."
            percentage < 50 -> tvProgressStatus.text = "Анализ данных..."
            percentage < 75 -> tvProgressStatus.text = "Проверка результатов..."
            else -> tvProgressStatus.text = "Завершение обработки..."
        }
    }

    private fun hideProgressBar() {
        cardProgress.visibility = View.GONE
    }
    
    private fun showVisualizationDialog(result: BatchResult) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.visualization_dialog, null)
        
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()
            
        val ivContourImage = dialogView.findViewById<ImageView>(R.id.iv_contour_image)
        val ivGridImage = dialogView.findViewById<ImageView>(R.id.iv_grid_image)
        val btnClose = dialogView.findViewById<Button>(R.id.btn_close)
        
        // Показываем визуализацию контура
        if (result.contourVisualization != null) {
            ivContourImage.setImageBitmap(result.contourVisualization)
        } else {
            ivContourImage.setImageResource(android.R.drawable.ic_menu_camera)
        }
        
        // Показываем визуализацию сетки
        if (result.gridVisualization != null) {
            ivGridImage.setImageBitmap(result.gridVisualization)
        } else {
            ivGridImage.setImageResource(android.R.drawable.ic_menu_camera)
        }
        
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    // Методы для работы с камерой
    private fun showCameraSetupDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.batch_camera_dialog, null)
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()

        val etStartNumber = dialogView.findViewById<EditText>(R.id.et_start_number)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_camera)
        val btnStart = dialogView.findViewById<Button>(R.id.btn_start_camera)

        // Устанавливаем текущий номер работы
        etStartNumber.setText(currentWorkNumber.toString())

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnStart.setOnClickListener {
            val startNumber = etStartNumber.text.toString().toIntOrNull() ?: 1
            currentWorkNumber = startNumber
            dialog.dismiss()
            takePhoto()
        }

        dialog.show()
    }

    private fun takePhoto() {
        try {
            val photoFile = createImageFile()
            photoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            cameraLauncher.launch(photoUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка при создании файла", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "BATCH_${timeStamp}_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    private fun showPhotoDialog(uri: Uri) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.batch_photo_dialog, null)
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()

        val tvWorkNumber = dialogView.findViewById<TextView>(R.id.tv_work_number)
        val ivPhoto = dialogView.findViewById<ImageView>(R.id.iv_photo)
        val btnRetake = dialogView.findViewById<Button>(R.id.btn_retake)
        val btnAdd = dialogView.findViewById<Button>(R.id.btn_add_photo)
        val btnFinish = dialogView.findViewById<Button>(R.id.btn_finish_camera)

        // Показываем номер работы
        tvWorkNumber.text = "Работа $currentWorkNumber"

        // Загружаем фотографию
        try {
            val bitmap = getBitmapFromUri(uri)
            if (bitmap != null) {
                ivPhoto.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
        }

        btnRetake.setOnClickListener {
            dialog.dismiss()
            takePhoto()
        }

        btnAdd.setOnClickListener {
            // Добавляем фотографию в список
            selectedImages.add(uri)
            
            // Сохраняем имя работы для этого URI
            val workName = "Работа $currentWorkNumber"
            photoWorkNames[uri] = workName
            
            updateSelectedCount()
            updateProcessButton()
            
            // Увеличиваем номер работы
            currentWorkNumber++
            
            Toast.makeText(this, "$workName добавлена", Toast.LENGTH_SHORT).show()
            
            // Продолжаем фотографирование
            dialog.dismiss()
            takePhoto()
        }

        btnFinish.setOnClickListener {
            // Добавляем последнюю фотографию
            selectedImages.add(uri)
            
            // Сохраняем имя работы для этого URI
            val workName = "Работа $currentWorkNumber"
            photoWorkNames[uri] = workName
            
            updateSelectedCount()
            updateProcessButton()
            
            Toast.makeText(this, "Фотографирование завершено. $workName добавлена", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1002
    }
} 