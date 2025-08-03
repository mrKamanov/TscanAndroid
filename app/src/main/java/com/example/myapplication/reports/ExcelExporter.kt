package com.example.myapplication.reports

import android.content.Context
import android.util.Log
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.os.Environment

class ExcelExporter(private val context: Context) {
    
    companion object {
        private const val TAG = "ExcelExporter"
    }
    
    /**
     * Экспортирует все отчеты в Excel файл
     */
    fun exportToExcel(reportsManager: ReportsManager): File? {
        try {
            val workbook = XSSFWorkbook()
            
            // Создаем стили
            val headerStyle = createHeaderStyle(workbook)
            val dataStyle = createDataStyle(workbook)
            val titleStyle = createTitleStyle(workbook)
            
            // Лист 1: Общая статистика
            createGeneralStatisticsSheet(workbook, reportsManager, headerStyle, dataStyle, titleStyle)
            
            // Лист 2: Детальные результаты
            createDetailedResultsSheet(workbook, reportsManager, headerStyle, dataStyle, titleStyle)
            
            // Лист 3: Анализ вопросов
            createQuestionAnalysisSheet(workbook, reportsManager, headerStyle, dataStyle, titleStyle)
            
            // Лист 4: Графики
            createChartsSheet(workbook, reportsManager, headerStyle, dataStyle, titleStyle)
            
            // Сохраняем файл
            val fileName = "OMR_Отчет_${SimpleDateFormat("dd_MM_yyyy_HH_mm", Locale.getDefault()).format(Date())}.xlsx"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            
            // Создаем папку, если её нет
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            FileOutputStream(file).use { fos ->
                workbook.write(fos)
            }
            workbook.close()
            
            Log.d(TAG, "✅ Excel файл создан: ${file.absolutePath}")
            return file
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка при создании Excel файла", e)
            return null
        }
    }
    
