package com.example.myapplication.reports

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import kotlin.math.roundToInt

class StatisticsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_STATS_CARDS = 1
        private const val TYPE_GRADE_DISTRIBUTION = 2
        private const val TYPE_ERROR_ANALYSIS = 3
        private const val TYPE_QUESTION_HEATMAP = 4
        private const val TYPE_RELATED_ERRORS = 5
        private const val TYPE_IDENTICAL_WORKS = 6
    }
    
    private var statistics: Map<String, Any> = emptyMap()
    
    fun updateStatistics(newStatistics: Map<String, Any>) {
        Log.d("StatisticsAdapter", "📊 updateStatistics() вызван с ${newStatistics.size} элементами")
        Log.d("StatisticsAdapter", "📊 Тепловая карта: ${(newStatistics["questionHeatmap"] as? List<*>)?.size ?: 0} элементов")
        statistics = newStatistics
        Log.d("StatisticsAdapter", "📊 Вызываем notifyDataSetChanged()")
        notifyDataSetChanged()
        Log.d("StatisticsAdapter", "📊 notifyDataSetChanged() завершен")
    }
    
    override fun getItemViewType(position: Int): Int {
        val viewType = when (position) {
            0 -> TYPE_HEADER
            1 -> TYPE_STATS_CARDS
            2 -> TYPE_GRADE_DISTRIBUTION
            3 -> TYPE_ERROR_ANALYSIS
            4 -> TYPE_QUESTION_HEATMAP
            5 -> TYPE_RELATED_ERRORS
            6 -> TYPE_IDENTICAL_WORKS
            else -> TYPE_HEADER
        }
        Log.d("StatisticsAdapter", "📊 getItemViewType() позиция $position -> тип $viewType")
        return viewType
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        Log.d("StatisticsAdapter", "📊 onCreateViewHolder() тип $viewType")
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.statistics_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_STATS_CARDS -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.statistics_cards, parent, false)
                StatsCardsViewHolder(view)
            }
            TYPE_GRADE_DISTRIBUTION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.grade_distribution_chart, parent, false)
                GradeDistributionViewHolder(view)
            }
            TYPE_ERROR_ANALYSIS -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.error_analysis_chart, parent, false)
                ErrorAnalysisViewHolder(view)
            }
            TYPE_QUESTION_HEATMAP -> {
                Log.d("StatisticsAdapter", "🔥 Создаем QuestionHeatmapViewHolder")
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.question_heatmap_chart, parent, false)
                QuestionHeatmapViewHolder(view)
            }
            TYPE_RELATED_ERRORS -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.related_errors_chart, parent, false)
                RelatedErrorsViewHolder(view)
            }
            TYPE_IDENTICAL_WORKS -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.identical_works_chart, parent, false)
                IdenticalWorksViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        Log.d("StatisticsAdapter", "📊 onBindViewHolder() позиция $position, тип ${holder.javaClass.simpleName}")
        when (holder) {
            is HeaderViewHolder -> holder.bind()
            is StatsCardsViewHolder -> holder.bind(statistics)
            is GradeDistributionViewHolder -> holder.bind(statistics["gradeDistribution"] as? Map<Int, Int> ?: emptyMap())
            is ErrorAnalysisViewHolder -> holder.bind(statistics["errorAnalysis"] as? List<Map<String, Any>> ?: emptyList())
            is QuestionHeatmapViewHolder -> {
                Log.d("StatisticsAdapter", "🔥 Вызываем QuestionHeatmapViewHolder.bind()")
                holder.bind(statistics["questionHeatmap"] as? List<Map<String, Any>> ?: emptyList())
            }
            is RelatedErrorsViewHolder -> holder.bind(statistics["relatedErrors"] as? List<Map<String, Any>> ?: emptyList())
            is IdenticalWorksViewHolder -> holder.bind(statistics["identicalWorks"] as? List<Map<String, Any>> ?: emptyList())
        }
    }
    
    override fun getItemCount(): Int = 7
    
    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind() {
            // Заголовок уже установлен в layout
        }
    }
    
    class StatsCardsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardTotalWorks: CardView = itemView.findViewById(R.id.card_total_works)
        private val cardAverageGrade: CardView = itemView.findViewById(R.id.card_average_grade)
        private val cardSuccessRate: CardView = itemView.findViewById(R.id.card_success_rate)
        private val cardBestResult: CardView = itemView.findViewById(R.id.card_best_result)
        
        private val tvTotalWorks: TextView = itemView.findViewById(R.id.tv_total_works_value)
        private val tvAverageGrade: TextView = itemView.findViewById(R.id.tv_average_grade_value)
        private val tvSuccessRate: TextView = itemView.findViewById(R.id.tv_success_rate_value)
        private val tvBestResult: TextView = itemView.findViewById(R.id.tv_best_result_value)
        private val tvSouValue: TextView = itemView.findViewById(R.id.tv_sou_value)
        
        fun bind(statistics: Map<String, Any>) {
            val totalWorks = statistics["totalWorks"] as? Int ?: 0
            val averageGrade = statistics["averageGrade"] as? Double ?: 0.0
            val successRate = statistics["successRate"] as? Double ?: 0.0
            val gradeDistribution = statistics["gradeDistribution"] as? Map<Int, Int> ?: emptyMap()
            
            // Исправляем грамматику
            val worksText = when {
                totalWorks == 0 -> "0 работ"
                totalWorks == 1 -> "1 работа"
                totalWorks < 5 -> "$totalWorks работы"
                else -> "$totalWorks работ"
            }
            
            tvTotalWorks.text = worksText
            tvAverageGrade.text = String.format("%.1f", averageGrade)
            tvSuccessRate.text = String.format("%.0f%%", successRate)
            
            // Расчет Качества знаний (КЗ)
            val grade4Count = gradeDistribution[4] ?: 0
            val grade5Count = gradeDistribution[5] ?: 0
            val qualityKnowledge = if (totalWorks > 0) {
                ((grade4Count + grade5Count).toDouble() / totalWorks) * 100
            } else 0.0
            
            tvBestResult.text = String.format("К/з %.0f%%", qualityKnowledge)
            
            // Расчет Степени обученности учащихся (СОУ)
            val grade2Count = gradeDistribution[2] ?: 0
            val grade3Count = gradeDistribution[3] ?: 0
            val sou = if (totalWorks > 0) {
                ((grade5Count * 1.0) + (grade4Count * 0.64) + (grade3Count * 0.36) + (grade2Count * 0.16)) / totalWorks * 100
            } else 0.0
            
            tvSouValue.text = String.format("СОУ %.0f%%", sou)
            
            // Устанавливаем цвета карточек
            cardTotalWorks.setCardBackgroundColor(Color.parseColor("#99A3BE8C"))
            cardAverageGrade.setCardBackgroundColor(Color.parseColor("#993B82F6"))
            cardSuccessRate.setCardBackgroundColor(Color.parseColor("#99E8B4D2"))
            cardBestResult.setCardBackgroundColor(Color.parseColor("#99DC2626"))
        }
    }
    
    class GradeDistributionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView.findViewById(R.id.grade_distribution_container)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_grade_distribution_title)
        
        fun bind(gradeDistribution: Map<Int, Int>) {
            tvTitle.text = "📊 Распределение оценок"
            container.removeAllViews()
            
            val total = gradeDistribution.values.sum()
            if (total == 0) {
                val emptyText = TextView(itemView.context).apply {
                    text = "Нет данных для отображения"
                    setTextColor(Color.GRAY)
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setPadding(0, 20, 0, 20)
                }
                container.addView(emptyText)
                return
            }
            
            gradeDistribution.entries.sortedBy { it.key }.forEach { (grade, count) ->
                val percentage = if (total > 0) (count.toDouble() / total * 100) else 0.0
                val gradeView = createGradeBar(itemView.context, grade, count, percentage)
                container.addView(gradeView)
            }
        }
        
        private fun createGradeBar(context: android.content.Context, grade: Int, count: Int, percentage: Double): View {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            
            val headerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, 4)
            }
            
            val tvGrade = TextView(context).apply {
                text = "Оценка $grade"
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val tvCount = TextView(context).apply {
                val worksText = when {
                    count == 0 -> "0 работ"
                    count == 1 -> "1 работа"
                    count < 5 -> "$count работы"
                    else -> "$count работ"
                }
                text = "$worksText (${String.format("%.0f", percentage)}%)"
                setTextColor(Color.WHITE)
                textAlignment = View.TEXT_ALIGNMENT_TEXT_END
            }
            
            headerLayout.addView(tvGrade)
            headerLayout.addView(tvCount)
            
            val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = percentage.roundToInt()
                progressTintList = android.content.res.ColorStateList.valueOf(getGradeColor(grade))
                setPadding(0, 4, 0, 0)
            }
            
            layout.addView(headerLayout)
            layout.addView(progressBar)
            
            return layout
        }
        
        private fun getGradeColor(grade: Int): Int {
            return when (grade) {
                2 -> Color.parseColor("#F44336") // Красный
                3 -> Color.parseColor("#FF9800") // Оранжевый
                4 -> Color.parseColor("#FFC107") // Желтый
                5 -> Color.parseColor("#4CAF50") // Зеленый
                else -> Color.GRAY
            }
        }
    }
    
    class ErrorAnalysisViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView.findViewById(R.id.error_analysis_container)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_error_analysis_title)
        
        fun bind(errorAnalysis: List<Map<String, Any>>) {
            tvTitle.text = "❌ Топ-5 самых сложных вопросов"
            container.removeAllViews()
            
            if (errorAnalysis.isEmpty()) {
                val emptyText = TextView(itemView.context).apply {
                    text = "Нет ошибок для анализа"
                    setTextColor(Color.GRAY)
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setPadding(0, 20, 0, 20)
                }
                container.addView(emptyText)
                return
            }
            
            errorAnalysis.forEach { error ->
                val question = error["question"] as? Int ?: 0
                val errors = error["errors"] as? Int ?: 0
                val percentage = error["percentage"] as? Double ?: 0.0
                
                val errorView = createErrorItem(itemView.context, question, errors, percentage)
                container.addView(errorView)
            }
        }
        
        private fun createErrorItem(context: android.content.Context, question: Int, errors: Int, percentage: Double): View {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            
            val tvQuestion = TextView(context).apply {
                text = "Вопрос $question"
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val tvErrors = TextView(context).apply {
                val errorsText = when {
                    errors == 0 -> "0 ошибок"
                    errors == 1 -> "1 ошибка"
                    errors < 5 -> "$errors ошибки"
                    else -> "$errors ошибок"
                }
                text = "$errorsText (${String.format("%.0f", percentage)}%)"
                setTextColor(Color.parseColor("#FF6B6B"))
                textAlignment = View.TEXT_ALIGNMENT_TEXT_END
            }
            
            layout.addView(tvQuestion)
            layout.addView(tvErrors)
            
            return layout
        }
    }
    
    class QuestionHeatmapViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView.findViewById(R.id.question_heatmap_container)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_question_heatmap_title)
        
        fun bind(questionHeatmap: List<Map<String, Any>>) {
            Log.d("StatisticsAdapter", "🔥 QuestionHeatmapViewHolder.bind() вызван с ${questionHeatmap.size} элементами")
            
            tvTitle.text = "🔥 Тепловая карта вопросов"
            container.removeAllViews()
            
            if (questionHeatmap.isEmpty()) {
                Log.w("StatisticsAdapter", "🔥 Тепловая карта пустая, показываем сообщение")
                val emptyText = TextView(itemView.context).apply {
                    text = "Нет данных для отображения"
                    setTextColor(Color.GRAY)
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setPadding(0, 20, 0, 20)
                }
                container.addView(emptyText)
                return
            }
            
            Log.d("StatisticsAdapter", "🔥 Создаем круговую диаграмму для ${questionHeatmap.size} вопросов")
            
            // Создаем круговую диаграмму
            val pieChartView = createPieChart(itemView.context, questionHeatmap)
            container.addView(pieChartView)
            
            // Создаем расшифровку
            val legendView = createLegend(itemView.context, questionHeatmap)
            container.addView(legendView)
            
            Log.d("StatisticsAdapter", "🔥 Круговая диаграмма создана")
        }
        
        private fun createPieChart(context: android.content.Context, questionHeatmap: List<Map<String, Any>>): View {
            val pieChartView = object : View(context) {
                override fun onDraw(canvas: android.graphics.Canvas) {
                    super.onDraw(canvas)
                    
                    val centerX = width / 2f
                    val centerY = height / 2f
                    val radius = minOf(width, height) / 2f - 20f
                    
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.FILL
                    }
                    
                    val totalQuestions = questionHeatmap.size
                    val sweepAngle = 360f / totalQuestions
                    
                    questionHeatmap.forEachIndexed { index, question ->
                        val successRate = question["successRate"] as? Double ?: 0.0
                        val startAngle = index * sweepAngle
                        
                        // Определяем цвет на основе процента успешности
                        val color = when {
                            successRate >= 80 -> Color.parseColor("#4CAF50") // Зеленый
                            successRate >= 60 -> Color.parseColor("#8BC34A") // Светло-зеленый
                            successRate >= 40 -> Color.parseColor("#FFC107") // Желтый
                            successRate >= 20 -> Color.parseColor("#FF9800") // Оранжевый
                            else -> Color.parseColor("#F44336") // Красный
                        }
                        
                        paint.color = color
                        
                        // Рисуем сектор
                        val rect = android.graphics.RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
                        canvas.drawArc(rect, startAngle, sweepAngle, true, paint)
                        
                        // Рисуем границу сектора
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.strokeWidth = 2f
                        paint.color = Color.WHITE
                        canvas.drawArc(rect, startAngle, sweepAngle, true, paint)
                        paint.style = android.graphics.Paint.Style.FILL
                    }
                }
            }
            
            pieChartView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                300
            )
            pieChartView.setPadding(0, 16, 0, 16)
            
            return pieChartView
        }
        
        private fun createLegend(context: android.content.Context, questionHeatmap: List<Map<String, Any>>): View {
            val legendLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 0, 16, 16)
            }
            
            // Группируем вопросы по цветам
            val colorGroups = mutableMapOf<Int, MutableList<Map<String, Any>>>()
            
            questionHeatmap.forEach { question ->
                val successRate = question["successRate"] as? Double ?: 0.0
                val color = when {
                    successRate >= 80 -> Color.parseColor("#4CAF50")
                    successRate >= 60 -> Color.parseColor("#8BC34A")
                    successRate >= 40 -> Color.parseColor("#FFC107")
                    successRate >= 20 -> Color.parseColor("#FF9800")
                    else -> Color.parseColor("#F44336")
                }
                
                colorGroups.getOrPut(color) { mutableListOf() }.add(question)
            }
            
            // Создаем легенду для каждой группы
            colorGroups.forEach { (color, questions) ->
                val groupLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 4, 0, 4)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                
                // Цветной индикатор
                val colorIndicator = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(16, 16)
                    setBackgroundColor(color)
                }
                
                // Текст с вопросами
                val questionsText = questions.joinToString(", ") { 
                    "Вопрос ${it["question"]}" 
                }
                val successRate = questions.first()["successRate"] as? Double ?: 0.0
                
                val legendText = TextView(context).apply {
                    text = "$questionsText (${String.format("%.0f", successRate)}%)"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = 8
                    }
                }
                
                groupLayout.addView(colorIndicator)
                groupLayout.addView(legendText)
                legendLayout.addView(groupLayout)
            }
            
            return legendLayout
        }
    }
    
    class RelatedErrorsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView.findViewById(R.id.related_errors_container)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_related_errors_title)
        
        fun bind(relatedErrors: List<Map<String, Any>>) {
            tvTitle.text = "🔗 Связанные ошибки"
            container.removeAllViews()
            
            if (relatedErrors.isEmpty()) {
                val emptyText = TextView(itemView.context).apply {
                    text = "Нет связанных ошибок для анализа"
                    setTextColor(Color.GRAY)
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setPadding(0, 20, 0, 20)
                }
                container.addView(emptyText)
                return
            }
            
            // Добавляем описание
            val descriptionText = TextView(itemView.context).apply {
                text = "Вопросы, в которых ученики часто ошибаются одновременно"
                setTextColor(Color.parseColor("#B0B0B0"))
                textSize = 12f
                setPadding(0, 0, 0, 16)
            }
            container.addView(descriptionText)
            
            relatedErrors.forEach { error ->
                val question1 = error["question1"] as? Int ?: 0
                val question2 = error["question2"] as? Int ?: 0
                val bothWrong = error["bothWrong"] as? Int ?: 0
                val correlation = error["correlation"] as? Double ?: 0.0
                val totalWorks = error["totalWorks"] as? Int ?: 0
                
                val errorView = createRelatedErrorItem(itemView.context, question1, question2, bothWrong, correlation, totalWorks)
                container.addView(errorView)
            }
        }
        
        private fun createRelatedErrorItem(context: android.content.Context, question1: Int, question2: Int, bothWrong: Int, correlation: Double, totalWorks: Int): View {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            
            val headerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, 4)
            }
            
            val tvQuestions = TextView(context).apply {
                text = "Вопросы $question1 и $question2"
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val tvPercentage = TextView(context).apply {
                text = "${String.format("%.0f", correlation * 100)}% работ"
                setTextColor(Color.parseColor("#FF6B6B"))
                textAlignment = View.TEXT_ALIGNMENT_TEXT_END
            }
            
            headerLayout.addView(tvQuestions)
            headerLayout.addView(tvPercentage)
            
            val detailText = TextView(context).apply {
                text = "Оба вопроса ошибочны в $bothWrong из $totalWorks работ"
                setTextColor(Color.parseColor("#B0B0B0"))
                textSize = 12f
                setPadding(0, 4, 0, 0)
            }
            
            layout.addView(headerLayout)
            layout.addView(detailText)
            
            return layout
        }
    }
    
    class IdenticalWorksViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView.findViewById(R.id.identical_works_container)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_identical_works_title)
        
        fun bind(identicalWorks: List<Map<String, Any>>) {
            tvTitle.text = "🔍 Идентичные работы"
            container.removeAllViews()
            
            if (identicalWorks.isEmpty()) {
                val emptyText = TextView(itemView.context).apply {
                    text = "Нет идентичных работ для анализа"
                    setTextColor(Color.GRAY)
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setPadding(0, 20, 0, 20)
                }
                container.addView(emptyText)
                return
            }
            
            // Добавляем описание
            val descriptionText = TextView(itemView.context).apply {
                text = "Работы с полностью одинаковыми ответами на все вопросы"
                setTextColor(Color.parseColor("#B0B0B0"))
                textSize = 12f
                setPadding(0, 0, 0, 16)
            }
            container.addView(descriptionText)
            
            identicalWorks.forEach { group ->
                val answerPattern = group["answerPattern"] as? String ?: ""
                val worksCount = group["worksCount"] as? Int ?: 0
                val totalWorks = group["totalWorks"] as? Int ?: 0
                val percentage = group["percentage"] as? Double ?: 0.0
                val workNames = group["workNames"] as? List<String> ?: emptyList()
                
                val groupView = createIdenticalWorksGroup(itemView.context, worksCount, totalWorks, percentage, workNames)
                container.addView(groupView)
            }
        }
        
        private fun createIdenticalWorksGroup(context: android.content.Context, worksCount: Int, totalWorks: Int, percentage: Double, workNames: List<String>): View {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            
            val worksText = TextView(context).apply {
                text = "Работы ${workNames.joinToString(", ")} имеют одинаковые ответы"
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 8)
            }
            
            val percentageText = TextView(context).apply {
                text = "${String.format("%.1f", percentage)}% от общего количества"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 12f
                setPadding(0, 4, 0, 0)
            }
            
            layout.addView(worksText)
            layout.addView(percentageText)
            
            return layout
        }
    }
} 