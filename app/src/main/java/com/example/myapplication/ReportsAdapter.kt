package com.example.myapplication

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.reports.ReportsManager

class ReportsAdapter : RecyclerView.Adapter<ReportsAdapter.ReportViewHolder>() {
    
    private var reports: List<ReportsManager.Report> = emptyList()
    private var onItemClickListener: ((ReportsManager.Report) -> Unit)? = null
    private var onDeleteClickListener: ((ReportsManager.Report) -> Unit)? = null
    
    fun setOnItemClickListener(listener: (ReportsManager.Report) -> Unit) {
        onItemClickListener = listener
    }
    
    fun setOnDeleteClickListener(listener: (ReportsManager.Report) -> Unit) {
        onDeleteClickListener = listener
    }
    
    fun updateReports(newReports: List<ReportsManager.Report>) {
        reports = newReports
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.report_item, parent, false)
        return ReportViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        holder.bind(reports[position])
    }
    
    override fun getItemCount(): Int = reports.size
    
    inner class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.report_card)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_report_title)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_report_date)
        private val tvStats: TextView = itemView.findViewById(R.id.tv_report_stats)
        private val tvGrade: TextView = itemView.findViewById(R.id.tv_report_grade)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_report)
        
        fun bind(report: ReportsManager.Report) {
            tvTitle.text = report.title
            tvDate.text = report.date
            
            val correctCount = report.omrResult.grading.count { it == 1 }
            val incorrectCount = report.omrResult.grading.count { it == 0 }
            val totalQuestions = report.omrResult.grading.size
            val percentage = if (totalQuestions > 0) (correctCount.toDouble() / totalQuestions * 100) else 0.0
            
            tvStats.text = "$correctCount/$totalQuestions (${String.format("%.1f", percentage)}%)"
            tvGrade.text = "Оценка ${report.grade}"
            tvGrade.setTextColor(getGradeColor(report.grade))
            
            // Обработчики кликов
            cardView.setOnClickListener {
                onItemClickListener?.invoke(report)
            }
            
            btnDelete.setOnClickListener {
                onDeleteClickListener?.invoke(report)
            }
        }
        
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
} 