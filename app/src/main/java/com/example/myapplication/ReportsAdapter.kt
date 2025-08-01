package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.reports.ReportsManager
import android.graphics.Color

class ReportsAdapter(
    private var reports: List<ReportsManager.Report>,
    private val onReportClick: (ReportsManager.Report) -> Unit,
    private val onReportDelete: (ReportsManager.Report) -> Unit
) : RecyclerView.Adapter<ReportsAdapter.ReportViewHolder>() {

    class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.report_title)
        val dateText: TextView = itemView.findViewById(R.id.report_date)
        val statsText: TextView = itemView.findViewById(R.id.report_stats)
        val gradeText: TextView = itemView.findViewById(R.id.report_grade)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btn_delete_report)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.report_item, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]
        val omrResult = report.omrResult
        
        val correctCount = omrResult.grading.sum()
        val questionsCount = omrResult.grading.size
        val percentage = if (questionsCount > 0) (correctCount.toFloat() / questionsCount) * 100 else 0f
        
        // Заголовок
        holder.titleText.text = report.title
        
        // Дата
        holder.dateText.text = report.date
        
        // Статистика
        holder.statsText.text = "✅ $correctCount/$questionsCount (${String.format("%.1f", percentage)}%)"
        
        // Оценка с цветовым кодированием
        holder.gradeText.text = "Оценка: ${report.grade}"
        holder.gradeText.setTextColor(getGradeColor(report.grade))
        
        // Обработчики событий
        holder.itemView.setOnClickListener {
            onReportClick(report)
        }
        
        holder.deleteButton.setOnClickListener {
            onReportDelete(report)
        }
    }

    override fun getItemCount(): Int = reports.size

    fun updateReports(newReports: List<ReportsManager.Report>) {
        reports = newReports
        notifyDataSetChanged()
    }
    
    private fun getGradeColor(grade: Int): Int {
        return when (grade) {
            5 -> Color.parseColor("#4CAF50") // Зеленый
            4 -> Color.parseColor("#2196F3") // Синий
            3 -> Color.parseColor("#FF9800") // Оранжевый
            2 -> Color.parseColor("#F44336") // Красный
            else -> Color.GRAY
        }
    }
} 