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
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitType
import org.isoron.uhabits.core.models.TargetPeriodMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportTest : BaseUnitTest() {

    @Test
    fun testHabitBullCSV() = runTest {
        importFromFile("habitbull.csv")
        assertEquals(4, habitList.size())
        val habit = habitList.getByPosition(0)
        assertEquals("Breed dragons", habit.name)
        assertEquals("with love and fire", habit.description)
        assertEquals(Frequency.DAILY, habit.frequency)
        assertTrue(isChecked(habit, 2016, 3, 18))
        assertTrue(isChecked(habit, 2016, 3, 19))
        assertFalse(isChecked(habit, 2016, 3, 20))
        assertTrue(isNotesEqual(habit, 2016, 3, 18, "text"))
    }

    @Test
    fun testHabitBullCSV2() = runTest {
        importFromFile("habitbull2.csv")
        assertEquals(6, habitList.size())
        val habit = habitList.getByPosition(2)
        assertEquals("H3", habit.name)
        assertEquals("Habit 3", habit.description)
        assertEquals(Frequency.DAILY, habit.frequency)
        assertTrue(isChecked(habit, 2019, 4, 11))
        assertTrue(isChecked(habit, 2019, 5, 7))
        assertFalse(isChecked(habit, 2019, 6, 14))
        assertTrue(isNotesEqual(habit, 2019, 4, 11, "text"))
        assertTrue(isNotesEqual(habit, 2019, 6, 14, "Habit 3 notes"))
    }

    @Test
    fun testHabitBullCSV3() = runTest {
        importFromFile("habitbull3.csv")
        assertEquals(2, habitList.size())

        val habit = habitList.getByPosition(0)
        assertEquals("Pushups", habit.name)
        assertEquals(HabitType.NUMERICAL, habit.type)
        assertEquals("", habit.description)
        assertEquals(Frequency.DAILY, habit.frequency)
        assertEquals(30000, getValue(habit, 2021, 9, 1))
        assertEquals(100000, getValue(habit, 2022, 1, 8))

        val habit2 = habitList.getByPosition(1)
        assertEquals("run", habit2.name)
        assertEquals(HabitType.YES_NO, habit2.type)
        assertEquals("", habit2.description)
        assertEquals(Frequency.DAILY, habit2.frequency)
        assertTrue(isChecked(habit2, 2022, 1, 3))
        assertTrue(isChecked(habit2, 2022, 1, 18))
        assertTrue(isChecked(habit2, 2022, 1, 19))
    }

    @Test
    fun testHabitBullCSV4() = runTest {
        importFromFile("habitbull4.csv")
        assertEquals(1, habitList.size())

        val habit = habitList.getByPosition(0)
        assertEquals("Caffeine", habit.name)
        assertEquals(HabitType.NUMERICAL, habit.type)
        assertEquals("", habit.description)
        assertEquals(Frequency.DAILY, habit.frequency)
        assertEquals(80000, getValue(habit, 2022, 11, 21))
        assertEquals(80000, getValue(habit, 2022, 11, 22))
    }

    @Test
    fun testHabitBullCSVDecidesTypeBeforeWritingEntries() = runTest {
        importFromFile("habitbull5.csv")
        assertEquals(2, habitList.size())

        val water = habitList.first { it.name == "Water" }
        assertEquals(HabitType.NUMERICAL, water.type)
        fun waterEntry(day: Int) = water.originalEntries.get(LocalDate(2023, 5, day))
        assertEquals(Entry(LocalDate(2023, 5, 1), Entry.AMOUNT, 1000, "first"), waterEntry(1))
        assertEquals(Entry(LocalDate(2023, 5, 2), Entry.AMOUNT, 3000), waterEntry(2))
        assertEquals(Entry(LocalDate(2023, 5, 3), Entry.AMOUNT, 0), waterEntry(3))
        assertEquals(Entry(LocalDate(2023, 5, 4), Entry.AMOUNT, 1000, "last"), waterEntry(4))

        val meditate = habitList.first { it.name == "Meditate" }
        assertEquals(HabitType.YES_NO, meditate.type)
        fun meditateEntry(day: Int) = meditate.originalEntries.get(LocalDate(2023, 5, day))
        assertEquals(Entry(LocalDate(2023, 5, 1), Entry.YES_MANUAL, null, "calm"), meditateEntry(1))
        assertEquals(Entry(LocalDate(2023, 5, 2), Entry.NO, null), meditateEntry(2))
    }

    @Test
    fun testLoopDB() = runTest {
        importFromFile("loop.db")
        assertEquals(9, habitList.size())
        val habit = habitList.getByPosition(0)
        assertEquals("Wake up early", habit.name)
        assertEquals(Frequency.THREE_TIMES_PER_WEEK, habit.frequency)
        assertTrue(isChecked(habit, 2016, 3, 14))
        assertTrue(isChecked(habit, 2016, 3, 16))
        assertFalse(isChecked(habit, 2016, 3, 17))
    }

    @Test
    fun testLoopDBVersion25UpgradesEntriesAndHabits() = runTest {
        importFromFile("loop_v25.db")
        assertEquals(10, habitList.size())
        val run = habitList.getByUUID("feedface000000000000000000000001")!!
        assertTrue(run.isNumerical)
        assertEquals(TargetPeriodMode.CALENDAR, run.targetPeriodMode)
        assertTrue(run.assumeNone)
        assertFalse(run.skipReminderIfLogged)

        fun entry(day: Int) = run.originalEntries.get(LocalDate(2021, 1, day))
        assertEquals(Entry(LocalDate(2021, 1, 1), Entry.AMOUNT, 2500, "morning"), entry(1))
        assertEquals(Entry(LocalDate(2021, 1, 2), Entry.SKIP, null, "sick"), entry(2))
        assertEquals(Entry(LocalDate(2021, 1, 3), Entry.UNKNOWN, null, "forgot"), entry(3))
        assertEquals(Entry(LocalDate(2021, 1, 4), Entry.AMOUNT, 1000), entry(4))
        assertEquals(Entry(LocalDate(2021, 1, 5), Entry.AMOUNT, 0), entry(5))

        val wakeUp = habitList.getByPosition(0)
        assertEquals("Wake up early", wakeUp.name)
        assertTrue(isChecked(wakeUp, 2016, 3, 14))
        assertFalse(isChecked(wakeUp, 2016, 3, 17))
    }

    @Test
    fun testLoopDBVersion26KeepsJudgingSettings() = runTest {
        importFromFile("loop_v26.db")
        val run = habitList.getByUUID("feedface000000000000000000000001")!!
        assertEquals(TargetPeriodMode.ROLLING, run.targetPeriodMode)
        assertFalse(run.assumeNone)
        assertTrue(run.skipReminderIfLogged)
    }

    @Test
    fun testRewireDB() = runTest {
        importFromFile("rewire.db")
        assertEquals(3, habitList.size())
        var habit = habitList.getByPosition(1)
        assertEquals("Wake up early", habit.name)
        assertEquals(Frequency.THREE_TIMES_PER_WEEK, habit.frequency)
        assertFalse(habit.hasReminder())
        assertFalse(isChecked(habit, 2015, 12, 31))
        assertTrue(isChecked(habit, 2016, 1, 18))
        assertTrue(isChecked(habit, 2016, 1, 28))
        assertFalse(isChecked(habit, 2016, 3, 10))
        habit = habitList.getByPosition(2)
        assertEquals("brush teeth", habit.name)
        assertEquals(Frequency.THREE_TIMES_PER_WEEK, habit.frequency)
        assertEquals(true, habit.hasReminder())
        val reminder = habit.reminder
        assertEquals(8, reminder!!.hour)
        assertEquals(0, reminder.minute)
        val reminderDays = booleanArrayOf(false, true, true, true, true, true, false)
        assertEquals(reminderDays.toList(), reminder.days.toArray().toList())
    }

    @Test
    fun testTickmateDB() = runTest {
        importFromFile("tickmate.db")
        assertEquals(3, habitList.size())
        val h = habitList.getByPosition(2)
        assertEquals("Vegan", h.name)
        assertTrue(isChecked(h, 2016, 1, 24))
        assertTrue(isChecked(h, 2016, 2, 5))
        assertTrue(isChecked(h, 2016, 3, 18))
        assertFalse(isChecked(h, 2016, 3, 14))
    }

    private fun isChecked(h: Habit, year: Int, month: Int, day: Int): Boolean {
        return getValue(h, year, month, day) == Entry.YES_MANUAL
    }

    private fun getValue(h: Habit, year: Int, month: Int, day: Int): Int {
        return h.originalEntries.get(LocalDate(year, month, day)).value
    }

    private fun isNotesEqual(h: Habit, year: Int, month: Int, day: Int, notes: String): Boolean {
        return h.originalEntries.get(LocalDate(year, month, day)).notes == notes
    }

    private suspend fun importFromFile(assetFilename: String) {
        val userFile = copyResourceToTempFile(assetFilename)
        assertTrue(userFile.exists())
        val dbOpener = databaseOpener()
        val importer = GenericImporter(
            LoopDBImporter(
                habitList,
                modelFactory,
                dbOpener,
                commandRunner,
                StandardLogging(),
                fileOpener
            ),
            RewireDBImporter(habitList, modelFactory, dbOpener),
            TickmateDBImporter(habitList, modelFactory, dbOpener),
            HabitBullCSVImporter(habitList, modelFactory, StandardLogging())
        )
        assertTrue(importer.canHandle(userFile))
        importer.importHabitsFromFile(userFile)
        userFile.delete()
    }
}
