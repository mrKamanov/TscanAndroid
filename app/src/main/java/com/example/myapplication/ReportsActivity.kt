package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.reports.ReportsManager
import com.example.myapplication.reports.StatisticsAdapter
import com.example.myapplication.reports.ReportsManager.GradingCriteria
import com.example.myapplication.reports.ReportsManager.CriteriaType
import com.google.android.material.navigation.NavigationView
import com.example.myapplication.reports.ExcelExporter
import java.io.File
import android.view.LayoutInflater

class ReportsActivity : AppCompatActivity() {
    
    private lateinit var reportsManager: ReportsManager
    private lateinit var reportsAdapter: ReportsAdapter
    private lateinit var statisticsAdapter: StatisticsAdapter
    private lateinit var drawerLayout: DrawerLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        // Полноэкранный режим
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        reportsManager = ReportsManager(this)
        setupUI()
        setupDrawer()
        setupRecyclerView()
        loadReports()
        loadStatistics()
    }
    
    private fun setupUI() {
        drawerLayout = findViewById(R.id.drawer_layout_reports)
        // Убираем настройку кнопок, так как они удалены из layout
    }
    
    private fun setupDrawer() {
        setupCriteriaSettings()
        setupDrawerButtons()
    }
    
    private fun setupCriteriaSettings() {
        val rbPercentages = findViewById<RadioButton>(R.id.rb_percentages)
        val rbGrades = findViewById<RadioButton>(R.id.rb_grades)
        val etGrade5 = findViewById<EditText>(R.id.et_grade_5)
        val etGrade4 = findViewById<EditText>(R.id.et_grade_4)
        val etGrade3 = findViewById<EditText>(R.id.et_grade_3)
        val etGrade2 = findViewById<EditText>(R.id.et_grade_2)
        
        // Загружаем текущие настройки
        loadCriteriaSettings()
        
        // Обработчик переключения режима
        rbPercentages.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                updateUnitLabels("%")
                updateDefaultValues(true)
            }
        }
        
        rbGrades.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                updateUnitLabels("балл")
                updateDefaultValues(false)
            }
        }
    }
    
    private fun setupDrawerButtons() {
        // Кнопка "Применить"
        findViewById<Button>(R.id.btn_apply_criteria).setOnClickListener {
            applyCriteriaSettings()
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(this, "✅ Настройки применены", Toast.LENGTH_SHORT).show()
        }
        
        // Кнопка "Экспорт в Excel"
        findViewById<Button>(R.id.btn_export_excel).setOnClickListener {
            exportToExcel()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        
        // Кнопка "Сбросить отчеты"
        findViewById<Button>(R.id.btn_reset_reports).setOnClickListener {
            showResetReportsDialog()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }
    
    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.reportsRecyclerView)
        val statisticsRecyclerView = findViewById<RecyclerView>(R.id.statistics_recycler_view)
        
        // Настройка списка отчетов
        reportsAdapter = ReportsAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = reportsAdapter

        reportsAdapter.setOnItemClickListener { report ->
            showReportDetails(report)
        }

        reportsAdapter.setOnDeleteClickListener { report ->
            deleteReport(report)
        }

        // Настройка инфографики
        statisticsAdapter = StatisticsAdapter()
        statisticsRecyclerView.layoutManager = LinearLayoutManager(this)
        statisticsRecyclerView.adapter = statisticsAdapter
    }
    
    private fun loadReports() {
        val reports = reportsManager.getReports()
        reportsAdapter.updateReports(reports)
        
        val emptyStateText = findViewById<TextView>(R.id.empty_state_text)
        if (reports.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
        } else {
            emptyStateText.visibility = View.GONE
        }
        
        updateCurrentCriteriaText()
    }

    private fun loadStatistics() {
        Log.d("ReportsActivity", "📊 loadStatistics() вызван")
        val statistics = reportsManager.getStatistics()
        Log.d("ReportsActivity", "📊 Статистика получена: ${statistics.size} элементов")
        Log.d("ReportsActivity", "📊 Тепловая карта: ${(statistics["questionHeatmap"] as? List<*>)?.size ?: 0} элементов")
        statisticsAdapter.updateStatistics(statistics)
        Log.d("ReportsActivity", "📊 Статистика обновлена в адаптере")
    }
    
    private fun showReportDetails(report: ReportsManager.Report) {
        val dialogView = layoutInflater.inflate(R.layout.report_details_dialog, null)
        
        // Заполняем данные
        dialogView.findViewById<TextView>(R.id.tv_report_title).text = report.title
        dialogView.findViewById<TextView>(R.id.tv_report_date).text = "Дата: ${report.date}"
        
        val correctCount = report.omrResult.grading.sum()
        val totalCount = report.omrResult.grading.size
        val incorrectCount = totalCount - correctCount
        val percentage = if (totalCount > 0) (correctCount.toFloat() / totalCount) * 100 else 0f
        
        dialogView.findViewById<TextView>(R.id.tv_correct_answers).text = "$correctCount из $totalCount"
        dialogView.findViewById<TextView>(R.id.tv_incorrect_answers).text = incorrectCount.toString()
        dialogView.findViewById<TextView>(R.id.tv_percentage).text = "${String.format("%.1f", percentage)}%"
        dialogView.findViewById<TextView>(R.id.tv_grade).text = "Оценка ${report.grade}"
        
        // Обработка ошибок
        val errorsContainer = dialogView.findViewById<LinearLayout>(R.id.errors_container)
        errorsContainer.removeAllViews()
        
        val actualErrors = mutableListOf<Map<String, Any>>()
        report.omrResult.grading.forEachIndexed { index, isCorrect ->
            if (isCorrect == 0) {
                val selectedAnswer = report.omrResult.selectedAnswers[index]
                val correctAnswer = report.omrResult.correctAnswers[index]
                actualErrors.add(mapOf(
                    "question_number" to (index + 1),
                    "selected_answer" to (selectedAnswer + 1),
                    "correct_answer" to (correctAnswer + 1)
                ))
            }
        }
        
        if (actualErrors.isEmpty()) {
            val noErrorsView = TextView(this).apply {
                text = "✅ Все ответы правильные!"
                setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                textSize = 14f
                setPadding(0, 8, 0, 8)
            }
            errorsContainer.addView(noErrorsView)
        } else {
            actualErrors.forEach { error ->
                val errorView = TextView(this).apply {
                    text = "Вопрос ${error["question_number"]}: выбрано ${error["selected_answer"]}, верно ${error["correct_answer"]}"
                    setTextColor(android.graphics.Color.parseColor("#FF6B6B"))
                    textSize = 14f
                    setPadding(0, 8, 0, 8)
                }
                errorsContainer.addView(errorView)
            }
        }
        
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Кнопка закрытия
        dialogView.findViewById<Button>(R.id.btn_close).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun deleteReport(report: ReportsManager.Report) {
        AlertDialog.Builder(this)
            .setTitle("Удаление отчета")
            .setMessage("Вы уверены, что хотите удалить отчет '${report.title}'?")
            .setPositiveButton("Удалить") { _, _ ->
                reportsManager.deleteReport(report.id)
                loadReports()
                loadStatistics()
                    Toast.makeText(this, "Отчет удален", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun showCriteriaSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.criteria_settings_dialog, null)
        val criteriaContainer = dialogView.findViewById<LinearLayout>(R.id.criteria_container)
        val currentCriteria = reportsManager.getCurrentCriteria()
        
        // Создаем диалог заранее
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        criteriaContainer.removeAllViews()
        
        reportsManager.getCriteriaList().forEach { criteria ->
            val criteriaView = layoutInflater.inflate(R.layout.criteria_item, criteriaContainer, false)
            
            criteriaView.findViewById<TextView>(R.id.tv_criteria_name).text = criteria.name
            criteriaView.findViewById<TextView>(R.id.tv_criteria_type).text = 
                if (criteria.type == ReportsManager.CriteriaType.PERCENTAGE) "По процентам" else "По баллам"
            
            val currentIndicator = criteriaView.findViewById<TextView>(R.id.current_indicator)
            if (criteria.id == currentCriteria?.id) {
                currentIndicator.text = "✓"
                currentIndicator.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                currentIndicator.visibility = View.VISIBLE
            } else {
                currentIndicator.visibility = View.GONE
            }
            
            criteriaView.setOnClickListener {
                reportsManager.setCurrentCriteria(criteria.id)
                reportsManager.recalculateAllGrades()
                loadReports()
                loadStatistics()
                updateCurrentCriteriaText()
                Toast.makeText(this, "Критерии изменены", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            
            criteriaContainer.addView(criteriaView)
        }
        
        // Кнопка удаления (только для не-дефолтных критериев)
        val btnDelete = dialogView.findViewById<Button>(R.id.btn_delete_criteria)
        btnDelete.setOnClickListener {
            val nonDefaultCriteria = reportsManager.getCriteriaList().filter { it.id != "default" }
            if (nonDefaultCriteria.isNotEmpty()) {
                val criteriaToDelete = nonDefaultCriteria.first()
                val success = reportsManager.deleteCriteria(criteriaToDelete.id)
                if (success) {
                    loadReports()
                    loadStatistics()
                    updateCurrentCriteriaText()
                    Toast.makeText(this, "Критерии удалены", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, "Не удалось удалить критерии", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Нет критериев для удаления", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Кнопка закрытия
        dialogView.findViewById<Button>(R.id.btn_close).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showAddCriteriaDialog() {
        val dialogView = layoutInflater.inflate(R.layout.add_criteria_dialog, null)
        
        val etName = dialogView.findViewById<EditText>(R.id.et_criteria_name)
        val rgCriteriaType = dialogView.findViewById<RadioGroup>(R.id.rg_criteria_type)
        val etGrade5 = dialogView.findViewById<EditText>(R.id.et_grade_5)
        val etGrade4 = dialogView.findViewById<EditText>(R.id.et_grade_4)
        val etGrade3 = dialogView.findViewById<EditText>(R.id.et_grade_3)
        val etGrade2 = dialogView.findViewById<EditText>(R.id.et_grade_2)
        
        // Устанавливаем значения по умолчанию
        etGrade5.setText("90")
        etGrade4.setText("75")
        etGrade3.setText("51")
        etGrade2.setText("0")
        
        // Обработчик изменения типа критериев
        rgCriteriaType.setOnCheckedChangeListener { group, checkedId ->
            val isPercentage = checkedId == R.id.rb_percentage
            val hint = if (isPercentage) "Минимальный процент для оценки" else "Минимальные баллы для оценки"
            etGrade5.hint = hint
            etGrade4.hint = hint
            etGrade3.hint = hint
            etGrade2.hint = hint
        }
        
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Кнопка отмены
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        // Кнопка сохранения
        dialogView.findViewById<Button>(R.id.btn_save).setOnClickListener {
            val name = etName.text.toString()
            if (name.isNotEmpty()) {
                val isPercentage = rgCriteriaType.checkedRadioButtonId == R.id.rb_percentage
                val type = if (isPercentage) ReportsManager.CriteriaType.PERCENTAGE else ReportsManager.CriteriaType.POINTS
                
                val grade5Value = etGrade5.text.toString().toDoubleOrNull() ?: 0.0
                val grade4Value = etGrade4.text.toString().toDoubleOrNull() ?: 0.0
                val grade3Value = etGrade3.text.toString().toDoubleOrNull() ?: 0.0
                val grade2Value = etGrade2.text.toString().toDoubleOrNull() ?: 0.0
                
                // Создаем правильную структуру критериев с диапазонами
                val criteria = mutableMapOf<Int, ClosedRange<Double>>()
                if (isPercentage) {
                    criteria[5] = grade5Value..100.0
                    criteria[4] = grade4Value..(grade5Value - 0.1)
                    criteria[3] = grade3Value..(grade4Value - 0.1)
                    criteria[2] = grade2Value..(grade3Value - 0.1)
                } else {
                    val maxPoints = maxOf(grade5Value, grade4Value, grade3Value, grade2Value)
                    criteria[5] = grade5Value..maxPoints
                    criteria[4] = grade4Value..(grade5Value - 0.1)
                    criteria[3] = grade3Value..(grade4Value - 0.1)
                    criteria[2] = grade2Value..(grade3Value - 0.1)
                }
                
                val newCriteria = ReportsManager.GradingCriteria(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    type = type,
                    criteria = criteria,
                    maxPoints = if (isPercentage) 100 else maxOf(grade5Value, grade4Value, grade3Value, grade2Value).toInt()
                )
                
                reportsManager.addCriteria(newCriteria)
                reportsManager.setCurrentCriteria(newCriteria.id)
                reportsManager.recalculateAllGrades()
                loadReports()
                loadStatistics()
                updateCurrentCriteriaText()
                Toast.makeText(this, "Новые критерии добавлены", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Введите название критериев", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.show()
    }



    private fun updateCurrentCriteriaText() {
        val currentCriteria = reportsManager.getCurrentCriteria()
        // Убираем обновление текста, так как элемент удален из layout
    }
    
    override fun onResume() {
        super.onResume()
        loadReports()
        loadStatistics()
    }

    /**
     * Экспортирует отчеты в Excel файл
     */
    private fun exportToExcel() {
        try {
            // Показываем красивый индикатор загрузки
            val progressDialog = createProgressDialog()
            progressDialog.show()
            
            // Запускаем экспорт в фоновом потоке
            Thread {
                try {
                    val excelExporter = ExcelExporter(this)
                    val file = excelExporter.exportToExcel(reportsManager)
                    
                    runOnUiThread {
                        progressDialog.dismiss()
                        
                        if (file != null) {
                            // Показываем красивый диалог успеха
                            showExportSuccessDialog(file)
                        } else {
                            Toast.makeText(this, "❌ Ошибка при создании Excel файла", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ReportsActivity", "Ошибка при экспорте в Excel", e)
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this, "❌ Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
            
        } catch (e: Exception) {
            Log.e("ReportsActivity", "Ошибка при запуске экспорта", e)
            Toast.makeText(this, "❌ Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Создает красивый диалог загрузки
     */
    private fun createProgressDialog(): AlertDialog {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.progress_dialog, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        // Убираем белые углы
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        return dialog
    }
    
    /**
     * Открывает Excel файл в другом приложении
     */
    private fun openExcelFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "Нет приложения для открытия Excel файлов", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("ReportsActivity", "Ошибка при открытии файла", e)
            Toast.makeText(this, "Ошибка при открытии файла", Toast.LENGTH_LONG).show()
        }
    }

    private fun showExportSuccessDialog(file: File) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.export_success_dialog, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        // Убираем белые углы
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Настраиваем информацию о файле
        val tvFileInfo = dialogView.findViewById<TextView>(R.id.tv_file_info)
        tvFileInfo.text = "Файл: ${file.name}\nСохранен в папке 'Загрузки'"
        
        // Кнопка "Открыть папку"
        dialogView.findViewById<Button>(R.id.btn_open_file).setOnClickListener {
            dialog.dismiss()
            openFileLocation(file)
        }
        
        // Кнопка "Отправить"
        dialogView.findViewById<Button>(R.id.btn_share_file).setOnClickListener {
            dialog.dismiss()
            shareExcelFile(file)
        }
        
        // Кнопка "Закрыть"
        dialogView.findViewById<Button>(R.id.btn_close).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * Отправляет Excel файл через другие приложения
     */
    private fun shareExcelFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "OMR Отчет")
                putExtra(Intent.EXTRA_TEXT, "Отчет по результатам проверки работ")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Отправить отчет"))
            
        } catch (e: Exception) {
            Log.e("ReportsActivity", "Ошибка при отправке файла", e)
            Toast.makeText(this, "Ошибка при отправке файла", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Открывает папку с файлом
     */
    private fun openFileLocation(file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            // Пытаемся открыть папку Downloads
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Если не получилось, показываем путь к файлу
                Toast.makeText(this, "Файл сохранен: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            Log.e("ReportsActivity", "Ошибка при открытии папки", e)
            Toast.makeText(this, "Файл сохранен в папке 'Загрузки'", Toast.LENGTH_LONG).show()
        }
    }
    
    // Методы для работы с настройками критериев
    private fun loadCriteriaSettings() {
        val prefs = getSharedPreferences("criteria_settings", MODE_PRIVATE)
        val isPercentages = prefs.getBoolean("is_percentages", true)
        
        findViewById<RadioButton>(R.id.rb_percentages).isChecked = isPercentages
        findViewById<RadioButton>(R.id.rb_grades).isChecked = !isPercentages
        
        findViewById<EditText>(R.id.et_grade_5).setText(prefs.getInt("grade_5", 90).toString())
        findViewById<EditText>(R.id.et_grade_4).setText(prefs.getInt("grade_4", 75).toString())
        findViewById<EditText>(R.id.et_grade_3).setText(prefs.getInt("grade_3", 50).toString())
        findViewById<EditText>(R.id.et_grade_2).setText(prefs.getInt("grade_2", 0).toString())
        
        updateUnitLabels(if (isPercentages) "%" else "балл")
        
        // Применяем загруженные критерии к ReportsManager
        applyLoadedCriteriaToManager()
    }
    
    private fun applyLoadedCriteriaToManager() {
        val prefs = getSharedPreferences("criteria_settings", MODE_PRIVATE)
        val isPercentages = prefs.getBoolean("is_percentages", true)
        val grade5 = prefs.getInt("grade_5", if (isPercentages) 90 else 0)
        val grade4 = prefs.getInt("grade_4", if (isPercentages) 75 else 0)
        val grade3 = prefs.getInt("grade_3", if (isPercentages) 50 else 0)
        val grade2 = prefs.getInt("grade_2", if (isPercentages) 0 else 0)
        
        // Создаем критерии в формате ReportsManager
        val criteria = if (isPercentages) {
            // Режим процентов
            mapOf(
                5 to grade5.toDouble()..100.0,
                4 to grade4.toDouble()..(grade5 - 0.01),
                3 to grade3.toDouble()..(grade4 - 0.01),
                2 to 0.0..(grade3 - 0.01)
            )
        } else {
            // Режим баллов - сравниваем количество правильных ответов
            mapOf(
                5 to grade5.toDouble()..1000.0, // Большой диапазон для максимального количества вопросов
                4 to grade4.toDouble()..(grade5 - 0.01),
                3 to grade3.toDouble()..(grade4 - 0.01),
                2 to 0.0..(grade3 - 0.01)
            )
        }
        
        // Обновляем критерии в ReportsManager
        val newCriteria = ReportsManager.GradingCriteria(
            id = "custom",
            name = if (isPercentages) "Пользовательские (проценты)" else "Пользовательские (баллы)",
            type = if (isPercentages) ReportsManager.CriteriaType.PERCENTAGE else ReportsManager.CriteriaType.POINTS,
            criteria = criteria
        )
        
        // Добавляем или обновляем критерии
        val existingCriteria = reportsManager.getCriteriaList().find { it.id == "custom" }
        if (existingCriteria != null) {
            // Обновляем существующие критерии
            reportsManager.deleteCriteria("custom")
        }
        reportsManager.addCriteria(newCriteria)
        reportsManager.setCurrentCriteria("custom")
    }
    
    private fun updateUnitLabels(unit: String) {
        findViewById<TextView>(R.id.tv_grade_5_unit).text = unit
        findViewById<TextView>(R.id.tv_grade_4_unit).text = unit
        findViewById<TextView>(R.id.tv_grade_3_unit).text = unit
        findViewById<TextView>(R.id.tv_grade_2_unit).text = unit
    }
    
    private fun updateDefaultValues(isPercentages: Boolean) {
        if (isPercentages) {
            findViewById<EditText>(R.id.et_grade_5).setText("90")
            findViewById<EditText>(R.id.et_grade_4).setText("75")
            findViewById<EditText>(R.id.et_grade_3).setText("50")
            findViewById<EditText>(R.id.et_grade_2).setText("0")
        } else {
            findViewById<EditText>(R.id.et_grade_5).setText("")
            findViewById<EditText>(R.id.et_grade_4).setText("")
            findViewById<EditText>(R.id.et_grade_3).setText("")
            findViewById<EditText>(R.id.et_grade_2).setText("")
        }
    }
    
    private fun applyCriteriaSettings() {
        val prefs = getSharedPreferences("criteria_settings", MODE_PRIVATE)
        val editor = prefs.edit()
        
        val isPercentages = findViewById<RadioButton>(R.id.rb_percentages).isChecked
        editor.putBoolean("is_percentages", isPercentages)
        
        val grade5 = findViewById<EditText>(R.id.et_grade_5).text.toString().toIntOrNull() ?: (if (isPercentages) 90 else 0)
        val grade4 = findViewById<EditText>(R.id.et_grade_4).text.toString().toIntOrNull() ?: (if (isPercentages) 75 else 0)
        val grade3 = findViewById<EditText>(R.id.et_grade_3).text.toString().toIntOrNull() ?: (if (isPercentages) 50 else 0)
        val grade2 = findViewById<EditText>(R.id.et_grade_2).text.toString().toIntOrNull() ?: (if (isPercentages) 0 else 0)
        
        editor.putInt("grade_5", grade5)
        editor.putInt("grade_4", grade4)
        editor.putInt("grade_3", grade3)
        editor.putInt("grade_2", grade2)
        
        editor.apply()
        
        // Обновляем критерии в ReportsManager
        val newCriteria = mapOf(
            "is_percentages" to isPercentages,
            "grade_5" to grade5,
            "grade_4" to grade4,
            "grade_3" to grade3,
            "grade_2" to grade2
        )
        
        // Пересчитываем оценки для всех отчетов с новыми критериями
        val reports = reportsManager.getReports()
        reports.forEach { report ->
            val newGrade = calculateGrade(report.omrResult.grading, newCriteria)
            reportsManager.updateReportGrade(report.id, newGrade)
        }
        
        // Перезагружаем отчеты с новыми критериями
        loadReports()
        loadStatistics()
    }
    
    /**
     * Вычисляет оценку на основе результатов и критериев
     */
    private fun calculateGrade(grading: IntArray, criteria: Map<String, Any>): Int {
        val correctAnswers = grading.count { it == 1 }
        val totalQuestions = grading.size
        
        if (totalQuestions == 0) return 2
        
        val isPercentages = criteria["is_percentages"] as? Boolean ?: true
        
        return if (isPercentages) {
            // Режим процентов - создаем диапазоны
            val percentage = (correctAnswers.toDouble() / totalQuestions) * 100
            val grade5 = criteria["grade_5"] as? Int ?: 90
            val grade4 = criteria["grade_4"] as? Int ?: 75
            val grade3 = criteria["grade_3"] as? Int ?: 50
            val grade2 = criteria["grade_2"] as? Int ?: 0
            
            when {
                percentage >= grade5 -> 5  // 90% и более = 5
                percentage >= grade4 -> 4  // 75% и более, но меньше 90% = 4
                percentage >= grade3 -> 3  // 50% и более, но меньше 75% = 3
                percentage >= grade2 -> 2  // 0% и более, но меньше 50% = 2
                else -> 2                 // меньше 0% = 2
            }
        } else {
            // Режим баллов - создаем диапазоны на основе критериев
            val grade5 = criteria["grade_5"] as? Int ?: 5
            val grade4 = criteria["grade_4"] as? Int ?: 4
            val grade3 = criteria["grade_3"] as? Int ?: 3
            val grade2 = criteria["grade_2"] as? Int ?: 2
            
            when {
                correctAnswers >= grade5 -> 5  // 4 и более = 5
                correctAnswers >= grade4 -> 4  // равно 3 но меньше 4 = 4
                correctAnswers >= grade3 -> 3  // равно 2 но меньше 3 = 3
                correctAnswers >= grade2 -> 2  // больше или равно 0 но меньше 2 = 2
                else -> 2
            }
        }
    }
    
    private fun showResetReportsDialog() {
        AlertDialog.Builder(this)
            .setTitle("🗑️ Сброс отчетов")
            .setMessage("Вы уверены, что хотите удалить все отчеты? Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ ->
                val reports = reportsManager.getReports()
                reports.forEach { report ->
                    reportsManager.deleteReport(report.id)
                }
                loadReports()
                loadStatistics()
                Toast.makeText(this, "🗑️ Все отчеты удалены", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
} 