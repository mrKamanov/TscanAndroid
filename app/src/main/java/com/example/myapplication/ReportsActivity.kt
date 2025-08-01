package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.reports.ReportsManager
import android.util.Log
import android.widget.Toast
import androidx.core.view.GravityCompat

class ReportsActivity : AppCompatActivity() {
    
    private lateinit var reportsManager: ReportsManager
    private lateinit var reportsAdapter: ReportsAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        // Инициализация менеджера отчетов
        reportsManager = ReportsManager(this)
        
        // Настройка UI
        setupUI()
        setupDrawer()
        setupRecyclerView()
        loadReports()
    }
    
    private fun setupUI() {
        recyclerView = findViewById(R.id.reportsRecyclerView)
        emptyStateText = findViewById(R.id.empty_state_text)
        
        // Кнопка сброса отчетов
        findViewById<Button>(R.id.btn_reset_reports).setOnClickListener {
            showResetReportsDialog()
        }
        
        // Кнопка критериев в боковом меню
        findViewById<Button>(R.id.btn_criteria_settings_drawer).setOnClickListener {
            showCriteriaSettingsDialog()
        }
        
        // Кнопка добавления новых критериев
        findViewById<Button>(R.id.btn_add_criteria).setOnClickListener {
            showAddCriteriaDialog()
        }
        
        // Обновляем текст текущих критериев
        updateCurrentCriteriaText()
    }
    
    private fun setupDrawer() {
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout_reports)
        val btnMenu = findViewById<ImageButton>(R.id.btn_menu_reports)
        
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }
    
    private fun setupRecyclerView() {
        reportsAdapter = ReportsAdapter(
            reports = emptyList(),
            onReportClick = { report -> showReportDetails(report) },
            onReportDelete = { report -> deleteReport(report) }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = reportsAdapter
    }
    
    private fun loadReports() {
        val reports = reportsManager.getReports()
        
        if (reports.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            emptyStateText.text = "📋 Отчеты отсутствуют\n\nДобавьте результаты проверки в окне сканирования"
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
            reportsAdapter.updateReports(reports)
        }
        
        Log.d("ReportsActivity", "📂 Загружено ${reports.size} отчетов")
    }
    
    private fun showReportDetails(report: ReportsManager.Report) {
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .create()
        
        val dialogView = layoutInflater.inflate(R.layout.report_details_dialog, null)
        dialog.setView(dialogView)
        
        // Заполняем данные
        dialogView.findViewById<TextView>(R.id.dialog_title).text = report.title
        dialogView.findViewById<TextView>(R.id.dialog_date).text = report.date
        
        val omrResult = report.omrResult
        val correctCount = omrResult.grading.sum()
        val questionsCount = omrResult.grading.size
        val incorrectCount = questionsCount - correctCount
        val percentage = if (questionsCount > 0) (correctCount.toFloat() / questionsCount) * 100 else 0f
        
        Log.d("ReportsActivity", "📊 Статистика отчета: correctCount=$correctCount, questionsCount=$questionsCount, incorrectCount=$incorrectCount")
        Log.d("ReportsActivity", "📊 Grading: ${omrResult.grading.contentToString()}")
        Log.d("ReportsActivity", "📊 SelectedAnswers: ${omrResult.selectedAnswers.contentToString()}")
        Log.d("ReportsActivity", "📊 CorrectAnswers: ${omrResult.correctAnswers}")
        
        dialogView.findViewById<TextView>(R.id.correct_answers).text = correctCount.toString()
        dialogView.findViewById<TextView>(R.id.incorrect_answers).text = incorrectCount.toString()
        dialogView.findViewById<TextView>(R.id.completion_percentage).text = "${String.format("%.1f", percentage)}%"
        dialogView.findViewById<TextView>(R.id.grade).text = report.grade.toString()
        
        // Настраиваем цвет оценки
        val gradeColor = when (report.grade) {
            5 -> resources.getColor(R.color.success, theme)
            4 -> resources.getColor(R.color.info, theme)
            3 -> resources.getColor(R.color.warning, theme)
            2 -> resources.getColor(R.color.error, theme)
            else -> resources.getColor(R.color.text_secondary, theme)
        }
        dialogView.findViewById<TextView>(R.id.grade).setTextColor(gradeColor)
        
        // Заполняем ошибки
        val errorsContainer = dialogView.findViewById<LinearLayout>(R.id.errors_container)
        val errorsCard = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.errors_card)
        
        Log.d("ReportsActivity", "🔍 Обрабатываем ошибки: ${omrResult.incorrectQuestions.size} ошибок")
        Log.d("ReportsActivity", "🔍 Данные ошибок: ${omrResult.incorrectQuestions}")
        
        // Создаем правильный список ошибок на основе grading
        val actualErrors = mutableListOf<Map<String, Any>>()
        
        omrResult.grading.forEachIndexed { index, isCorrect ->
            if (isCorrect == 0) { // Неправильный ответ
                val questionNumber = index + 1
                val selectedAnswer = (omrResult.selectedAnswers.getOrNull(index) ?: 0) + 1  // +1 для пользователя
                val correctAnswer = (omrResult.correctAnswers.getOrNull(index) ?: 0) + 1   // +1 для пользователя
                
                Log.d("ReportsActivity", "🔍 Найдена ошибка: Вопрос $questionNumber (индекс $index), выбран $selectedAnswer, правильный $correctAnswer")
                
                actualErrors.add(mapOf(
                    "question" to questionNumber,
                    "selected" to selectedAnswer,
                    "correct" to correctAnswer
                ))
            }
        }
        
        Log.d("ReportsActivity", "🔍 Найдено ${actualErrors.size} реальных ошибок")
        
        if (actualErrors.isNotEmpty()) {
            errorsContainer.removeAllViews()
            
            actualErrors.forEach { error ->
                try {
                    val questionNumber = error["question"] as? Int ?: 0
                    val selectedAnswer = error["selected"] as? Int ?: 0  // Уже +1 в ImageProcessor
                    val correctAnswer = error["correct"] as? Int ?: 0    // Уже +1 в ImageProcessor
                    
                    val errorView = TextView(this).apply {
                        text = "Вопрос $questionNumber: ответ $selectedAnswer, правильный $correctAnswer"
                        setTextColor(resources.getColor(R.color.text, theme))
                        textSize = 14f
                        setPadding(0, 8, 0, 8)
                    }
                    errorsContainer.addView(errorView)
                    
                    Log.d("ReportsActivity", "✅ Добавлена ошибка: Вопрос $questionNumber (ответ $selectedAnswer, правильный $correctAnswer)")
                } catch (e: Exception) {
                    Log.e("ReportsActivity", "❌ Ошибка обработки данных ошибки: ${e.message}")
                    val errorView = TextView(this).apply {
                        text = "Вопрос: ошибка обработки данных"
                        setTextColor(resources.getColor(R.color.error, theme))
                        textSize = 14f
                        setPadding(0, 8, 0, 8)
                    }
                    errorsContainer.addView(errorView)
                }
            }
        } else {
            // Показываем сообщение об отсутствии ошибок
            errorsContainer.removeAllViews()
            val noErrorsView = TextView(this).apply {
                text = "🎉 Все ответы правильные!"
                setTextColor(resources.getColor(R.color.success, theme))
                textSize = 14f
                setPadding(0, 8, 0, 8)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
            errorsContainer.addView(noErrorsView)
        }
        
        // Кнопка закрытия
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_close).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    

    
    private fun deleteReport(report: ReportsManager.Report) {
        AlertDialog.Builder(this)
            .setTitle("Удаление отчета")
            .setMessage("Вы уверены, что хотите удалить отчет '${report.title}'?")
            .setPositiveButton("Удалить") { _, _ ->
                if (reportsManager.deleteReport(report.id)) {
                    Toast.makeText(this, "Отчет удален", Toast.LENGTH_SHORT).show()
                    loadReports()
                } else {
                    Toast.makeText(this, "Ошибка удаления отчета", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun showCriteriaSettingsDialog() {
        val criteria = reportsManager.getCriteriaList()
        val currentCriteria = reportsManager.getCurrentCriteria()
        
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .create()
        
        val dialogView = layoutInflater.inflate(R.layout.criteria_settings_dialog, null)
        dialog.setView(dialogView)
        
        // Заполняем список критериев
        val criteriaContainer = dialogView.findViewById<LinearLayout>(R.id.criteria_container)
        criteriaContainer.removeAllViews()
        
        criteria.forEach { criterion ->
            val criteriaItem = layoutInflater.inflate(R.layout.criteria_item, criteriaContainer, false)
            
            criteriaItem.findViewById<TextView>(R.id.criteria_name).text = criterion.name
            criteriaItem.findViewById<TextView>(R.id.criteria_type).text = when (criterion.type) {
                ReportsManager.CriteriaType.PERCENTAGE -> "По процентам"
                ReportsManager.CriteriaType.POINTS -> "По баллам"
            }
            
            // Показываем текущий критерий
            if (criterion.id == currentCriteria?.id) {
                criteriaItem.findViewById<TextView>(R.id.current_indicator).visibility = View.VISIBLE
                criteriaItem.setBackgroundResource(R.drawable.menu_card_bg)
            } else {
                criteriaItem.findViewById<TextView>(R.id.current_indicator).visibility = View.GONE
            }
            
            // Обработчик выбора критерия
            criteriaItem.setOnClickListener {
                reportsManager.setCurrentCriteria(criterion.id)
                Toast.makeText(this, "Критерии изменены: ${criterion.name}", Toast.LENGTH_SHORT).show()
                updateCurrentCriteriaText() // Обновляем текст в боковом меню
                loadReports() // Перезагружаем для обновления оценок
                dialog.dismiss()
            }
            
            criteriaContainer.addView(criteriaItem)
        }
        
        // Кнопка удаления критериев
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_delete_criteria).setOnClickListener {
            val currentCriteria = reportsManager.getCurrentCriteria()
            if (currentCriteria != null && currentCriteria.name != "По процентам (по умолчанию)") {
                AlertDialog.Builder(this)
                    .setTitle("Удаление критериев")
                    .setMessage("Вы уверены, что хотите удалить критерии '${currentCriteria.name}'?")
                    .setPositiveButton("Удалить") { _, _ ->
                        reportsManager.deleteCriteria(currentCriteria.id)
                        Toast.makeText(this, "Критерии удалены", Toast.LENGTH_SHORT).show()
                        updateCurrentCriteriaText()
                        loadReports() // Пересчитываем оценки
                        dialog.dismiss()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            } else {
                Toast.makeText(this, "Нельзя удалить критерии по умолчанию", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Кнопка закрытия
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_close_criteria).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showAddCriteriaDialog() {
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .create()
        
        val dialogView = layoutInflater.inflate(R.layout.add_criteria_dialog, null)
        dialog.setView(dialogView)
        
        // Получаем элементы UI
        val etCriteriaName = dialogView.findViewById<EditText>(R.id.et_criteria_name)
        val rgCriteriaType = dialogView.findViewById<RadioGroup>(R.id.rg_criteria_type)
        val rbPercentage = dialogView.findViewById<RadioButton>(R.id.rb_percentage)
        val rbPoints = dialogView.findViewById<RadioButton>(R.id.rb_points)
        val tvSettingsTitle = dialogView.findViewById<TextView>(R.id.tv_settings_title)
        val etGrade5Min = dialogView.findViewById<EditText>(R.id.et_grade_5_min)
        val etGrade4Min = dialogView.findViewById<EditText>(R.id.et_grade_4_min)
        val etGrade3Min = dialogView.findViewById<EditText>(R.id.et_grade_3_min)
        val etGrade2Min = dialogView.findViewById<EditText>(R.id.et_grade_2_min)
        
        // Обработчик переключения типа критериев
        rgCriteriaType.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.rb_percentage -> {
                    tvSettingsTitle.text = "Настройки по процентам:"
                    etGrade5Min.hint = "90"
                    etGrade4Min.hint = "75"
                    etGrade3Min.hint = "51"
                    etGrade2Min.hint = "0"
                }
                R.id.rb_points -> {
                    tvSettingsTitle.text = "Настройки по баллам:"
                    etGrade5Min.hint = "18"
                    etGrade4Min.hint = "15"
                    etGrade3Min.hint = "10"
                    etGrade2Min.hint = "0"
                }
            }
        }
        
        // Кнопка отмены
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        // Кнопка сохранения
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save).setOnClickListener {
            val name = etCriteriaName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Введите название критериев", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val type = if (rbPercentage.isChecked) ReportsManager.CriteriaType.PERCENTAGE else ReportsManager.CriteriaType.POINTS
            
            try {
                val grade5Min = etGrade5Min.text.toString().toDoubleOrNull() ?: 0.0
                val grade4Min = etGrade4Min.text.toString().toDoubleOrNull() ?: 0.0
                val grade3Min = etGrade3Min.text.toString().toDoubleOrNull() ?: 0.0
                val grade2Min = etGrade2Min.text.toString().toDoubleOrNull() ?: 0.0
                
                // Создаем критерии
                val criteria = mutableMapOf<Int, ClosedRange<Double>>()
                criteria[5] = grade5Min..100.0
                criteria[4] = grade4Min..(grade5Min - 0.1)
                criteria[3] = grade3Min..(grade4Min - 0.1)
                criteria[2] = grade2Min..(grade3Min - 0.1)
                
                val newCriteria = ReportsManager.GradingCriteria(
                    id = System.currentTimeMillis().toString(),
                    name = name,
                    type = type,
                    criteria = criteria,
                    maxPoints = if (type == ReportsManager.CriteriaType.POINTS) 20 else 0
                )
                
                reportsManager.addCriteria(newCriteria)
                reportsManager.setCurrentCriteria(newCriteria.id)
                
                Toast.makeText(this, "Критерии '$name' добавлены", Toast.LENGTH_SHORT).show()
                updateCurrentCriteriaText()
                loadReports() // Пересчитываем оценки
                dialog.dismiss()
                
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка при создании критериев: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.show()
    }
    
    private fun showResetReportsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Сброс всех отчетов")
            .setMessage("Вы уверены, что хотите удалить ВСЕ отчеты? Это действие нельзя отменить.")
            .setPositiveButton("Удалить все") { _, _ ->
                // Удаляем все отчеты
                val reports = reportsManager.getReports()
                reports.forEach { report ->
                    reportsManager.deleteReport(report.id)
                }
                Toast.makeText(this, "Все отчеты удалены", Toast.LENGTH_SHORT).show()
                loadReports()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun updateCurrentCriteriaText() {
        val currentCriteria = reportsManager.getCurrentCriteria()
        val criteriaText = findViewById<TextView>(R.id.current_criteria_text)
        
        if (currentCriteria != null) {
            criteriaText.text = currentCriteria.name
        } else {
            criteriaText.text = "Не выбрано"
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadReports() // Обновляем при возвращении в активность
        updateCurrentCriteriaText()
    }
} 