package com.example.fitapp.feature

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitapp.feature.account.data.PdfReportWriter
import com.example.fitapp.feature.account.domain.DailyRow
import com.example.fitapp.feature.account.domain.ProgressReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** The PDF is drawn with Android's own PdfDocument, so it is checked on a device. */
@RunWith(AndroidJUnit4::class)
class PdfReportWriterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val from = LocalDate.of(2026, 1, 1)

    private fun row(day: Long) =
        DailyRow(from.plusDays(day), 80.0 - day * 0.05, 2100.0, 120.0, 250.0, 70.0, 7, if (day % 3 == 0L) 1 else 0, if (day % 3 == 0L) 12 else 0, if (day % 3 == 0L) 2400.0 else 0.0)

    private fun report(rows: List<DailyRow>, imperial: Boolean = false) = ProgressReport("Sam", from.plusDays(89), from, from.plusDays(89), imperial, rows)

    private fun pages(file: File): Int = Regex("/Type\\s*/Page\\b").findAll(file.readText(Charsets.ISO_8859_1)).count()

    @Test
    fun aReportIsAValidPdfWithAtLeastOnePage() {
        val file = folder.newFile("report.pdf")

        PdfReportWriter.write(report((0L until 10L).map(::row)), "Progress report, last 30 days", file)

        val bytes = file.readBytes()
        assertTrue(bytes.size > 1_000)
        assertEquals("%PDF", String(bytes, 0, 4, Charsets.ISO_8859_1))
        assertTrue(pages(file) >= 1)
    }

    @Test
    fun manyDaysRunOverSeveralPages() {
        val few = folder.newFile("few.pdf")
        val many = folder.newFile("many.pdf")

        PdfReportWriter.write(report((0L until 5L).map(::row)), "Report", few)
        PdfReportWriter.write(report((0L until 90L).map(::row)), "Report", many)

        assertTrue("90 rows need more than one page", pages(many) > pages(few))
    }

    @Test
    fun anEmptyReportAndImperialUnitsStillWork() {
        val empty = folder.newFile("empty.pdf")
        val imperial = folder.newFile("imperial.pdf")

        PdfReportWriter.write(report(emptyList()), "Report", empty)
        PdfReportWriter.write(report((0L until 8L).map(::row), imperial = true), "Report", imperial)

        assertTrue(empty.length() > 500)
        assertTrue(imperial.length() > 500)
    }
}
