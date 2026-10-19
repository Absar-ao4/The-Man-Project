package com.example.fitapp.feature.extras

import com.example.fitapp.fakes.advice
import com.example.fitapp.fakes.idea
import com.example.fitapp.fakes.weight
import com.example.fitapp.feature.account.domain.DailyRow
import com.example.fitapp.feature.account.domain.ProgressReport
import com.example.fitapp.feature.account.domain.ReportBuilder
import com.example.fitapp.feature.account.domain.ReportCsv
import com.example.fitapp.feature.account.domain.ReportFormat
import com.example.fitapp.feature.account.domain.ReportPeriod
import com.example.fitapp.feature.account.domain.ReportText
import com.example.fitapp.feature.articles.domain.ArticleCategory
import com.example.fitapp.feature.articles.domain.ArticleText
import com.example.fitapp.feature.articles.domain.Markdown
import com.example.fitapp.feature.articles.domain.MdBlock
import com.example.fitapp.feature.articles.domain.MdSpan
import com.example.fitapp.feature.coach.domain.AdviceAction
import com.example.fitapp.feature.coach.domain.CoachText
import com.example.fitapp.feature.coach.domain.MuscleSets
import com.example.fitapp.feature.mealideas.domain.IdeaItem
import com.example.fitapp.feature.mealideas.domain.IdeaSource
import com.example.fitapp.feature.mealideas.domain.IdeaText
import com.example.fitapp.feature.nutrition.domain.DayTotals
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.workout.domain.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MarkdownTest {
    @Test
    fun `headings, paragraphs and lists become blocks`() {
        val blocks =
            Markdown.parse(
                """
                ## The idea

                Muscles get stronger.
                They adapt to what you ask.

                - Add weight
                * Add a rep

                1. First
                2. Second

                ### Small print
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                MdBlock.Heading(2, "The idea"),
                MdBlock.Paragraph("Muscles get stronger. They adapt to what you ask."),
                MdBlock.Bullet("Add weight"),
                MdBlock.Bullet("Add a rep"),
                MdBlock.Numbered(1, "First"),
                MdBlock.Numbered(2, "Second"),
                MdBlock.Heading(3, "Small print"),
            ),
            blocks,
        )
    }

    @Test
    fun `a heading ends a paragraph and a list item ends one too`() {
        val blocks = Markdown.parse("Some text\n## Next\nMore text\n- item\nafter")

        assertEquals(
            listOf(MdBlock.Paragraph("Some text"), MdBlock.Heading(2, "Next"), MdBlock.Paragraph("More text"), MdBlock.Bullet("item"), MdBlock.Paragraph("after")),
            blocks,
        )
    }

    @Test
    fun `empty text has no blocks and odd text stays plain`() {
        assertTrue(Markdown.parse("").isEmpty())
        assertTrue(Markdown.parse("\n\n   \n").isEmpty())
        assertEquals(listOf(MdBlock.Paragraph("#not a heading and 2.5 kg is not a list")), Markdown.parse("#not a heading and 2.5 kg is not a list"))
        assertEquals(listOf(MdBlock.Heading(1, "Top")), Markdown.parse("# Top"))
    }

    @Test
    fun `bold text is split out and the markers disappear`() {
        assertEquals(listOf(MdSpan("Add ", false), MdSpan("weight", true), MdSpan(" slowly", false)), Markdown.inline("Add **weight** slowly"))
        assertEquals(listOf(MdSpan("plain", false)), Markdown.inline("plain"))
        assertEquals(listOf(MdSpan("all bold", true)), Markdown.inline("**all bold**"))
        assertEquals(listOf(MdSpan("one", true), MdSpan(" two ", false), MdSpan("three", true)), Markdown.inline("**one** two **three**"))
    }

    @Test
    fun `an open bold marker bolds the rest and nothing breaks`() {
        assertEquals(listOf(MdSpan("a ", false), MdSpan("b", true)), Markdown.inline("a **b"))
        assertTrue(Markdown.inline("").isEmpty())
    }

    @Test
    fun `the seeded articles' syntax is understood`() {
        val body = "## A simple rule\n\nPick a rep range, for example 8 to 12. Add **2.5 kg** for upper body lifts.\n\n- Add weight.\n- Add a set.\n\n1. **Two to five minutes** of easy cardio."
        val blocks = Markdown.parse(body)

        assertEquals(5, blocks.size)
        assertEquals(MdBlock.Numbered(1, "**Two to five minutes** of easy cardio."), blocks.last())
        assertEquals(MdSpan("Two to five minutes", true), Markdown.inline((blocks.last() as MdBlock.Numbered).text).first())
    }
}

class ReportTest {
    private val zone = ZoneId.of("Asia/Karachi") // UTC+5
    private val from = LocalDate.of(2026, 3, 1)
    private val to = LocalDate.of(2026, 3, 31)

    private fun totals(
        day: Int,
        kcal: Double,
        protein: Double = 100.0,
        water: Int = 6,
    ) = DayTotals(LocalDate.of(2026, 3, day), Macros(kcal, protein, 200.0, 60.0), water, 3)

    private fun session(
        startedAt: String,
        sets: Int = 10,
        volume: Double = 1000.0,
    ) = SessionSummary("s-$startedAt", "Push", Instant.parse(startedAt), Duration.ofMinutes(50), 4, sets, volume, 1)

    @Test
    fun `days are merged and only days with something are listed, oldest first`() {
        val rows =
            ReportBuilder.daily(
                from,
                to,
                weights = listOf(weight(LocalDate.of(2026, 3, 5), 80.4), weight(LocalDate.of(2026, 3, 2), 81.0)),
                nutrition = listOf(totals(5, 2100.0), totals(9, 1900.0)),
                workouts = listOf(session("2026-03-09T10:00:00Z")),
                zone = zone,
            )

        assertEquals(listOf(2, 5, 9), rows.map { it.date.dayOfMonth })
        assertEquals(81.0, rows[0].weightKg!!, 0.0)
        assertNull(rows[0].calories)
        assertEquals(2100.0, rows[1].calories!!, 0.0)
        assertEquals(80.4, rows[1].weightKg!!, 0.0)
        assertEquals(1, rows[2].workouts)
        assertEquals(10, rows[2].sets)
        assertEquals(1000.0, rows[2].volumeKg, 0.0)
    }

    @Test
    fun `a workout counts on the day in the users time zone`() {
        // 22:00 UTC on the 9th is 03:00 on the 10th in Karachi.
        val rows = ReportBuilder.daily(from, to, emptyList(), emptyList(), listOf(session("2026-03-09T22:00:00Z")), zone)

        assertEquals(listOf(10), rows.map { it.date.dayOfMonth })
    }

    @Test
    fun `several workouts on one day add up and days outside the period are dropped`() {
        val rows =
            ReportBuilder.daily(
                from,
                to,
                weights = listOf(weight(LocalDate.of(2026, 2, 28), 79.0)),
                nutrition = listOf(totals(1, 2000.0)),
                workouts = listOf(session("2026-03-09T06:00:00Z", 8, 800.0), session("2026-03-09T12:00:00Z", 6, 600.0), session("2026-04-02T06:00:00Z")),
                zone = zone,
            )

        assertEquals(listOf(1, 9), rows.map { it.date.dayOfMonth })
        assertEquals(2, rows[1].workouts)
        assertEquals(14, rows[1].sets)
        assertEquals(1400.0, rows[1].volumeKg, 0.0)
    }

    @Test
    fun `nothing logged gives no rows`() {
        assertTrue(ReportBuilder.daily(from, to, emptyList(), emptyList(), emptyList(), zone).isEmpty())
    }

    private fun report(vararg rows: DailyRow) = ProgressReport("Sam", to, from, to, imperial = false, rows = rows.toList())

    private fun row(
        day: Int,
        weight: Double? = null,
        kcal: Double? = null,
        protein: Double? = null,
        workouts: Int = 0,
        sets: Int = 0,
        volume: Double = 0.0,
    ) = DailyRow(LocalDate.of(2026, 3, day), weight, kcal, protein, kcal?.let { 200.0 }, kcal?.let { 60.0 }, kcal?.let { 6 }, workouts, sets, volume)

    @Test
    fun `the summary adds up the period`() {
        val r = report(row(1, weight = 82.0, kcal = 2000.0, protein = 100.0, workouts = 1, sets = 10, volume = 1000.0), row(8, weight = 80.5, kcal = 2200.0, protein = 120.0, workouts = 2, sets = 18, volume = 1800.0), row(9, workouts = 0))

        assertEquals(3, r.workouts)
        assertEquals(28, r.sets)
        assertEquals(2800.0, r.volumeKg, 0.0)
        assertEquals(2, r.loggedFoodDays)
        assertEquals(2100.0, r.averageCalories!!, 0.0)
        assertEquals(110.0, r.averageProtein!!, 0.0)
        assertEquals(-1.5, r.weightChangeKg!!, 1e-9)
    }

    @Test
    fun `a period with no weigh-ins or food has no averages or change`() {
        val r = report(row(3, workouts = 1, sets = 5, volume = 300.0))

        assertNull(r.averageCalories)
        assertNull(r.averageProtein)
        assertNull(r.weightChangeKg)
        assertEquals(0, r.loggedFoodDays)
        assertTrue(r.weights.isEmpty())
        assertNull(report(row(3, weight = 80.0)).weightChangeKg)
    }

    @Test
    fun `the csv has a header, a row per day, points for decimals and nothing for missing values`() {
        val csv = ReportCsv.build(listOf(row(1, weight = 82.04, kcal = 2000.4, protein = 100.56, workouts = 1, sets = 10, volume = 1000.0), row(2, workouts = 0)))

        val lines = csv.split("\r\n")
        assertEquals("date,weight_kg,calories,protein_g,carbs_g,fat_g,water_glasses,workouts,sets,volume_kg", lines[0])
        assertEquals("2026-03-01,82.0,2000,100.6,200.0,60.0,6,1,10,1000.0", lines[1])
        assertEquals("2026-03-02,,,,,,,0,0,0.0", lines[2])
        assertEquals("", lines[3]) // the file ends with a line break
        assertEquals(4, lines.size)
    }

    @Test
    fun `an empty report is just the header`() {
        assertEquals(ReportCsv.HEADER + "\r\n", ReportCsv.build(emptyList()))
    }

    @Test
    fun `numbers never use a decimal comma`() {
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        try {
            assertEquals("80.5", ReportCsv.number(80.5, 1))
            assertEquals("", ReportCsv.number(null, 1))
        } finally {
            java.util.Locale.setDefault(java.util.Locale.US)
        }
    }

    @Test
    fun `table cells use the chosen units and leave blanks where nothing happened`() {
        val metric = ReportText.cells(row(1, weight = 80.0, kcal = 2150.4, protein = 99.6, workouts = 1, sets = 10, volume = 1250.0), imperial = false)
        val imperial = ReportText.cells(row(1, weight = 80.0, volume = 1250.0), imperial = true)
        val empty = ReportText.cells(row(2, kcal = null), imperial = false)

        assertEquals(listOf("2026-03-01", "80 kg", "2150", "100 g", "1", "10", "1250 kg"), metric)
        assertEquals("176.4 lb", imperial[1])
        assertEquals("2756 lb", imperial[6])
        assertEquals(listOf("2026-03-02", "", "", "", "", "", ""), empty)
        assertEquals(ReportText.TABLE_HEADER.size, metric.size)
    }

    @Test
    fun `names and titles`() {
        assertEquals("progress-report-2026-03-31.pdf", ReportText.fileName(to, ReportFormat.PDF))
        assertEquals("progress-report-2026-03-31.csv", ReportText.fileName(to, ReportFormat.CSV))
        assertEquals("Progress report, last 90 days", ReportText.title(ReportPeriod.QUARTER))
        assertEquals("-1.5 kg", ReportText.change(-1.5, imperial = false))
        assertEquals("Log two weigh-ins to see a change", ReportText.change(null, imperial = false))
        assertEquals(listOf(30, 90, 365), ReportPeriod.entries.map { it.days })
        assertEquals("application/pdf", ReportFormat.PDF.mimeType)
        assertEquals("text/csv", ReportFormat.CSV.mimeType)
    }
}

class IdeaAndCoachTextTest {
    @Test
    fun `what is left reads well`() {
        assertEquals("850 kcal and 62 g protein left today", IdeaText.remaining(Macros(850.4, 62.2, 90.0, 25.0)))
    }

    @Test
    fun `an idea shows its foods and totals`() {
        val i = idea()

        assertEquals("Chicken breast, raw: 150 g", IdeaText.item(i.items[0]))
        assertEquals("Rice: 82.5 g", IdeaText.item(IdeaItem("rice", "Rice", 82.5, Macros.ZERO)))
        assertEquals("650 kcal, 45 g protein, 70 g carbs, 15 g fat", IdeaText.macros(i.totals))
    }

    @Test
    fun `the source and the tries left are explained`() {
        assertTrue(IdeaText.sourceLabel(IdeaSource.AI).contains("AI"))
        assertTrue(IdeaText.sourceLabel(IdeaSource.RULES).contains("rules"))
        assertEquals("2 AI tries left today", IdeaText.usesLeft(IdeaSource.AI, 2))
        assertEquals("1 AI try left today", IdeaText.usesLeft(IdeaSource.AI, 1))
        assertEquals("That was your last AI try today", IdeaText.usesLeft(IdeaSource.AI, 0))
        assertNull(IdeaText.usesLeft(IdeaSource.RULES, 3))
    }

    @Test
    fun `advice headlines use the chosen units`() {
        assertEquals("Go up to 62.5 kg", CoachText.headline(advice(), imperial = false))
        assertEquals("Go up to 137.8 lb", CoachText.headline(advice(), imperial = true))
        assertEquals("Drop to 54 kg", CoachText.headline(advice(action = AdviceAction.DECREASE, current = 60.0, recommended = 54.0), imperial = false))
        assertEquals("Stay at 60 kg", CoachText.headline(advice(action = AdviceAction.HOLD, recommended = 60.0), imperial = false))
        assertEquals("8 to 12 reps", CoachText.repRange(advice()))
    }

    @Test
    fun `labels and chart scale`() {
        assertEquals("Add weight", CoachText.actionLabel(AdviceAction.INCREASE))
        assertEquals("Deload", CoachText.actionLabel(AdviceAction.DECREASE))
        assertEquals("Keep going", CoachText.actionLabel(AdviceAction.HOLD))
        assertEquals("Chest", CoachText.muscleLabel("CHEST"))
        assertEquals("Rear delts", CoachText.muscleLabel("REAR_DELTS"))
        assertEquals(6, CoachText.maxSets(listOf(MuscleSets("CHEST", 6), MuscleSets("BACK", 2))))
        assertEquals(1, CoachText.maxSets(listOf(MuscleSets("CHEST", 0))))
        assertEquals(1, CoachText.maxSets(emptyList()))
    }

    @Test
    fun `article meta lines`() {
        assertEquals("3 min read", ArticleText.readTime(3))
        assertEquals("Training, 3 min read", ArticleText.meta(ArticleCategory.TRAINING, 3))
        assertEquals("3 min read", ArticleText.meta(null, 3))
        assertEquals("Lock-in safety", ArticleCategory.LOCKIN_SAFETY.label)
    }
}
