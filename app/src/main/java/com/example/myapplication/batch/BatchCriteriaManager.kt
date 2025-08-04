package com.example.myapplication.batch

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class BatchCriteria(
    val id: String,
    val name: String,
    val questions: Int,
    val choices: Int,
    val correctAnswers: List<Int>
)

class BatchCriteriaManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("batch_criteria", Context.MODE_PRIVATE)
    private val criteriaList = mutableListOf<BatchCriteria>()

    init {
        loadCriteria()
    }

    fun addCriteria(criteria: BatchCriteria) {
        criteriaList.add(criteria)
        saveCriteria()
        android.util.Log.d("BatchCriteriaManager", "💾 Добавлены критерии: ${criteria.name}, всего: ${criteriaList.size}")
    }

    fun getCriteriaList(): List<BatchCriteria> {
        android.util.Log.d("BatchCriteriaManager", "📋 Получен список критериев: ${criteriaList.size} элементов")
        criteriaList.forEach { criteria ->
            android.util.Log.d("BatchCriteriaManager", "📋 Критерии: ${criteria.name} (${criteria.questions} вопросов, ${criteria.choices} вариантов)")
        }
        return criteriaList.toList()
    }

    fun getCriteriaById(id: String): BatchCriteria? {
        return criteriaList.find { it.id == id }
    }

    fun deleteCriteria(id: String): Boolean {
        val criteriaToDelete = criteriaList.find { it.id == id }
        val removed = criteriaList.removeAll { it.id == id }
        if (removed) {
            saveCriteria()
            android.util.Log.d("BatchCriteriaManager", "🗑️ Удалены критерии: ${criteriaToDelete?.name}, осталось: ${criteriaList.size}")
        } else {
            android.util.Log.d("BatchCriteriaManager", "❌ Не удалось удалить критерии с ID: $id")
        }
        return removed
    }

    fun updateCriteria(criteria: BatchCriteria): Boolean {
        val index = criteriaList.indexOfFirst { it.id == criteria.id }
        if (index != -1) {
            criteriaList[index] = criteria
            saveCriteria()
            return true
        }
        return false
    }

    private fun saveCriteria() {
        val jsonArray = JSONArray()
        criteriaList.forEach { criteria ->
            val jsonObject = JSONObject().apply {
                put("id", criteria.id)
                put("name", criteria.name)
                put("questions", criteria.questions)
                put("choices", criteria.choices)
                put("correctAnswers", JSONArray(criteria.correctAnswers))
            }
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString("criteria_list", jsonArray.toString()).apply()
        android.util.Log.d("BatchCriteriaManager", "💾 Сохранено ${criteriaList.size} критериев")
    }

    private fun loadCriteria() {
        criteriaList.clear()
        val jsonString = prefs.getString("criteria_list", "[]")
        android.util.Log.d("BatchCriteriaManager", "📥 Загружаем критерии из JSON: $jsonString")
        try {
            val jsonArray = JSONArray(jsonString)
            android.util.Log.d("BatchCriteriaManager", "📥 Найдено ${jsonArray.length()} критериев в JSON")
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val correctAnswersArray = jsonObject.getJSONArray("correctAnswers")
                val correctAnswers = mutableListOf<Int>()
                for (j in 0 until correctAnswersArray.length()) {
                    correctAnswers.add(correctAnswersArray.getInt(j))
                }
                
                val criteria = BatchCriteria(
                    id = jsonObject.getString("id"),
                    name = jsonObject.getString("name"),
                    questions = jsonObject.getInt("questions"),
                    choices = jsonObject.getInt("choices"),
                    correctAnswers = correctAnswers
                )
                criteriaList.add(criteria)
                android.util.Log.d("BatchCriteriaManager", "📥 Загружены критерии: ${criteria.name}")
            }
        } catch (e: Exception) {
            android.util.Log.e("BatchCriteriaManager", "❌ Ошибка загрузки критериев", e)
            e.printStackTrace()
        }
    }
} 