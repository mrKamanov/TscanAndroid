package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
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
import com.example.myapplication.ml.FixedAnswerCallback
import com.example.myapplication.models.BatchResult
import com.example.myapplication.models.OMRResult
import com.example.myapplication.models.FixedAnswer
import com.example.myapplication.batch.BatchImageProcessor
import com.example.myapplication.batch.BatchFixedAnswerProcessor
import com.example.myapplication.reports.ReportsManager
import com.example.myapplication.utils.SettingsHelper
import org.opencv.android.OpenCVLoader
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.os.Environment
import androidx.core.content.FileProvider

class BatchActivity : AppCompatActivity(), FixedAnswerCallback {
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
    private var workCounter = 0 // Счетчик для нумерации работ по порядку загрузки
    
    // Отслеживание занятых номеров работ
    private val usedWorkNumbers = mutableSetOf<Int>()
    private var nextAvailableWorkNumber = 1
    
    // Отслеживание всех обработанных файлов (по именам)
    private val processedFileNames = mutableSetOf<String>()
    
    // Обработчик исправлений для пакетной обработки
    private lateinit var fixedAnswerProcessor: BatchFixedAnswerProcessor
    
    // Переменные для обработки исправлений (устаревшие - используем fixedAnswerProcessor)
    private val pendingFixedAnswers = mutableMapOf<String, MutableList<FixedAnswer>>() // filename -> список исправлений
    private val fixedAnswerDecisions = mutableMapOf<String, MutableMap<Int, Boolean>>() // filename -> question -> считать ошибкой
    