    private fun createHeaderStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.color = IndexedColors.WHITE.index
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.DARK_BLUE.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.borderBottom = BorderStyle.THIN
        style.borderTop = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.alignment = HorizontalAlignment.CENTER
        return style
    }
    
    private fun createDataStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        style.borderBottom = BorderStyle.THIN
        style.borderTop = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.alignment = HorizontalAlignment.CENTER
        return style
    }
    
    private fun createTitleStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.fontHeightInPoints = 14
        style.setFont(font)
        style.alignment = HorizontalAlignment.CENTER
        return style
    }
    
    private fun createGeneralStatisticsSheet(workbook: Workbook, reportsManager: ReportsManager, headerStyle: CellStyle, dataStyle: CellStyle, titleStyle: CellStyle) {
        val sheet = workbook.createSheet("Общая статистика")
        val statistics = reportsManager.getStatistics()
        
        var rowNum = 0
        
        // Заголовок
        val titleRow = sheet.createRow(rowNum++)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("📊 ОБЩАЯ СТАТИСТИКА РАБОТ")
        titleCell.cellStyle = titleStyle
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 3))
        
        rowNum++ // Пропуск строки
        
        // Основные показатели
        val totalWorks = statistics["totalWorks"] as? Int ?: 0
        val averageGrade = statistics["averageGrade"] as? Double ?: 0.0
        val successRate = statistics["successRate"] as? Double ?: 0.0
        val gradeDistribution = statistics["gradeDistribution"] as? Map<Int, Int> ?: emptyMap()
        
        // Расчет КЗ и СОУ
        val grade4Count = gradeDistribution[4] ?: 0
        val grade5Count = gradeDistribution[5] ?: 0
        val grade2Count = gradeDistribution[2] ?: 0
        val grade3Count = gradeDistribution[3] ?: 0
        
        val qualityKnowledge = if (totalWorks > 0) {
            ((grade4Count + grade5Count).toDouble() / totalWorks) * 100
        } else 0.0
        
        val sou = if (totalWorks > 0) {
            ((grade5Count * 1.0) + (grade4Count * 0.64) + (grade3Count * 0.36) + (grade2Count * 0.16)) / totalWorks * 100
        } else 0.0
        
        // Таблица основных показателей
        val metricsData = listOf(
            listOf("Показатель", "Значение"),
            listOf("Общее количество работ", totalWorks.toString()),
            listOf("Средний балл", String.format("%.1f", averageGrade)),
            listOf("Процент успешности", String.format("%.0f%%", successRate)),
            listOf("Качество знаний (КЗ)", String.format("%.0f%%", qualityKnowledge)),
            listOf("Степень обученности (СОУ)", String.format("%.0f%%", sou))
        )
        
        metricsData.forEachIndexed { index, rowData ->
            val row = sheet.createRow(rowNum++)
            rowData.forEachIndexed { colIndex, value ->
                val cell = row.createCell(colIndex)
                cell.setCellValue(value)
                cell.cellStyle = if (index == 0) headerStyle else dataStyle
            }
        }
        
        rowNum++ // Пропуск строки
        
        // Распределение оценок
        val gradeRow = sheet.createRow(rowNum++)
        val gradeCell = gradeRow.createCell(0)
        gradeCell.setCellValue("📊 РАСПРЕДЕЛЕНИЕ ОЦЕНОК")
        gradeCell.cellStyle = titleStyle
        sheet.addMergedRegion(CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3))
        
        rowNum++
        
        val gradeHeaderRow = sheet.createRow(rowNum++)
        val gradeHeaders = listOf("Оценка", "Количество", "Процент")
        gradeHeaders.forEachIndexed { index, header ->
            val cell = gradeHeaderRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }
        
        gradeDistribution.entries.sortedBy { it.key }.forEach { (grade, count) ->
            val percentage = if (totalWorks > 0) (count.toDouble() / totalWorks * 100) else 0.0
            val row = sheet.createRow(rowNum++)
            
            val gradeCell = row.createCell(0)
            gradeCell.setCellValue(grade.toDouble())
            gradeCell.cellStyle = dataStyle
            
            val countCell = row.createCell(1)
            countCell.setCellValue(count.toDouble())
            countCell.cellStyle = dataStyle
            
            val percentCell = row.createCell(2)
            percentCell.setCellValue(String.format("%.1f%%", percentage))
            percentCell.cellStyle = dataStyle
        }
        
        // Устанавливаем фиксированную ширину столбцов
        sheet.setColumnWidth(0, 6000) // Показатель - шире
        sheet.setColumnWidth(1, 3000) // Значение
        sheet.setColumnWidth(2, 2000) // Оценка
        sheet.setColumnWidth(3, 3000) // Количество
        sheet.setColumnWidth(4, 2000) // Процент
    }
    
    private fun createDetailedResultsSheet(workbook: Workbook, reportsManager: ReportsManager, headerStyle: CellStyle, dataStyle: CellStyle, titleStyle: CellStyle) {
        val sheet = workbook.createSheet("Детальные результаты")
        val reports = reportsManager.getReports()
        
        var rowNum = 0
        
        // Заголовок
        val titleRow = sheet.createRow(rowNum++)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("📋 ДЕТАЛЬНЫЕ РЕЗУЛЬТАТЫ ВСЕХ РАБОТ")
        titleCell.cellStyle = titleStyle
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 6))
        
        rowNum++
        
        // Заголовки таблицы
        val headerRow = sheet.createRow(rowNum++)
        val headers = listOf("№", "Название", "Дата", "Правильно", "Всего", "Процент", "Оценка")
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }
        
        // Данные работ
        reports.forEach { report ->
            val row = sheet.createRow(rowNum++)
            
            val correctAnswers = report.omrResult.grading.count { it == 1 }
            val totalQuestions = report.omrResult.grading.size
            val percentage = if (totalQuestions > 0) (correctAnswers.toDouble() / totalQuestions * 100) else 0.0
            
            row.createCell(0).apply { setCellValue(report.workNumber.toDouble()); cellStyle = dataStyle }
            row.createCell(1).apply { setCellValue(report.title); cellStyle = dataStyle }
            row.createCell(2).apply { setCellValue(report.date); cellStyle = dataStyle }
            row.createCell(3).apply { setCellValue(correctAnswers.toDouble()); cellStyle = dataStyle }
            row.createCell(4).apply { setCellValue(totalQuestions.toDouble()); cellStyle = dataStyle }
            row.createCell(5).apply { setCellValue(String.format("%.1f%%", percentage)); cellStyle = dataStyle }
            row.createCell(6).apply { setCellValue(report.grade.toDouble()); cellStyle = dataStyle }
        }
        
        // Устанавливаем фиксированную ширину столбцов
        sheet.setColumnWidth(0, 1000) // №
        sheet.setColumnWidth(1, 4000) // Название
        sheet.setColumnWidth(2, 5000) // Дата - шире
        sheet.setColumnWidth(3, 2000) // Правильно
        sheet.setColumnWidth(4, 2000) // Всего
        sheet.setColumnWidth(5, 2000) // Процент
        sheet.setColumnWidth(6, 1500) // Оценка
    }
    
    private fun createQuestionAnalysisSheet(workbook: Workbook, reportsManager: ReportsManager, headerStyle: CellStyle, dataStyle: CellStyle, titleStyle: CellStyle) {
        val sheet = workbook.createSheet("Анализ вопросов")
        val statistics = reportsManager.getStatistics()
        
        var rowNum = 0
        
        // Заголовок
        val titleRow = sheet.createRow(rowNum++)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("🔥 АНАЛИЗ ВОПРОСОВ")
        titleCell.cellStyle = titleStyle
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 4))
        
        rowNum++
        
        // Тепловая карта вопросов
        val heatmapTitleRow = sheet.createRow(rowNum++)
        val heatmapTitleCell = heatmapTitleRow.createCell(0)
        heatmapTitleCell.setCellValue("🔥 Тепловая карта вопросов")
        heatmapTitleCell.cellStyle = titleStyle
        sheet.addMergedRegion(CellRangeAddress(rowNum - 1, rowNum - 1, 0, 4))
        
        rowNum++
        
        val heatmapHeaderRow = sheet.createRow(rowNum++)
        val heatmapHeaders = listOf("Вопрос", "Правильно", "Всего", "Процент успешности", "Статус")
        heatmapHeaders.forEachIndexed { index, header ->
            val cell = heatmapHeaderRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }
        
        val questionHeatmap = statistics["questionHeatmap"] as? List<Map<String, Any>> ?: emptyList()
        questionHeatmap.forEach { question ->
            val row = sheet.createRow(rowNum++)
            
            val questionNum = question["question"] as? Int ?: 0
            val correctAnswers = question["correctAnswers"] as? Int ?: 0
            val totalAnswers = question["totalAnswers"] as? Int ?: 0
            val successRate = question["successRate"] as? Double ?: 0.0
            
            val status = when {
                successRate >= 80 -> "Отлично"
                successRate >= 60 -> "Хорошо"
                successRate >= 40 -> "Удовлетворительно"
                successRate >= 20 -> "Плохо"
                else -> "Очень плохо"
            }
            
            row.createCell(0).apply { setCellValue(questionNum.toDouble()); cellStyle = dataStyle }
            row.createCell(1).apply { setCellValue(correctAnswers.toDouble()); cellStyle = dataStyle }
            row.createCell(2).apply { setCellValue(totalAnswers.toDouble()); cellStyle = dataStyle }
            row.createCell(3).apply { setCellValue(String.format("%.1f%%", successRate)); cellStyle = dataStyle }
            row.createCell(4).apply { setCellValue(status); cellStyle = dataStyle }
        }
        
        rowNum += 2 // Пропуск строк
        
        // Связанные ошибки
        val relatedTitleRow = sheet.createRow(rowNum++)
        val relatedTitleCell = relatedTitleRow.createCell(0)
        relatedTitleCell.setCellValue("🔗 Связанные ошибки")
        relatedTitleCell.cellStyle = titleStyle
        sheet.addMergedRegion(CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3))
        
        rowNum++
        
        val relatedHeaderRow = sheet.createRow(rowNum++)
        val relatedHeaders = listOf("Вопрос 1", "Вопрос 2", "Оба ошибочны", "Процент работ")
        relatedHeaders.forEachIndexed { index, header ->
            val cell = relatedHeaderRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }
        
        val relatedErrors = statistics["relatedErrors"] as? List<Map<String, Any>> ?: emptyList()
        relatedErrors.forEach { error ->
            val row = sheet.createRow(rowNum++)
            
            val question1 = error["question1"] as? Int ?: 0
            val question2 = error["question2"] as? Int ?: 0
            val bothWrong = error["bothWrong"] as? Int ?: 0
            val correlation = error["correlation"] as? Double ?: 0.0
            
            row.createCell(0).apply { setCellValue(question1.toDouble()); cellStyle = dataStyle }
            row.createCell(1).apply { setCellValue(question2.toDouble()); cellStyle = dataStyle }
            row.createCell(2).apply { setCellValue(bothWrong.toDouble()); cellStyle = dataStyle }
            row.createCell(3).apply { setCellValue(String.format("%.1f%%", correlation * 100)); cellStyle = dataStyle }
        }
        
        // Устанавливаем фиксированную ширину столбцов
        sheet.setColumnWidth(0, 1500) // Вопрос
        sheet.setColumnWidth(1, 2000) // Правильно
        sheet.setColumnWidth(2, 2000) // Всего
        sheet.setColumnWidth(3, 3000) // Процент успешности
        sheet.setColumnWidth(4, 4000) // Статус - шире
    }
    
    private fun createChartsSheet(workbook: Workbook, reportsManager: ReportsManager, headerStyle: CellStyle, dataStyle: CellStyle, titleStyle: CellStyle) {
        val sheet = workbook.createSheet("Данные для графиков")
        val statistics = reportsManager.getStatistics()
        
        var rowNum = 0
        
        // Заголовок
        val titleRow = sheet.createRow(rowNum++)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("📈 ДАННЫЕ ДЛЯ СОЗДАНИЯ ГРАФИКОВ")
        titleCell.cellStyle = titleStyle
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 4))
        
        rowNum++
        
        // Данные для графиков
        val gradeDistribution = statistics["gradeDistribution"] as? Map<Int, Int> ?: emptyMap()
        val totalWorks = statistics["totalWorks"] as? Int ?: 0
        
        // Данные для круговой диаграммы оценок
        val chartDataRow = sheet.createRow(rowNum++)
        chartDataRow.createCell(0).setCellValue("Оценка")
        chartDataRow.createCell(1).setCellValue("Количество")
        chartDataRow.createCell(2).setCellValue("Процент")
        
        gradeDistribution.entries.sortedBy { it.key }.forEach { (grade, count) ->
            val percentage = if (totalWorks > 0) (count.toDouble() / totalWorks * 100) else 0.0
            val row = sheet.createRow(rowNum++)
            
            row.createCell(0).setCellValue("Оценка $grade")
            row.createCell(1).setCellValue(count.toDouble())
            row.createCell(2).setCellValue(percentage)
        }
        
        // Устанавливаем фиксированную ширину столбцов
        sheet.setColumnWidth(0, 2000) // Оценка
        sheet.setColumnWidth(1, 2000) // Количество
        sheet.setColumnWidth(2, 2000) // Процент
    }
} 