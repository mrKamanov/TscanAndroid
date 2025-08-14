package com.example.myapplication.reports

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.myapplication.models.OMRResult
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Менеджер для работы с отчетами и критериями оценки
 */
class ReportsManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ReportsManager"
        private const val PREFS_NAME = "reports_prefs"
        private const val KEY_REPORTS = "reports"
        private const val KEY_CRITERIA = "criteria"
        private const val KEY_CURRENT_CRITERIA = "current_criteria"
        private const val KEY_WORK_COUNTER = "work_counter"
    }
    
    // Типы критериев оценки
    enum class CriteriaType {
        PERCENTAGE, // По процентам
        POINTS      // По баллам
    }
    
    // Критерии оценки
    data class GradingCriteria(
        val id: String,
        val name: String,
        val type: CriteriaType,
        val criteria: Map<Int, ClosedRange<Double>>, // Оценка -> диапазон
        val maxPoints: Int = 0 // Максимальное количество баллов (для типа POINTS)
    )
    
    // Отчет о работе
    data class Report(
        val id: String,
        val workNumber: Int,
        val title: String,
        val date: String,
        val omrResult: OMRResult,
        var grade: Int,
        var criteriaId: String
    )
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val reports = mutableListOf<Report>()
    private val criteriaList = mutableListOf<GradingCriteria>()
    private var currentCriteriaId: String = ""
    
    init {
        loadData()
        createDefaultCriteria()
        
        // Проверяем, что текущие критерии установлены
        if (currentCriteriaId.isEmpty() && criteriaList.isNotEmpty()) {
            currentCriteriaId = criteriaList.first().id
            Log.d(TAG, "🔧 Установлены критерии по умолчанию: $currentCriteriaId")
        }
    }
    
    /**
     * Создает критерии по умолчанию
     */
    private fun createDefaultCriteria() {
        if (criteriaList.isEmpty()) {
            // Критерии по процентам
            val percentageCriteria = GradingCriteria(
                id = "default",
                name = "По процентам (по умолчанию)",
                type = CriteriaType.PERCENTAGE,
                criteria = mapOf(
                    5 to 90.0..100.0,
                    4 to 75.0..89.99,
                    3 to 51.0..74.99,
                    2 to 0.0..50.99
                )
            )
            
            addCriteria(percentageCriteria)
            setCurrentCriteria(percentageCriteria.id)
        }
    }
    
    /**
     * Добавляет новый отчет
     */
    fun addReport(omrResult: OMRResult, title: String): Report {
        val workNumber = getNextWorkNumber()
        val grade = calculateGrade(omrResult)
        
        Log.d(TAG, "📋 Создаем отчет: $title, текущие критерии: $currentCriteriaId")
        
        val report = Report(
            id = UUID.randomUUID().toString(),
            workNumber = workNumber,
            title = title,
            date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()),
            omrResult = omrResult,
            grade = grade,
            criteriaId = currentCriteriaId
        )
        
        reports.add(report)
        saveData()
        
        Log.d(TAG, "📋 Добавлен отчет: $title (оценка: ${report.grade})")
        return report
    }
    
    /**
     * Получает все отчеты
     */
    fun getReports(): List<Report> = reports.toList()
    
    /**
     * Удаляет отчет
     */
    fun deleteReport(reportId: String): Boolean {
        val removed = reports.removeAll { it.id == reportId }
        if (removed) {
            saveData()
        }
        return removed
    }
    
    /**
     * Обновляет оценку отчета
     */
    fun updateReportGrade(reportId: String, newGrade: Int): Boolean {
        val report = reports.find { it.id == reportId }
        if (report != null) {
            report.grade = newGrade
            saveData()
            Log.d(TAG, "📊 Обновлена оценка отчета $reportId: $newGrade")
            return true
        }
        return false
    }
    
    /**
     * Добавляет новые критерии
     */
    fun addCriteria(criteria: GradingCriteria) {
        criteriaList.add(criteria)
        Log.d(TAG, "📊 Добавляем критерии: ${criteria.name}, тип: ${criteria.type}, критерии: ${criteria.criteria}")
        saveData()
        Log.d(TAG, "📊 Критерии добавлены: ${criteria.name}, всего критериев: ${criteriaList.size}")
    }
    
    /**
     * Получает все критерии
     */
    fun getCriteriaList(): List<GradingCriteria> = criteriaList.toList()
    
    /**
     * Устанавливает текущие критерии
     */
    fun setCurrentCriteria(criteriaId: String) {
        currentCriteriaId = criteriaId
        prefs.edit().putString(KEY_CURRENT_CRITERIA, criteriaId).apply()
        
        // Пересчитываем оценки для всех отчетов
        recalculateAllGrades()
        
        Log.d(TAG, "🔄 Установлены критерии: $criteriaId")
    }
    
    /**
     * Получает текущие критерии
     */
    fun getCurrentCriteria(): GradingCriteria? {
        return criteriaList.find { it.id == currentCriteriaId }
    }
    
    /**
     * Удаляет критерии
     */
    fun deleteCriteria(criteriaId: String): Boolean {
        if (criteriaId == currentCriteriaId) {
            Log.w(TAG, "❌ Нельзя удалить текущие критерии")
            return false
        }
        
        val removed = criteriaList.removeAll { it.id == criteriaId }
        if (removed) {
            saveData()
        }
        return removed
    }
    
    /**
     * Рассчитывает оценку по результатам
     */
    private fun calculateGrade(omrResult: OMRResult): Int {
        val criteria = getCurrentCriteria() ?: return 0
        
        Log.d(TAG, "🔍 Расчет оценки: критерии=${criteria.name}, тип=${criteria.type}")
        
        return when (criteria.type) {
            CriteriaType.PERCENTAGE -> {
                val correctCount = omrResult.grading.sum()
                val questionsCount = omrResult.grading.size
                val percentage = if (questionsCount > 0) (correctCount.toDouble() / questionsCount) * 100 else 0.0
                
                Log.d(TAG, "📊 Процентная оценка: $correctCount/$questionsCount = ${String.format("%.1f", percentage)}%")
                Log.d(TAG, "📊 Критерии: ${criteria.criteria}")
                
                val grade = criteria.criteria.entries.find { (_, range) ->
                    percentage in range
                }?.key ?: 0
                
                Log.d(TAG, "📊 Результат оценки: $grade")
                grade
            }
            
            CriteriaType.POINTS -> {
                val correctCount = omrResult.grading.sum()
                val points = correctCount.toDouble()
                
                Log.d(TAG, "📊 Балльная оценка: $correctCount баллов")
                Log.d(TAG, "📊 Критерии: ${criteria.criteria}")
                
                val grade = criteria.criteria.entries.find { (_, range) ->
                    points in range
                }?.key ?: 0
                
                Log.d(TAG, "📊 Результат оценки: $grade")
                grade
            }
        }
    }
    
    /**
     * Пересчитывает оценки для всех отчетов
     */
    fun recalculateAllGrades() {
        reports.forEach { report ->
            val newGrade = calculateGrade(report.omrResult)
            report.grade = newGrade
            report.criteriaId = currentCriteriaId
        }
        saveData()
    }
    
    /**
     * Получает следующий номер работы
     */
    private fun getNextWorkNumber(): Int {
        val current = prefs.getInt(KEY_WORK_COUNTER, 0)
        val next = current + 1
        prefs.edit().putInt(KEY_WORK_COUNTER, next).apply()
        return next
    }
    
    /**
     * Сохраняет данные
     */
    private fun saveData() {
        try {
            // Сохраняем отчеты
            val reportsArray = JSONArray()
            reports.forEach { report ->
                val reportObj = JSONObject().apply {
                    put("id", report.id)
                    put("workNumber", report.workNumber)
                    put("title", report.title)
                    put("date", report.date)
                    put("grade", report.grade)
                    put("criteriaId", report.criteriaId)
                    put("omrResult", JSONObject().apply {
                        put("selectedAnswers", JSONArray(report.omrResult.selectedAnswers.toList()))
                        put("grading", JSONArray(report.omrResult.grading.toList()))
                        put("correctAnswers", JSONArray(report.omrResult.correctAnswers.toList()))
                        put("incorrectQuestions", JSONArray(report.omrResult.incorrectQuestions.map { 
                            JSONObject(it as Map<*, *>)
                        }))
                    })
                }
                reportsArray.put(reportObj)
            }
            
            // Сохраняем критерии
            val criteriaArray = JSONArray()
            criteriaList.forEach { criteria ->
                val criteriaObj = JSONObject().apply {
                    put("id", criteria.id)
                    put("name", criteria.name)
                    put("type", criteria.type.name)
                    put("maxPoints", criteria.maxPoints)
                    
                    // Сохраняем критерии в правильном формате
                    val criteriaJson = JSONObject()
                    criteria.criteria.forEach { (grade, range) ->
                        criteriaJson.put(grade.toString(), "${range.start}..${range.endInclusive}")
                    }
                    put("criteria", criteriaJson)
                }
                criteriaArray.put(criteriaObj)
                Log.d(TAG, "💾 Сохраняем критерии: ${criteria.name} -> ${criteriaObj}")
            }
            
            prefs.edit()
                .putString(KEY_REPORTS, reportsArray.toString())
                .putString(KEY_CRITERIA, criteriaArray.toString())
                .apply()
                
            Log.d(TAG, "💾 Данные сохранены: ${reports.size} отчетов, ${criteriaList.size} критериев")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сохранения данных: ${e.message}", e)
        }
    }
    
    /**
     * Загружает данные
     */
    private fun loadData() {
        try {
            // Загружаем отчеты
            val reportsJson = prefs.getString(KEY_REPORTS, "[]")
            val reportsArray = JSONArray(reportsJson)
            
            reports.clear()
            for (i in 0 until reportsArray.length()) {
                val reportObj = reportsArray.getJSONObject(i)
                val omrResultObj = reportObj.getJSONObject("omrResult")
                
                val selectedAnswers = omrResultObj.getJSONArray("selectedAnswers").run {
                    IntArray(length()) { getInt(it) }
                }
                val grading = omrResultObj.getJSONArray("grading").run {
                    IntArray(length()) { getInt(it) }
                }
                val correctAnswers = omrResultObj.getJSONArray("correctAnswers").run {
                    List(length()) { getInt(it) }
                }
                val incorrectQuestions = omrResultObj.getJSONArray("incorrectQuestions").run {
                    List(length()) { getJSONObject(it).toMap() }
                }
                
                val omrResult = OMRResult(
                    selectedAnswers = selectedAnswers,
                    grading = grading,
                    correctAnswers = correctAnswers,
                    incorrectQuestions = incorrectQuestions
                )
                
                val report = Report(
                    id = reportObj.getString("id"),
                    workNumber = reportObj.getInt("workNumber"),
                    title = reportObj.getString("title"),
                    date = reportObj.getString("date"),
                    omrResult = omrResult,
                    grade = reportObj.getInt("grade"),
                    criteriaId = reportObj.getString("criteriaId")
                )
                
                reports.add(report)
            }
            
            // Загружаем критерии
            val criteriaJson = prefs.getString(KEY_CRITERIA, "[]")
            val criteriaArray = JSONArray(criteriaJson)
            
            criteriaList.clear()
            for (i in 0 until criteriaArray.length()) {
                val criteriaObj = criteriaArray.getJSONObject(i)
                val criteriaMap = criteriaObj.getJSONObject("criteria").run {
                    keys().asSequence().associate { key ->
                        key.toInt() to parseRange(getString(key))
                    }
                }
                
                val criteria = GradingCriteria(
                    id = criteriaObj.getString("id"),
                    name = criteriaObj.getString("name"),
                    type = CriteriaType.valueOf(criteriaObj.getString("type")),
                    criteria = criteriaMap,
                    maxPoints = criteriaObj.optInt("maxPoints", 0)
                )
                
                criteriaList.add(criteria)
            }
            
            // Загружаем текущие критерии
            currentCriteriaId = prefs.getString(KEY_CURRENT_CRITERIA, "") ?: ""
            
            Log.d(TAG, "📂 Данные загружены: ${reports.size} отчетов, ${criteriaList.size} критериев")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка загрузки данных: ${e.message}", e)
        }
    }
    
    /**
     * Парсит диапазон из строки
     */
    private fun parseRange(rangeStr: String): ClosedRange<Double> {
        val parts = rangeStr.split("..")
        return if (parts.size == 2) {
            parts[0].toDouble()..parts[1].toDouble()
        } else {
            0.0..0.0
        }
    }
    
    /**
     * Принудительно сохраняет данные
     */
    fun forceSave() {
        saveData()
    }

    fun getStatistics(): Map<String, Any> {
        if (reports.isEmpty()) {
            return mapOf(
                "totalWorks" to 0,
                "averageGrade" to 0.0,
                "successRate" to 0.0,
                "gradeDistribution" to mapOf(2 to 0, 3 to 0, 4 to 0, 5 to 0),
                "errorAnalysis" to emptyList<Map<String, Any>>(),
                "questionHeatmap" to emptyList<Map<String, Any>>(),
                "timeProgress" to emptyList<Map<String, Any>>()
            )
        }

        val totalWorks = reports.size
        val averageGrade = reports.map { it.grade }.average()
        val successRate = (reports.count { it.grade >= 3 }.toDouble() / totalWorks) * 100

        // Распределение оценок
        val gradeDistribution = mapOf(
            2 to reports.count { it.grade == 2 },
            3 to reports.count { it.grade == 3 },
            4 to reports.count { it.grade == 4 },
            5 to reports.count { it.grade == 5 }
        )

        // Анализ ошибок по вопросам
        val errorAnalysis = mutableListOf<Map<String, Any>>()
        val questionErrors = mutableMapOf<Int, Int>()
        
        reports.forEach { report ->
            report.omrResult.grading.forEachIndexed { index, isCorrect ->
                if (isCorrect == 0) {
                    questionErrors[index + 1] = questionErrors.getOrDefault(index + 1, 0) + 1
                }
            }
        }
        
        questionErrors.entries.sortedByDescending { it.value }.take(5).forEach { (question, errors) ->
            errorAnalysis.add(mapOf(
                "question" to question,
                "errors" to errors,
                "percentage" to (errors.toDouble() / totalWorks * 100)
            ))
        }

        // Тепловая карта вопросов
        val questionHeatmap = mutableListOf<Map<String, Any>>()
        val totalQuestions = reports.firstOrNull()?.omrResult?.grading?.size ?: 0
        
        Log.d(TAG, "🔥 Создание тепловой карты: $totalQuestions вопросов, $totalWorks работ")
        Log.d(TAG, "🔥 Первый отчет: grading.size = ${reports.firstOrNull()?.omrResult?.grading?.size}")
        Log.d(TAG, "🔥 Все отчеты:")
        reports.forEachIndexed { index, report ->
            Log.d(TAG, "🔥   Отчет $index: grading.size = ${report.omrResult.grading.size}, grading = ${report.omrResult.grading.contentToString()}")
        }
        
        if (totalQuestions > 0) {
            for (question in 1..totalQuestions) {
                val correctAnswers = reports.count { report ->
                    report.omrResult.grading.getOrNull(question - 1) == 1
                }
                val successRate = (correctAnswers.toDouble() / totalWorks) * 100
                
                questionHeatmap.add(mapOf(
                    "question" to question,
                    "successRate" to successRate,
                    "correctAnswers" to correctAnswers,
                    "totalAnswers" to totalWorks
                ))
                
                Log.d(TAG, "🔥 Вопрос $question: $correctAnswers/$totalWorks (${String.format("%.1f", successRate)}%)")
            }
        } else {
            Log.w(TAG, "🔥 Нет вопросов для тепловой карты")
        }
        
        Log.d(TAG, "🔥 Тепловая карта создана: ${questionHeatmap.size} элементов")

        // Динамика результатов по времени
        val timeProgress = reports.sortedBy { it.date }.mapIndexed { index, report ->
            val averageGradeUpToThis = reports.take(index + 1).map { it.grade }.average()
            mapOf(
                "date" to report.date,
                "workNumber" to report.workNumber,
                "averageGrade" to averageGradeUpToThis,
                "totalWorks" to index + 1
            )
        }

        // Анализ связанных ошибок
        val relatedErrors = analyzeRelatedErrors()
        
        // Анализ идентичных работ
        val identicalWorks = analyzeIdenticalWorks()

        return mapOf(
            "totalWorks" to totalWorks,
            "averageGrade" to averageGrade,
            "successRate" to successRate,
            "gradeDistribution" to gradeDistribution,
            "errorAnalysis" to errorAnalysis,
            "questionHeatmap" to questionHeatmap,
            "relatedErrors" to relatedErrors,
            "identicalWorks" to identicalWorks
        )
    }

    /**
     * Анализирует связанные ошибки между вопросами
     */
    private fun analyzeRelatedErrors(): List<Map<String, Any>> {
        val totalWorks = reports.size
        if (totalWorks < 2) return emptyList() // Нужно минимум 2 работы для анализа
        
        val totalQuestions = reports.firstOrNull()?.omrResult?.grading?.size ?: 0
        if (totalQuestions < 2) return emptyList() // Нужно минимум 2 вопроса
        
        val relatedErrors = mutableListOf<Map<String, Any>>()
        
        // Анализируем пары вопросов
        for (q1 in 1..totalQuestions) {
            for (q2 in (q1 + 1)..totalQuestions) {
                var bothWrong = 0
                var q1WrongQ2Right = 0
                var q1RightQ2Wrong = 0
                var bothRight = 0
                
                reports.forEach { report ->
                    val q1Correct = report.omrResult.grading.getOrNull(q1 - 1) == 1
                    val q2Correct = report.omrResult.grading.getOrNull(q2 - 1) == 1
                    
                    when {
                        !q1Correct && !q2Correct -> bothWrong++
                        !q1Correct && q2Correct -> q1WrongQ2Right++
                        q1Correct && !q2Correct -> q1RightQ2Wrong++
                        q1Correct && q2Correct -> bothRight++
                    }
                }
                
                // Если есть связь (оба вопроса часто ошибаются вместе)
                val correlation = bothWrong.toDouble() / totalWorks
                if (correlation >= 0.3) { // Если 30% и больше работ имеют ошибки в обоих вопросах
                    relatedErrors.add(mapOf(
                        "question1" to q1,
                        "question2" to q2,
                        "bothWrong" to bothWrong,
                        "correlation" to correlation,
                        "totalWorks" to totalWorks
                    ))
                }
            }
        }
        
        // Сортируем по силе связи (корреляции)
        return relatedErrors.sortedByDescending { it["correlation"] as Double }.take(5)
    }
    
    /**
     * Анализирует идентичные работы
     */
    private fun analyzeIdenticalWorks(): List<Map<String, Any>> {
        val totalWorks = reports.size
        if (totalWorks < 2) return emptyList() // Нужно минимум 2 работы для анализа
        
        val identicalGroups = mutableMapOf<String, MutableList<Int>>()
        
        // Группируем работы по паттерну ответов
        reports.forEach { report ->
            val answerPattern = report.omrResult.selectedAnswers.contentHashCode().toString()
            
            if (identicalGroups.containsKey(answerPattern)) {
                identicalGroups[answerPattern]!!.add(report.workNumber)
            } else {
                identicalGroups[answerPattern] = mutableListOf(report.workNumber)
            }
        }
        
        // Фильтруем только группы с 2+ работами
        val result = identicalGroups
            .filter { it.value.size >= 2 }
            .map { (pattern, workNumbers) ->
                val worksCount = workNumbers.size
                val percentage = (worksCount.toDouble() / totalWorks) * 100
                
                mapOf(
                    "answerPattern" to pattern,
                    "worksCount" to worksCount,
                    "totalWorks" to totalWorks,
                    "percentage" to percentage,
                    "workNames" to workNumbers.map { it.toString() }
                )
            }
            .sortedByDescending { it["worksCount"] as Int }
            .take(10) // Показываем топ-10 групп
        
        Log.d(TAG, "🔍 Найдено ${result.size} групп идентичных работ")
        return result
    }
}

/**
 * Расширение для конвертации JSONObject в Map
 */
private fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    val keys = this.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        map[key] = this.get(key)
    }
    return map
} 