    private val selectImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris?.let { uriList ->
            if (uriList.isNotEmpty()) {
                // Проверяем на дубликаты по имени файла
                val newUris = uriList.filter { uri ->
                    val fileName = getFileNameFromUri(uri)
                    // Проверяем, нет ли уже файла с таким именем в текущей сессии
                    val notInCurrentSession = !selectedImages.any { existingUri ->
                        getFileNameFromUri(existingUri) == fileName
                    }
                    // И проверяем, не был ли файл уже обработан ранее
                    val notProcessedBefore = !processedFileNames.contains(fileName)
                    
                    notInCurrentSession && notProcessedBefore
                }
                
                if (newUris.isEmpty()) {
                    Toast.makeText(this, "Все файлы уже загружены", Toast.LENGTH_SHORT).show()
                    return@let
                }
                
                // Добавляем только новые файлы
                newUris.forEach { uri ->
                    selectedImages.add(uri)
                    
                    // Генерируем уникальное имя для файла
                    val fileName = getFileNameFromUri(uri)
                    val workName = "Файл: $fileName"
                    photoWorkNames[uri] = workName
                }
                
                updateSelectedCount()
                updateProcessButton()
                
                val skippedCount = uriList.size - newUris.size
                val message = if (skippedCount > 0) {
                    "Загружено ${newUris.size} новых файлов, ${skippedCount} уже загружены или обработаны ранее"
                } else {
                    "Загружено ${newUris.size} файлов"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
        }
        // Убираем уведомление об ошибке при отмене фотографирования
        // Пользователь сам отменил через крестик - это нормально
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
        OMRModelManager.setGlobalInstance(omrModelManager)
        
        // Инициализируем обработчик исправлений для пакетной обработки
        fixedAnswerProcessor = BatchFixedAnswerProcessor(this)
        
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
        batchResultsAdapter = BatchResultsAdapter(mutableListOf(), fixedAnswerProcessor) { result ->
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
            showSmartResetDialog()
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
                            // Получаем номер работы из photoWorkNames
                            val workName = photoWorkNames[uri]
                            val workNumber = if (workName != null && workName.matches(Regex("\\d+"))) {
                                // Для фотографий используем сохраненный номер
                                workName.toInt()
                            } else {
                                // Для файлов из галереи используем следующий доступный номер
                                getNextAvailableWorkNumber()
                            }
                            
                            // Добавляем номер в использованные (если еще не добавлен)
                            if (!usedWorkNumbers.contains(workNumber)) {
                                usedWorkNumbers.add(workNumber)
                                updateNextAvailableWorkNumber()
                            }
                            
                            val result = processImage(bitmap, correctAnswers, getFileNameFromUri(uri), workNumber)
                            processedCount++
                            
                            // Добавляем имя файла в список обработанных
                            processedFileNames.add(getFileNameFromUri(uri))
                            
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
                                    workNumber = ++workCounter, // Увеличиваем счетчик и присваиваем номер
                                    originalImage = null,
                                    processedImage = null,
                                    correctCount = 0,
                                    totalQuestions = currentQuestions,
                                    percentage = 0.0,
                                    grade = 0,
                                    errors = listOf(BatchResult.ErrorDetail(1, 0, 1)),
                                    correctAnswers = correctAnswers,
                                    selectedAnswers = List(currentQuestions) { 0 },
                                    isAddedToReport = false // По умолчанию не добавлена в отчет
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
                    
                    // Автоматически сбрасываем загруженные работы после успешной обработки
                    // чтобы избежать их повторной проверки
                    // НО сохраняем usedWorkNumbers и nextAvailableWorkNumber
                    // чтобы избежать дубликатов при повторной загрузке тех же файлов
                    selectedImages.clear()
                    photoWorkNames.clear()
                    updateSelectedCount()
                    updateProcessButton()
                    
                    Toast.makeText(this, "Обработка завершена. Загруженные работы сброшены", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    hideProgressBar()
                    btnProcess.isEnabled = true
                    btnProcess.text = "Начать обработку"
                    Toast.makeText(this, "Критическая ошибка: ${e.message}. Загруженные работы сохранены", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun processImage(bitmap: Bitmap, correctAnswers: List<Int>, filename: String, workNumber: Int): BatchResult {
        try {
            // Используем BatchImageProcessor для обработки изображения
            val batchImageProcessor = BatchImageProcessor()
            
            // Устанавливаем ML модель
            batchImageProcessor.setMLModel(omrModelManager)
            
            // Создаем callback для обработки исправлений через fixedAnswerProcessor
            val tempCallback = fixedAnswerProcessor.createFixedAnswerCallback(filename)
            
            // Используем ML для получения результатов (с OpenCV для поиска контуров)
            val omrResult = batchImageProcessor.processFrameWithML(
                bitmap,
                currentQuestions,
                currentChoices,
                correctAnswers,
                null, // onProgressUpdate
                tempCallback // FixedAnswerCallback для этого файла
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
                    workNumber = workNumber, // Используем переданный номер работы
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
                    gridVisualization = omrResult.gridVisualization,
                    fixedAnswers = omrResult.fixedAnswers, // Добавляем исправления
                    isAddedToReport = false // По умолчанию не добавлена в отчет
                )
            } else {
                // Если ML не сработал (контур не найден), возвращаем результат с ошибкой
                Log.w("BatchActivity", "⚠️ Контур не найден для файла: $filename")
                return BatchResult(
                    id = UUID.randomUUID().toString(),
                    filename = filename,
                    workNumber = workNumber, // Используем переданный номер работы
                    originalImage = bitmap,
                    processedImage = null,
                    correctCount = 0,
                    totalQuestions = currentQuestions,
                    percentage = 0.0,
                    grade = 0, // Специальная оценка для ошибки
                    errors = listOf(BatchResult.ErrorDetail(1, 0, correctAnswers.firstOrNull() ?: 1)),
                    correctAnswers = correctAnswers,
                    selectedAnswers = List(currentQuestions) { 0 },
                    isAddedToReport = false // По умолчанию не добавлена в отчет
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Возвращаем результат с ошибкой
            return BatchResult(
                id = UUID.randomUUID().toString(),
                filename = UUID.randomUUID().toString(),
                workNumber = workNumber, // Используем переданный номер работы
                originalImage = bitmap,
                processedImage = null,
                correctCount = 0,
                totalQuestions = currentQuestions,
                percentage = 0.0,
                grade = 2,
                errors = listOf(BatchResult.ErrorDetail(1, 0, correctAnswers.firstOrNull() ?: 0)),
                correctAnswers = correctAnswers,
                selectedAnswers = List(currentQuestions) { 0 },
                isAddedToReport = false // По умолчанию не добавлена в отчет
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

        // Убираем белые углы
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Настраиваем элементы диалога
        val ivResultImage = dialogView.findViewById<ImageView>(R.id.iv_result_image)
        val tvImageCaption = dialogView.findViewById<TextView>(R.id.tv_image_caption)
        val tvCorrectCount = dialogView.findViewById<TextView>(R.id.tv_correct_count)
        val tvTotalQuestions = dialogView.findViewById<TextView>(R.id.tv_total_questions)
        val tvPercentage = dialogView.findViewById<TextView>(R.id.tv_percentage)
        val tvGrade = dialogView.findViewById<TextView>(R.id.tv_grade)
        val layoutErrorDetails = dialogView.findViewById<LinearLayout>(R.id.layout_error_details)
        val btnClose = dialogView.findViewById<ImageView>(R.id.btn_close)
        val btnAddToReport = dialogView.findViewById<MaterialButton>(R.id.btn_add_to_report)
        
        // Элементы для исправлений
        val cardFixedAnswers = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.card_fixed_answers)
        val layoutFixedAnswers = dialogView.findViewById<LinearLayout>(R.id.layout_fixed_answers)

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
            tvGrade.setTextColor(getColor(android.R.color.darker_gray))
        } else {
            tvCorrectCount.text = "Правильно: ${result.correctCount}"
            tvTotalQuestions.text = "Всего: ${result.totalQuestions}"
            tvPercentage.text = "Процент: ${String.format("%.1f", result.percentage)}%"
            tvGrade.text = "Оценка: ${result.grade}"
            tvGrade.setTextColor(getGradeColor(result.grade))
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
        
        // Показываем исправления, если они есть
        val fixedAnswers = fixedAnswerProcessor.getFixedAnswers(result.filename)
        if (fixedAnswers.isNotEmpty()) {
            cardFixedAnswers.visibility = View.VISIBLE
            layoutFixedAnswers.removeAllViews()
            
                            // Создаем ссылку на диалог для обновления UI
                val currentDialog = dialog
                
                fixedAnswers.forEach { fixedAnswer ->
                    // Проверяем, было ли уже принято решение
                    val existingDecision = fixedAnswerProcessor.getFixedAnswerDecision(result.filename, fixedAnswer.questionNumber)
                val fixedAnswerCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ContextCompat.getDrawable(this@BatchActivity, R.drawable.edit_text_background)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 8, 0, 8)
                    }
                    setPadding(16, 12, 16, 12)
                }
                
                // Заголовок исправления
                val headerLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, 8)
                    }
                }
                
                val warningIcon = TextView(this).apply {
                    text = "⚠️"
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 8, 0)
                    }
                }
                
                val fixedAnswerText = TextView(this).apply {
                    text = "Вопрос ${fixedAnswer.questionNumber}, вариант ${fixedAnswer.choiceNumber}"
                    setTextColor(ContextCompat.getColor(this@BatchActivity, R.color.text_secondary))
                    textSize = 14f
                    typeface = ResourcesCompat.getFont(this@BatchActivity, R.font.krabuler)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                
                headerLayout.addView(warningIcon)
                headerLayout.addView(fixedAnswerText)
                
                // Кнопки ДА/НЕТ
                val buttonsLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 8, 0, 0)
                    }
                }
                
