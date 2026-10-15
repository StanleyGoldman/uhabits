/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.isoron.uhabits.core.io

import kotlinx.coroutines.test.runTest
import org.isoron.platform.io.ZipReader
import org.isoron.platform.io.withDefaultLocale
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.NumericalHabitType
import org.isoron.uhabits.core.models.TargetPeriodMode
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HabitsCSVExporterTest : BaseUnitTest() {

    @BeforeTest
    override fun setUp() {
        super.setUp()
        habitList.add(fixtures.createShortHabit())
        habitList.add(fixtures.createEmptyHabit())
    }

    @Test
    fun testExportCSV() = runTest {
        val selected: MutableList<Habit> = mutableListOf()
        for (h in habitList) selected.add(h)
        val exporter = HabitsCSVExporter(habitList, selected)
        val bytes = exporter.writeArchive()
        assertTrue(bytes.isNotEmpty())

        val entries = ZipReader(bytes).entries()

        val filesToCheck = arrayOf(
            "001 Meditate/Checkmarks.csv",
            "001 Meditate/Scores.csv",
            "002 Wake up early/Checkmarks.csv",
            "002 Wake up early/Scores.csv",
            "Checkmarks.csv",
            "Habits.csv",
            "Scores.csv"
        )

        for (file in filesToCheck) {
            val entry = entries.find { it.name == file }
            assertNotNull(entry, "$file should exist in zip")
            val expected = fileOpener.openResourceFile("csv_export/$file").lines()
            assertEquals(expected, entry.content.trimEnd().lines(), "content mismatch for $file")
        }
    }

    @Test
    fun testExportCSV_measurableCheckmarksArePlainAmounts() = runTest {
        val habit = measurableHabitWithEntries()
        val entries = ZipReader(HabitsCSVExporter(habitList, listOf(habit)).writeArchive()).entries()
        val checkmarks = entries.first { it.name.endsWith(" Run/Checkmarks.csv") }
        assertEquals(
            listOf(
                "Date,Value,Notes",
                "2015-01-25,2.5,",
                "2015-01-24,5000,",
                "2015-01-23,0.003,",
                "2015-01-22,SKIP,",
                "2015-01-21,UNKNOWN,",
                "2015-01-20,0,",
                "2015-01-19,0.002,",
                "2015-01-18,1,",
                "2015-01-17,0.75,"
            ),
            checkmarks.content.trimEnd().lines()
        )
    }

    @Test
    fun testExportCSV_combinedCheckmarksShowMeasurableAmounts() = runTest {
        val habit = measurableHabitWithEntries()
        val entries = ZipReader(HabitsCSVExporter(habitList, listOf(habit)).writeArchive()).entries()
        val checkmarks = entries.first { it.name == "Checkmarks.csv" }
        assertEquals(
            listOf(
                "Date,Run,",
                "2015-01-25,2.5,",
                "2015-01-24,5000,",
                "2015-01-23,0.003,",
                "2015-01-22,SKIP,",
                "2015-01-21,UNKNOWN,",
                "2015-01-20,0,",
                "2015-01-19,0.002,",
                "2015-01-18,1,",
                "2015-01-17,0.75,"
            ),
            checkmarks.content.trimEnd().lines()
        )
    }

    @Test
    fun testExportCSV_measurableAmountsUseDotInEveryLocale() = runTest {
        val habit = measurableHabitWithEntries()
        val bytes = withDefaultLocale("de-DE") {
            HabitsCSVExporter(habitList, listOf(habit)).writeArchive()
        }
        val entries = ZipReader(bytes).entries()
        val perHabit = entries.first { it.name.endsWith(" Run/Checkmarks.csv") }.content.trimEnd().lines()
        assertEquals("2015-01-25,2.5,", perHabit[1])
        assertEquals("2015-01-17,0.75,", perHabit.last())
        val combined = entries.first { it.name == "Checkmarks.csv" }.content.trimEnd().lines()
        assertEquals("2015-01-25,2.5,", combined[1])
        assertEquals("2015-01-17,0.75,", combined.last())
    }

    @Test
    fun testExportCSV_habitsListTargetPeriodAssumeNoneAndSkipReminder() = runTest {
        val rolling = fixtures.createEmptyNumericalHabit(NumericalHabitType.AT_MOST)
        rolling.name = "Rolling"
        rolling.targetPeriodMode = TargetPeriodMode.ROLLING
        rolling.assumeNone = false
        rolling.skipReminderIfLogged = true
        habitList.add(rolling)
        val calendar = fixtures.createEmptyNumericalHabit(NumericalHabitType.AT_LEAST)
        calendar.name = "Calendar"
        habitList.add(calendar)
        val entries = ZipReader(HabitsCSVExporter(habitList, emptyList()).writeArchive()).entries()
        val lines = entries.first { it.name == "Habits.csv" }.content.trimEnd().lines()
        assertEquals(
            "Position,Name,Type,Question,Description,FrequencyNumerator,FrequencyDenominator," +
                "Color,Unit,Target Type,Target Value,Archived?," +
                "Target Period,Assume None,Skip Reminder If Logged",
            lines[0]
        )
        // The last three columns of each row, keyed by habit name
        val settingsByName = lines.drop(1).associate { line ->
            val cols = line.split(",")
            cols[1] to cols.takeLast(3)
        }
        assertEquals(listOf("Rolling", "false", "true"), settingsByName.getValue("Rolling"))
        assertEquals(listOf("Calendar", "true", "false"), settingsByName.getValue("Calendar"))
        assertEquals(listOf("", "", ""), settingsByName.getValue("Meditate"))
    }

    /**
     * A measurable habit whose entries include amounts that the old export
     * mistook for yes-or-no labels: 0.003, 0, 0.002 and 1.
     */
    private fun measurableHabitWithEntries(): Habit {
        val habit = fixtures.createEmptyNumericalHabit(NumericalHabitType.AT_LEAST)
        habitList.add(habit)
        val entries = listOf(
            Entry(LocalDate(2015, 1, 25), Entry.AMOUNT, 2500),
            Entry(LocalDate(2015, 1, 24), Entry.AMOUNT, 5_000_000),
            Entry(LocalDate(2015, 1, 23), Entry.AMOUNT, 3),
            Entry(LocalDate(2015, 1, 22), Entry.SKIP, null),
            Entry(LocalDate(2015, 1, 21), Entry.UNKNOWN, null),
            Entry(LocalDate(2015, 1, 20), Entry.AMOUNT, 0),
            Entry(LocalDate(2015, 1, 19), Entry.AMOUNT, 2),
            Entry(LocalDate(2015, 1, 18), Entry.AMOUNT, 1000),
            Entry(LocalDate(2015, 1, 17), Entry.AMOUNT, 750)
        )
        for (e in entries) habit.originalEntries.add(e)
        habit.recompute()
        return habit
    }
}
