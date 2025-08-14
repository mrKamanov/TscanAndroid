package com.example.myapplication

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.models.BatchResult
import com.example.myapplication.batch.BatchFixedAnswerProcessor

class BatchResultsAdapter(
    private val results: MutableList<BatchResult>,
    private val fixedAnswerProcessor: BatchFixedAnswerProcessor,
    private val onItemClick: (BatchResult) -> Unit
) : RecyclerView.Adapter<BatchResultsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPreview: ImageView = view.findViewById(R.id.iv_preview)
        val tvFilename: TextView = view.findViewById(R.id.tv_filename)
        val tvScore: TextView = view.findViewById(R.id.tv_score)
        val tvGrade: TextView = view.findViewById(R.id.tv_grade)
        val tvErrors: TextView = view.findViewById(R.id.tv_errors)
        val btnDetails: Button = view.findViewById(R.id.btn_details)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.batch_result_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = results[position]
        android.util.Log.d("BatchResultsAdapter", "🔗 Привязываем элемент $position: ${result.filename}")
        
        // Устанавливаем превью - показываем визуализацию контура или сетки
        when {
            result.contourVisualization != null -> {
                holder.ivPreview.setImageBitmap(result.contourVisualization)
            }
            result.gridVisualization != null -> {
                holder.ivPreview.setImageBitmap(result.gridVisualization)
            }
            result.processedImage != null -> {
                holder.ivPreview.setImageBitmap(result.processedImage)
            }
            result.originalImage != null -> {
                holder.ivPreview.setImageBitmap(result.originalImage)
            }
            else -> {
                // Если нет изображения, показываем заглушку
                holder.ivPreview.setImageResource(android.R.drawable.ic_menu_camera)
            }
        }
        
        // Устанавливаем номер работы в заголовке
        holder.tvFilename.text = "Работа ${result.workNumber}"
        
        // Устанавливаем имя файла в подзаголовке
        try {
            val tvFilenameSubtitle = holder.itemView.findViewById<TextView>(R.id.tv_filename_subtitle)
            tvFilenameSubtitle?.text = result.filename
        } catch (e: Exception) {
            // Если элемент не найден, игнорируем
        }
        
        // Специальная обработка для ошибок
        if (result.grade == 0) {
            holder.tvScore.text = "ОШИБКА: Контур не найден"
            holder.tvGrade.text = "Оценка: -"
            holder.tvGrade.setTextColor(holder.itemView.context.getColor(android.R.color.darker_gray))
        } else {
            holder.tvScore.text = "${result.correctCount}/${result.totalQuestions} (${String.format("%.1f", result.percentage)}%)"
            holder.tvGrade.text = "Оценка: ${result.grade}"
            holder.tvGrade.setTextColor(getGradeColor(result.grade))
        }
        
        // Формируем простой статус
        val statusText = when {
            result.grade == 0 -> "Контур не найден"
            result.isAddedToReport -> "В отчете"
            fixedAnswerProcessor.hasFixedAnswers(result.filename) && result.errors.isNotEmpty() -> "Обнаружены исправления"
            fixedAnswerProcessor.hasFixedAnswers(result.filename) -> "Обнаружены исправления"
            result.errors.isNotEmpty() -> "Есть ошибки"
            else -> "Ошибок нет"
        }
        
        holder.tvErrors.text = statusText
        
        // Обработчик клика на кнопку подробностей
        holder.btnDetails.setOnClickListener {
            onItemClick(result)
        }
        
        // Обработчик клика на превью
        holder.ivPreview.setOnClickListener {
            onItemClick(result)
        }
    }

    override fun getItemCount(): Int {
        android.util.Log.d("BatchResultsAdapter", "📊 getItemCount: ${results.size}")
        return results.size
    }

    fun addResult(result: BatchResult) {
        results.add(result)
        notifyItemInserted(results.size - 1)
    }
    
    fun addResults(newResults: List<BatchResult>) {
        val startPosition = results.size
        results.addAll(newResults)
        notifyItemRangeInserted(startPosition, newResults.size)
    }

    fun clearResults() {
        results.clear()
        notifyDataSetChanged()
    }

    fun getResults(): List<BatchResult> = results.toList()
    
    /**
     * Обновляет результат по индексу
     */
    fun updateResult(index: Int, updatedResult: BatchResult) {
        if (index in results.indices) {
            results[index] = updatedResult
            notifyItemChanged(index)
        }
    }
    
    /**
     * Обновляет результат по ID
     */
    fun updateResultById(resultId: String, updatedResult: BatchResult) {
        val index = results.indexOfFirst { it.id == resultId }
        if (index != -1) {
            updateResult(index, updatedResult)
        }
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
} 