                lateinit var btnYes: MaterialButton
                lateinit var btnNo: MaterialButton
                
                btnYes = MaterialButton(this).apply {
                    // Если решение уже принято, показываем его
                    if (existingDecision == true) {
                        text = "Ошибка"
                        isEnabled = false
                        backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@BatchActivity, R.color.error))
                    } else {
                        text = "ДА"
                        isEnabled = true
                        backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@BatchActivity, R.color.error))
                    }
                    
                    setTextColor(ContextCompat.getColor(this@BatchActivity, R.color.text_secondary))
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        setMargins(0, 0, 8, 0)
                    }
                    cornerRadius = 28
                    minHeight = 44
                    maxHeight = 44
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    textSize = 13f
                    isAllCaps = false
                    setOnClickListener {
                        // Проверяем, не было ли уже принято решение
                        if (existingDecision != null) {
                            Toast.makeText(this@BatchActivity, "Решение уже принято для этого исправления", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        
                        // Логика для "ДА" - считаем исправление ошибкой
                        handleFixedAnswerDecision(result, fixedAnswer, true, currentDialog)
                        
                        // Обновляем UI кнопки
                        btnYes.isEnabled = false
                        btnYes.text = "Ошибка"
                        btnYes.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@BatchActivity, R.color.error))
                        btnNo.isEnabled = false
                        btnNo.text = "Отменено"
                        btnNo.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@BatchActivity, R.color.text_secondary))
                    }
                }
                
                btnNo = MaterialButton(this).apply {
                    // Если решение уже принято, показываем его
                    if (existingDecision == false) {
                        text = "Правильно"
                        isEnabled = false
                        backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@BatchActivity, R.color.success_green))
                    } else {
                        text = "НЕТ"
                        isEnabled = true
                        backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@BatchActivity, R.color.success_green))
                    }
                    
                    setTextColor(ContextCompat.getColor(this@BatchActivity, R.color.text_secondary))
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        setMargins(8, 0, 0, 0)
                    }
                    cornerRadius = 28
                    minHeight = 44
                    maxHeight = 44
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    textSize = 13f
                    isAllCaps = false
                    setOnClickListener {
                        // Проверяем, не было ли уже принято решение
                        if (existingDecision != null) {
                            Toast.makeText(this@BatchActivity, "Решение уже принято для этого исправления", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        
                        // Логика для "НЕТ" - оставляем исправление правильным
                        handleFixedAnswerDecision(result, fixedAnswer, false, currentDialog)
                        
                        // Обновляем UI кнопки
                        btnNo.isEnabled = false
                        btnNo.text = "Правильно"
                        btnNo.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@BatchActivity, R.color.success_green))
                        btnYes.isEnabled = false
                        btnYes.text = "Отменено"
                        btnYes.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@BatchActivity, R.color.text_secondary))
                    }
                }
                
                buttonsLayout.addView(btnYes)
                buttonsLayout.addView(btnNo)
                
                fixedAnswerCard.addView(headerLayout)
                fixedAnswerCard.addView(buttonsLayout)
                layoutFixedAnswers.addView(fixedAnswerCard)
            }
        } else {
            cardFixedAnswers.visibility = View.GONE
        }

        // Обработчики кнопок
        btnClose.setOnClickListener {
            // Перед закрытием обновляем все результаты с учетом исправлений
            updateAllResultsWithFixedAnswers()
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
            
            // Устанавливаем флаг добавления в отчет
            val updatedResult = result.copy(isAddedToReport = true)
            val resultIndex = batchResultsAdapter.getResults().indexOfFirst { it.id == result.id }
            if (resultIndex != -1) {
                batchResultsAdapter.updateResult(resultIndex, updatedResult)
            }
            
            Toast.makeText(this, "Добавлено в отчет", Toast.LENGTH_SHORT).show()
            btnAddToReport.isEnabled = false
            btnAddToReport.text = "Добавлено"
        }



                dialog.show()
    }
    
    /**
     * Обрабатывает решение пользователя по исправлению
     */
    private fun handleFixedAnswerDecision(result: BatchResult, fixedAnswer: FixedAnswer, countAsError: Boolean, dialog: AlertDialog) {
        Log.d("BatchActivity", "🔍 Обработка решения по исправлению: вопрос ${fixedAnswer.questionNumber}, считать ошибкой: $countAsError")
        
        // Сохраняем решение пользователя через fixedAnswerProcessor
        fixedAnswerProcessor.handleFixedAnswerDecision(result.filename, fixedAnswer.questionNumber, countAsError)
        
        // Обновляем результат с учетом всех предыдущих изменений
        updateBatchResultAfterFixedAnswer(result, fixedAnswer, countAsError, dialog)
        
        // Обновляем UI в реальном времени
        updateResultUI(result, dialog)
    }
    
    /**
     * Обновляет BatchResult после принятия решения по исправлению
     * Учитывает ВСЕ предыдущие изменения для корректного расчета
     */
    private fun updateBatchResultAfterFixedAnswer(
        result: BatchResult, 
        fixedAnswer: FixedAnswer, 
        countAsError: Boolean, 
        dialog: AlertDialog
    ) {
        // Находим результат в адаптере
        val results = batchResultsAdapter.getResults()
        val resultIndex = results.indexOfFirst { it.id == result.id }
        
        if (resultIndex != -1) {
            // Получаем актуальный результат (с предыдущими изменениями)
            val currentResult = results[resultIndex]
            
            // Получаем ВСЕ исправления для этого файла
            val allFixedAnswers = fixedAnswerProcessor.getFixedAnswers(result.filename)
            
            // Создаем новый список ошибок на основе оригинальных ошибок
            val updatedErrors = currentResult.errors.toMutableList()
            
            // Обрабатываем каждое исправление
            allFixedAnswers.forEach { fa ->
                val decision = fixedAnswerProcessor.getFixedAnswerDecision(result.filename, fa.questionNumber)
                
                if (decision == true) {
                    // Если исправление считается ошибкой
                    val questionIndex = fa.questionNumber - 1
                    val correctAnswer = currentResult.correctAnswers.getOrNull(questionIndex) ?: 0
                    
                    // Проверяем, нет ли уже такой ошибки
                    val existingError = updatedErrors.find { it.questionNumber == fa.questionNumber }
                    if (existingError == null) {
                        val newError = BatchResult.ErrorDetail(
                            questionNumber = fa.questionNumber,
                            selectedAnswer = fa.choiceNumber,
                            correctAnswer = correctAnswer + 1
                        )
                        updatedErrors.add(newError)
                    }
                }
            }
            
            // Пересчитываем статистику на основе ВСЕХ ошибок
            val newCorrectCount = currentResult.totalQuestions - updatedErrors.size
            val newPercentage = (newCorrectCount.toDouble() / currentResult.totalQuestions) * 100
            val newGrade = calculateGradeForBatch(newPercentage.toInt(), currentResult.totalQuestions)
            
            val updatedResult = currentResult.copy(
                correctCount = newCorrectCount,
                percentage = newPercentage,
                grade = newGrade,
                errors = updatedErrors,
                workNumber = currentResult.workNumber,
                isAddedToReport = currentResult.isAddedToReport
            )
            
            // Обновляем результат в адаптере
            batchResultsAdapter.updateResult(resultIndex, updatedResult)
            
            Log.d("BatchActivity", "✅ Результат обновлен с учетом ВСЕХ исправлений: ${updatedResult.correctCount}/${updatedResult.totalQuestions} = ${String.format("%.1f", updatedResult.percentage)}%, оценка: ${updatedResult.grade}")
            Log.d("BatchActivity", "📊 Обработано исправлений: ${allFixedAnswers.size}, ошибок: ${updatedErrors.size}")
        }
    }
    
    /**
     * Обновляет UI результата в реальном времени
     */
    private fun updateResultUI(result: BatchResult, dialog: AlertDialog) {
        // Обновляем статистику в диалоге
        val results = batchResultsAdapter.getResults()
        val resultIndex = results.indexOfFirst { it.id == result.id }
        
        if (resultIndex != -1) {
            val updatedResult = results[resultIndex]
            
            // Обновляем элементы UI в диалоге
            updateDialogUI(updatedResult, dialog)
            
            // Обновляем превью в списке результатов
            batchResultsAdapter.notifyItemChanged(resultIndex)
        }
    }
    
    /**
     * Обновляет элементы UI в диалоге
     */
    private fun updateDialogUI(updatedResult: BatchResult, dialog: AlertDialog) {
        try {
            // Обновляем статистику в диалоге
            val tvCorrectCount = dialog.findViewById<TextView>(R.id.tv_correct_count)
            val tvPercentage = dialog.findViewById<TextView>(R.id.tv_percentage)
            val tvGrade = dialog.findViewById<TextView>(R.id.tv_grade)
            
            if (tvCorrectCount != null) {
                tvCorrectCount.text = "Правильно: ${updatedResult.correctCount}"
            }
            
            if (tvPercentage != null) {
                tvPercentage.text = "Процент: ${String.format("%.1f", updatedResult.percentage)}%"
            }
            
            if (tvGrade != null) {
                tvGrade.text = "Оценка: ${updatedResult.grade}"
                if (updatedResult.grade == 0) {
                    tvGrade.setTextColor(getColor(android.R.color.darker_gray))
                } else {
                    tvGrade.setTextColor(getGradeColor(updatedResult.grade))
                }
            }
            
            Log.d("BatchActivity", "🔄 UI диалога обновлен: ${updatedResult.correctCount}/${updatedResult.totalQuestions} = ${String.format("%.1f", updatedResult.percentage)}%, оценка: ${updatedResult.grade}")
        } catch (e: Exception) {
            Log.e("BatchActivity", "❌ Ошибка обновления UI диалога: ${e.message}")
        }
    }
    
    /**
     * Обновляет все результаты с учетом исправлений
     */
    private fun updateAllResultsWithFixedAnswers() {
        val results = batchResultsAdapter.getResults()
        results.forEach { result ->
            // Проверяем, есть ли исправления для этого результата
            if (fixedAnswerProcessor.hasFixedAnswers(result.filename)) {
                // Обновляем результат с учетом всех исправлений
                // Создаем временный диалог для передачи в метод
                val tempDialog = AlertDialog.Builder(this).create()
                updateBatchResultAfterFixedAnswer(result, FixedAnswer(0, 0, false, null), false, tempDialog)
            }
        }
        
        // Обновляем весь список
        batchResultsAdapter.notifyDataSetChanged()
        Log.d("BatchActivity", "🔄 Все результаты обновлены с учетом исправлений")
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
        var skippedCount = 0
        results.forEach { result ->
            if (result.grade > 0) { // Добавляем только успешно обработанные результаты
                // Проверяем, не добавлена ли уже работа в отчет
                if (result.isAddedToReport) {
                    Log.d("BatchActivity", "⏭️ Работа ${result.filename} уже добавлена в отчет, пропускаем")
                    skippedCount++
                    return@forEach
                }
                
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
            val message = if (skippedCount > 0) {
                "Добавлено $addedCount результатов в отчет (пропущено $skippedCount уже добавленных)"
            } else {
                "Добавлено $addedCount результатов в отчет"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            btnAddAllToReport.isEnabled = false
            btnAddAllToReport.text = "Добавлено"
        } else {
            Toast.makeText(this, "Нет новых результатов для добавления в отчет", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSaveCriteriaDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.batch_save_criteria_dialog, null)
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()

        // Убираем белые углы
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etCriteriaName = dialogView.findViewById<EditText>(R.id.et_criteria_name)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btn_save)

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

        // Убираем белые углы
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val layoutCriteriaList = dialogView.findViewById<LinearLayout>(R.id.layout_criteria_list)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_cancel_load)

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
        workCounter = 0 // Сбрасываем счетчик работ
        
        // Сбрасываем систему номеров работ
        usedWorkNumbers.clear()
        nextAvailableWorkNumber = 1
        
        // Очищаем данные об исправлениях
        fixedAnswerProcessor.clearAllData()
        
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
    


    // Методы для работы с камерой
    private fun showCameraSetupDialog() {
        // Убираем лишний диалог с начальным номером - сразу открываем камеру
        // Номер работы будет указан в финальном диалоге
        takePhoto()
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

        val etWorkNumber = dialogView.findViewById<EditText>(R.id.et_work_number)
        val tvWorkNumberError = dialogView.findViewById<TextView>(R.id.tv_work_number_error)
        val ivPhoto = dialogView.findViewById<ImageView>(R.id.iv_photo)
        val btnRetake = dialogView.findViewById<Button>(R.id.btn_retake)
        val btnAdd = dialogView.findViewById<Button>(R.id.btn_add_photo)
        val btnNext = dialogView.findViewById<Button>(R.id.btn_next_photo)

        // Устанавливаем следующий доступный номер работы
        etWorkNumber.setText(nextAvailableWorkNumber.toString())
        
        // Функция валидации номера работы
        fun validateWorkNumber(): Boolean {
            val workNumberText = etWorkNumber.text.toString()
            if (workNumberText.isEmpty()) {
                tvWorkNumberError.text = "Введите номер работы"
                tvWorkNumberError.visibility = View.VISIBLE
                etWorkNumber.setTextColor(getColor(R.color.error))
                return false
            }
            
            val workNumber = workNumberText.toIntOrNull()
            if (workNumber == null || workNumber <= 0) {
                tvWorkNumberError.text = "Номер работы должен быть положительным числом"
                tvWorkNumberError.visibility = View.VISIBLE
                etWorkNumber.setTextColor(getColor(R.color.error))
                return false
            }
            
            if (usedWorkNumbers.contains(workNumber)) {
                tvWorkNumberError.text = "Номер работы $workNumber уже занят. Следующий свободный: $nextAvailableWorkNumber"
                tvWorkNumberError.visibility = View.VISIBLE
                etWorkNumber.setTextColor(getColor(R.color.error))
                return false
            }
            
            // Номер валиден
            tvWorkNumberError.visibility = View.GONE
            etWorkNumber.setTextColor(getColor(R.color.text_inverse))
            return true
        }
        
        // Валидация при вводе
        etWorkNumber.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                validateWorkNumber()
            }
        })

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
            // Проверяем валидность номера работы
            if (!validateWorkNumber()) {
                Toast.makeText(this, "Исправьте ошибки в номере работы", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val workNumber = etWorkNumber.text.toString().toInt()
            
            // Проверяем, не добавлен ли уже файл с таким именем
            val fileName = getFileNameFromUri(uri)
            if (selectedImages.any { existingUri ->
                getFileNameFromUri(existingUri) == fileName
            }) {
                Toast.makeText(this, "Файл с именем '$fileName' уже добавлен", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Проверяем, не был ли файл уже обработан ранее
            if (processedFileNames.contains(fileName)) {
                Toast.makeText(this, "Файл '$fileName' уже был обработан ранее", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Добавляем фотографию в список
            selectedImages.add(uri)
            
            // Сохраняем имя работы для этого URI
            val workName = "Работа $workNumber"
            photoWorkNames[uri] = workNumber.toString() // Сохраняем номер работы
            
            // Добавляем номер в использованные
            usedWorkNumbers.add(workNumber)
            
            // Обновляем следующий доступный номер
            updateNextAvailableWorkNumber()
            
            updateSelectedCount()
            updateProcessButton()
            
            Toast.makeText(this, "$workName добавлена", Toast.LENGTH_SHORT).show()
            
            // Закрываем диалог и НЕ продолжаем фотографирование
            dialog.dismiss()
        }

        // Кнопка "Следующая работа"
        btnNext.setOnClickListener {
            if (!validateWorkNumber()) {
                Toast.makeText(this, "Исправьте ошибки в номере работы", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val workNumber = etWorkNumber.text.toString().toInt()
            val fileName = getFileNameFromUri(uri)
            
            // Проверяем дубликаты
            if (selectedImages.any { existingUri ->
                getFileNameFromUri(existingUri) == fileName
            }) {
                Toast.makeText(this, "Файл с именем '$fileName' уже добавлен", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (processedFileNames.contains(fileName)) {
                Toast.makeText(this, "Файл '$fileName' уже был обработан ранее", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Добавляем фотографию
            selectedImages.add(uri)
            photoWorkNames[uri] = workNumber.toString()
            usedWorkNumbers.add(workNumber)
            updateNextAvailableWorkNumber()
            
            // Обновляем UI
            updateSelectedCount()
            updateProcessButton()
            
            // Закрываем диалог и делаем следующее фото
            dialog.dismiss()
            takePhoto() // Сразу делаем следующее фото
            
            Toast.makeText(this, "Фотография добавлена как Работа $workNumber. Делаем следующее фото...", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }
    
    // ===== РЕАЛИЗАЦИЯ FixedAnswerCallback =====
    
    override fun onFixedAnswerDetected(fixedAnswer: FixedAnswer, onUserDecision: (Boolean) -> Unit) {
        Log.d("BatchActivity", "⚠️ Обнаружено исправление в вопросе ${fixedAnswer.questionNumber}")
        
        // Сохраняем исправление для текущего обрабатываемого файла
        val currentFilename = "current_processing" // Временно, нужно будет передавать реальное имя файла
        pendingFixedAnswers.getOrPut(currentFilename) { mutableListOf() }.add(fixedAnswer)
        
        // Пока что автоматически считаем исправление ошибкой (будет переопределено в диалоге)
        onUserDecision(true)
    }
    
    override fun onAllFixedAnswersProcessed(finalResult: OMRResult) {
        Log.d("BatchActivity", "✅ Все исправления обработаны, обновляем результаты")
        
        // Здесь будет логика обновления результатов после принятия решений пользователем
        // Пока что просто логируем
    }
    
    /**
     * Возвращает цвет для оценки
     */
    private fun getGradeColor(grade: Int): Int {
        return when (grade) {
            5 -> Color.parseColor("#4CAF50") // Зеленый
            4 -> Color.parseColor("#8BC34A") // Светло-зеленый
            3 -> Color.parseColor("#FFC107") // Желтый
            2 -> Color.parseColor("#F44336") // Красный
            else -> Color.GRAY
        }
    }
    
    /**
     * Обновляет следующий доступный номер работы
     */
    private fun updateNextAvailableWorkNumber() {
        // Начинаем с 1 и ищем первый свободный номер
        var candidate = 1
        while (usedWorkNumbers.contains(candidate)) {
            candidate++
        }
        nextAvailableWorkNumber = candidate
    }
    
    /**
     * Получает следующий доступный номер работы
     */
    private fun getNextAvailableWorkNumber(): Int {
        updateNextAvailableWorkNumber()
        return nextAvailableWorkNumber
    }
    
    /**
     * Получает имя файла из URI
     */
    private fun getFileNameFromUri(uri: Uri): String {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    it.getString(nameIndex)
                } else {
                    uri.lastPathSegment ?: "unknown_file"
                }
            } ?: (uri.lastPathSegment ?: "unknown_file")
        } catch (e: Exception) {
            uri.lastPathSegment ?: "unknown_file"
        }
    }
    
    /**
     * Показывает умный диалог сброса
     */
    private fun showSmartResetDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.batch_smart_reset_dialog, null)
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()

        // Убираем белые углы
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Находим кнопки
        val btnResetAll = dialogView.findViewById<MaterialButton>(R.id.btn_reset_all)
        val btnResetResults = dialogView.findViewById<MaterialButton>(R.id.btn_reset_results)
        val btnResetWorks = dialogView.findViewById<MaterialButton>(R.id.btn_reset_works)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btn_confirm)

        // Переменная для хранения выбранного действия
        var selectedAction: (() -> Unit)? = null

        // Обработчики для кнопок выбора действия
        btnResetAll.setOnClickListener {
            selectedAction = { resetAll() }
            // Визуально выделяем выбранную кнопку
            btnResetAll.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#475569"))
            btnResetResults.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E293B"))
            btnResetWorks.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E293B"))
        }

        btnResetResults.setOnClickListener {
            selectedAction = { resetOnlyResults() }
            // Визуально выделяем выбранную кнопку
            btnResetAll.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E293B"))
            btnResetResults.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#475569"))
            btnResetWorks.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E293B"))
        }

        btnResetWorks.setOnClickListener {
            selectedAction = { resetOnlyLoadedWorks() }
            // Визуально выделяем выбранную кнопку
            btnResetAll.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E293B"))
            btnResetResults.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E293B"))
            btnResetWorks.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#475569"))
        }

        // Кнопка отмены
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        // Кнопка подтверждения
        btnConfirm.setOnClickListener {
            selectedAction?.invoke()
            dialog.dismiss()
        }

        dialog.show()
    }
    
    /**
     * Сбрасывает только результаты, оставляя загруженные работы
     */
    private fun resetOnlyResults() {
        batchResultsAdapter.clearResults()
        fixedAnswerProcessor.clearAllData()
        Toast.makeText(this, "Результаты сброшены, загруженные работы готовы к повторной проверке", Toast.LENGTH_LONG).show()
    }
    
    /**
     * Сбрасывает только загруженные работы, оставляя результаты
     */
    private fun resetOnlyLoadedWorks() {
        selectedImages.clear()
        photoWorkNames.clear()
        usedWorkNumbers.clear()
        nextAvailableWorkNumber = 1
        updateSelectedCount()
        updateProcessButton()
        Toast.makeText(this, "Загруженные работы сброшены, результаты сохранены", Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1002
    }
} 