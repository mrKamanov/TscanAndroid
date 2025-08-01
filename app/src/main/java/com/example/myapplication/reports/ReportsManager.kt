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
                id = "percentage_default",
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
     * Добавляет критерии оценки
     */
    fun addCriteria(criteria: GradingCriteria) {
        criteriaList.add(criteria)
        saveData()
        Log.d(TAG, "📊 Добавлены критерии: ${criteria.name}")
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
    private fun recalculateAllGrades() {
        reports.forEach { report ->
            val newGrade = calculateGrade(report.omrResult)
            if (newGrade != report.grade) {
                Log.d(TAG, "🔄 Пересчитана оценка для работы ${report.workNumber}: ${report.grade} -> $newGrade")
                report.grade = newGrade
                report.criteriaId = currentCriteriaId
            }
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
                    put("criteria", JSONObject(criteria.criteria.mapKeys { it.key.toString() }))
                }
                criteriaArray.put(criteriaObj)
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