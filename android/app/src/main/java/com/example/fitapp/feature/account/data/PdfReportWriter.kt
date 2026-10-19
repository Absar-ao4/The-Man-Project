package com.example.fitapp.feature.account.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.fitapp.feature.account.domain.ProgressReport
import com.example.fitapp.feature.account.domain.ReportText
import com.example.fitapp.feature.body.domain.UnitFormat
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Draws a progress report on an A4 PDF with Android's own PdfDocument: a summary, a weight chart and a table of the days
 * that have something in them, over as many pages as it takes. No library is involved.
 */
internal object PdfReportWriter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val ROW_HEIGHT = 15f
    private const val CHART_HEIGHT = 130f

    private val ink = Color.rgb(22, 24, 29)
    private val muted = Color.rgb(94, 97, 106)
    private val line = Color.rgb(227, 227, 220)
    private val blue = Color.rgb(61, 90, 254)

    private fun paint(
        size: Float,
        bold: Boolean = false,
        color: Int = ink,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    fun write(
        report: ProgressReport,
        title: String,
        out: File,
    ) {
        val document = PdfDocument()
        try {
            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            var canvas = page.canvas
            var y = MARGIN + 20f

            canvas.drawText(title, MARGIN, y, paint(20f, bold = true))
            y += 20f
            val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            val period = "${report.from.format(format)} to ${report.to.format(format)}"
            canvas.drawText(listOfNotNull(report.name.takeIf { it.isNotBlank() }, period).joinToString(", "), MARGIN, y, paint(11f, color = muted))
            y += 14f
            canvas.drawText("Made on ${report.generatedOn.format(format)} from the data on this phone", MARGIN, y, paint(11f, color = muted))
            y += 30f

            canvas.drawText("Summary", MARGIN, y, paint(14f, bold = true))
            y += 20f
            val body = paint(11f)
            summaryLines(report).forEach {
                canvas.drawText(it, MARGIN, y, body)
                y += 16f
            }
            y += 10f

            canvas.drawText("Weight", MARGIN, y, paint(14f, bold = true))
            y += 10f
            drawChart(canvas, report, y)
            y += CHART_HEIGHT + 36f

            canvas.drawText("Days", MARGIN, y, paint(14f, bold = true))
            y += 18f
            y = drawHeader(canvas, y)

            val cell = paint(9f)
            for (row in report.rows) {
                if (y > PAGE_HEIGHT - MARGIN - 20f) {
                    footer(canvas, pageNumber)
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                    canvas = page.canvas
                    y = MARGIN + 10f
                    y = drawHeader(canvas, y)
                }
                ReportText.cells(row, report.imperial).forEachIndexed { i, text -> canvas.drawText(text, MARGIN + COLUMNS[i], y, cell) }
                y += ROW_HEIGHT
            }
            if (report.rows.isEmpty()) {
                canvas.drawText("Nothing was logged in this period.", MARGIN, y + 6f, paint(10f, color = muted))
            }
            footer(canvas, pageNumber)
            document.finishPage(page)
            out.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    /** The left edge of each table column, in points from the margin. */
    private val COLUMNS = floatArrayOf(0f, 80f, 150f, 215f, 280f, 340f, 395f)

    private fun drawHeader(
        canvas: Canvas,
        y: Float,
    ): Float {
        val header = paint(9f, bold = true, color = muted)
        ReportText.TABLE_HEADER.forEachIndexed { i, text -> canvas.drawText(text, MARGIN + COLUMNS[i], y, header) }
        val rule = Paint().apply {
            color = line
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN, y + 5f, PAGE_WIDTH - MARGIN, y + 5f, rule)
        return y + ROW_HEIGHT + 4f
    }

    private fun summaryLines(report: ProgressReport): List<String> {
        val lines = mutableListOf<String>()
        lines += "Workouts: ${report.workouts}, sets: ${report.sets}, volume: ${ReportText.volume(report.volumeKg, report.imperial)}"
        lines +=
            report.averageCalories?.let {
                "Food logged on ${report.loggedFoodDays} days, on average ${Math.round(it)} kcal and ${Math.round(report.averageProtein ?: 0.0)} g protein"
            } ?: "No food was logged in this period"
        val weights = report.weights
        lines +=
            if (weights.isEmpty()) {
                "No weigh-ins in this period"
            } else {
                "Weight: ${UnitFormat.weight(weights.first().second, report.imperial)} to ${UnitFormat.weight(weights.last().second, report.imperial)} (${ReportText.change(report.weightChangeKg, report.imperial)})"
            }
        return lines
    }

    private fun drawChart(
        canvas: Canvas,
        report: ProgressReport,
        top: Float,
    ) {
        val left = MARGIN + 36f
        val right = PAGE_WIDTH - MARGIN
        val bottom = top + CHART_HEIGHT
        val frame = Paint().apply {
            color = line
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRect(left, top, right, bottom, frame)
        val points = report.weights.map { (date, kg) -> date.toEpochDay() to (if (report.imperial) UnitFormat.kgToLb(kg) else kg) }
        if (points.isEmpty()) return
        val minY = points.minOf { it.second } - 1.0
        val maxY = points.maxOf { it.second } + 1.0
        val minX = points.first().first
        val spanX = (points.last().first - minX).coerceAtLeast(1L)
        fun px(day: Long) = if (points.size == 1) (left + right) / 2f else left + (day - minX).toFloat() / spanX * (right - left)

        fun py(value: Double) = (bottom - ((value - minY) / (maxY - minY)).toFloat() * CHART_HEIGHT)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = blue
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        if (points.size >= 2) {
            val path = Path()
            points.forEachIndexed { i, p -> if (i == 0) path.moveTo(px(p.first), py(p.second)) else path.lineTo(px(p.first), py(p.second)) }
            canvas.drawPath(path, stroke)
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue }
        points.forEach { canvas.drawCircle(px(it.first), py(it.second), 3f, dot) }
        val label = paint(8f, color = muted)
        canvas.drawText(String.format(java.util.Locale.US, "%.1f", maxY), MARGIN, top + 8f, label)
        canvas.drawText(String.format(java.util.Locale.US, "%.1f", minY), MARGIN, bottom, label)
    }

    private fun footer(
        canvas: Canvas,
        page: Int,
    ) {
        canvas.drawText("The Man Project, page $page", MARGIN, PAGE_HEIGHT - 24f, paint(8f, color = muted))
    }
}