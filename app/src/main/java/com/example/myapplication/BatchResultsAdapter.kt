package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.models.BatchResult

class BatchResultsAdapter(
    private val results: MutableList<BatchResult>,
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
        
        // Устанавливаем информацию
        holder.tvFilename.text = result.filename
        
        // Специальная обработка для ошибок
        if (result.grade == 0) {
            holder.tvScore.text = "ОШИБКА: Контур не найден"
            holder.tvGrade.text = "Оценка: -"
        } else {
            holder.tvScore.text = "${result.correctCount}/${result.totalQuestions} (${String.format("%.1f", result.percentage)}%)"
            holder.tvGrade.text = "Оценка: ${result.grade}"
        }
        
        // Формируем текст ошибок
        val errorText = when {
            result.grade == 0 -> "Контур бланка не найден"
            result.errors.isEmpty() -> "Ошибок нет"
            result.errors.size == 1 -> "Ошибка: Вопрос ${result.errors[0].questionNumber}"
            else -> "Ошибки: ${result.errors.joinToString(", ") { "Вопрос ${it.questionNumber}" }}"
        }
        holder.tvErrors.text = errorText
        
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
} 