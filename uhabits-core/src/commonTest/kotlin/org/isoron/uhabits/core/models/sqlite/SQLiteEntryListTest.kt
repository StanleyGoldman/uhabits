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

package org.isoron.uhabits.core.models.sqlite

import kotlinx.coroutines.test.runTest
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.BaseUnitTest.Companion.buildMemoryDatabase
import org.isoron.uhabits.core.database.EntryData
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Entry.Companion.AMOUNT
import org.isoron.uhabits.core.models.Entry.Companion.SKIP
import org.isoron.uhabits.core.models.Entry.Companion.UNKNOWN
import org.isoron.uhabits.core.models.Entry.Companion.YES_MANUAL
import org.isoron.uhabits.core.models.HabitType
import kotlin.test.Test
import kotlin.test.assertEquals

class SQLiteEntryListTest {

    private lateinit var factory: SQLModelFactory
    private lateinit var entries: SQLiteEntryList
    private lateinit var yesNoEntries: SQLiteEntryList
    private val today = LocalDate(2015, 1, 25)

    private suspend fun initTest() {
        val database = buildMemoryDatabase()
        factory = SQLModelFactory(database)
        val habitList = factory.buildHabitList()
        val habit = factory.buildHabit()
        habit.type = HabitType.NUMERICAL
        habitList.add(habit)
        entries = habit.originalEntries as SQLiteEntryList
        val yesNoHabit = factory.buildHabit()
        habitList.add(yesNoHabit)
        yesNoEntries = yesNoHabit.originalEntries as SQLiteEntryList
    }

    private fun reloadEntries(habitId: Long): SQLiteEntryList {
        val habit = factory.buildHabitList().getById(habitId)!!
        return habit.originalEntries as SQLiteEntryList
    }

    @Test
    fun testLoad() = runTest {
        initTest()
        val today = LocalDate(2015, 1, 25)
        factory.entryRepository.insert(
            EntryData(
                habitId = entries.habitId,
                timestamp = today.unixTime,
                state = AMOUNT,
                amount = 500
            )
        )
        factory.entryRepository.insert(
            EntryData(
                habitId = entries.habitId,
                timestamp = today.minus(5).unixTime,
                state = SKIP,
                notes = "away"
            )
        )
        assertEquals(
            Entry(today, AMOUNT, 500),
            entries.get(today)
        )
        assertEquals(
            Entry(date = today.minus(1), state = UNKNOWN),
            entries.get(today.minus(1))
        )
        assertEquals(
            Entry(today.minus(5), SKIP, null, "away"),
            entries.get(today.minus(5))
        )
    }

    @Test
    fun testLoadEntryAtUnixEpoch() = runTest {
        initTest()
        factory.entryRepository.insert(
            EntryData(habitId = yesNoEntries.habitId, timestamp = 0, state = 2)
        )
        assertEquals(
            listOf(Entry(date = LocalDate(1970, 1, 1), state = YES_MANUAL)),
            yesNoEntries.getKnown()
        )
    }

    @Test
    fun testAdd() = runTest {
        initTest()
        val habitId = entries.habitId!!

        assertEquals(0, factory.entryRepository.findAllByHabitId(habitId).size)

        val original = Entry(today, AMOUNT, 150)
        entries.add(original)

        val all = factory.entryRepository.findAllByHabitId(habitId)
        assertEquals(1, all.size)
        assertEquals(AMOUNT, all[0].state)
        assertEquals(150, all[0].amount)
        assertEquals(today.unixTime, all[0].timestamp)

        val replacement = Entry(today, AMOUNT, 90)
        entries.add(replacement)

        val all2 = factory.entryRepository.findAllByHabitId(habitId)
        assertEquals(1, all2.size)
        assertEquals(90, all2[0].amount)
    }

    @Test
    fun testStateAndAmountSurviveReload() = runTest {
        initTest()
        entries.add(Entry(today, AMOUNT, 2500, "two and a half"))
        entries.add(Entry(today.minus(1), SKIP, null, "sick"))
        entries.add(Entry(today.minus(2), AMOUNT, 0))
        yesNoEntries.add(Entry(today, YES_MANUAL, null, "done"))

        val reloaded = reloadEntries(entries.habitId!!)
        assertEquals(Entry(today, AMOUNT, 2500, "two and a half"), reloaded.get(today))
        assertEquals(Entry(today.minus(1), SKIP, null, "sick"), reloaded.get(today.minus(1)))
        assertEquals(Entry(today.minus(2), AMOUNT, 0), reloaded.get(today.minus(2)))
        val reloadedYesNo = reloadEntries(yesNoEntries.habitId!!)
        assertEquals(Entry(today, YES_MANUAL, null, "done"), reloadedYesNo.get(today))
    }

    @Test
    fun testAddMeasurableEntryStoresStateAndAmount() = runTest {
        initTest()
        val expected = listOf(
            Entry(today, AMOUNT, 2500, "notes"),
            Entry(today.minus(1), SKIP),
            Entry(today.minus(2), UNKNOWN, notes = "only notes"),
            Entry(today.minus(4), AMOUNT, 0)
        )
        expected.forEach { entries.add(it) }

        val reloaded = reloadEntries(entries.habitId!!)
        for (entry in expected) {
            assertEquals(entry, entries.get(entry.date))
            assertEquals(entry, reloaded.get(entry.date))
        }
    }

    @Test
    fun testAddYesNoEntryStoresStateWithoutAmount() = runTest {
        initTest()
        yesNoEntries.add(Entry(today, YES_MANUAL))
        val reloaded = reloadEntries(yesNoEntries.habitId!!)
        assertEquals(Entry(today, YES_MANUAL, null), reloaded.get(today))
    }
}
