/*
 * Copyright (C) 2016-2026 Álinson Santos Xavier <git@axavier.org>
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

package org.isoron.uhabits.core.database.migrations

import kotlinx.coroutines.test.runTest
import org.isoron.platform.io.Database
import org.isoron.platform.io.format
import org.isoron.platform.io.migrateTo
import org.isoron.platform.io.query
import org.isoron.platform.io.queryLong
import org.isoron.platform.io.querySingle
import org.isoron.platform.io.run
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.models.Entry.Companion.AMOUNT
import org.isoron.uhabits.core.models.Entry.Companion.SKIP
import org.isoron.uhabits.core.models.Entry.Companion.UNKNOWN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Version26Test : BaseUnitTest() {

    private lateinit var db: Database
    private var yesNoHabit = 0L
    private var measurableHabit = 0L

    private suspend fun initDb() {
        db = openDatabaseResource("/databases/022.db")
        migrateTo(25)
        yesNoHabit = insertHabit(type = 0)
        measurableHabit = insertHabit(type = 1)
    }

    private suspend fun migrateTo(version: Int) {
        db.migrateTo(version) { v ->
            val path = "migrations/${format("%02d.sql", v)}"
            fileOpener.openResourceFile(path).lines().joinToString("\n")
        }
    }

    private fun insertHabit(type: Int): Long {
        db.run(
            """
            insert into Habits(name, freq_num, freq_den, color, position, archived, type)
            values ('Test', 1, 1, 0, 0, 0, $type)
            """
        )
        return db.queryLong("select last_insert_rowid()")
    }

    private fun insertRep(habit: Long, timestamp: Long, value: Int, notes: String? = null) {
        val notesSql = if (notes == null) "null" else "'$notes'"
        db.run(
            "insert into Repetitions(habit, timestamp, value, notes) " +
                "values ($habit, $timestamp, $value, $notesSql)"
        )
    }

    /** Returns (state, amount, notes) of the entry the migration produced. */
    private fun rep(habit: Long, timestamp: Long): Triple<Int, Int?, String?> {
        return db.querySingle(
            "select state, amount, notes from Repetitions where habit = ? and timestamp = ?",
            habit.toString(),
            timestamp.toString()
        ) { Triple(it.getInt(0), it.getIntOrNull(1), it.getTextOrNull(2)) }!!
    }

    private fun dbTest(block: suspend () -> Unit) = runTest {
        initDb()
        block()
    }

    @Test
    fun testYesNoEntriesKeepTheirValueAsState() = dbTest {
        insertRep(yesNoHabit, 100, -1)
        insertRep(yesNoHabit, 200, 0)
        insertRep(yesNoHabit, 300, 2, "done")
        insertRep(yesNoHabit, 400, 3)
        migrateTo(26)
        assertEquals(Triple(-1, null, null), rep(yesNoHabit, 100))
        assertEquals(Triple(0, null, null), rep(yesNoHabit, 200))
        assertEquals(Triple(2, null, "done"), rep(yesNoHabit, 300))
        assertEquals(Triple(3, null, null), rep(yesNoHabit, 400))
    }

    @Test
    fun testMeasurableSkipBecomesSkipWithoutAmount() = dbTest {
        insertRep(measurableHabit, 100, 3, "sick")
        migrateTo(26)
        assertEquals(Triple(SKIP, null, "sick"), rep(measurableHabit, 100))
    }

    @Test
    fun testMeasurableUnknownWithNotesIsKeptAsUnknown() = dbTest {
        insertRep(measurableHabit, 100, -1, "forgot to count")
        migrateTo(26)
        assertEquals(Triple(UNKNOWN, null, "forgot to count"), rep(measurableHabit, 100))
    }

    @Test
    fun testMeasurableStoredTwoBecomesAmountOfOne() = dbTest {
        insertRep(measurableHabit, 100, 2)
        migrateTo(26)
        assertEquals(Triple(AMOUNT, 1000, null), rep(measurableHabit, 100))
    }

    @Test
    fun testMeasurableZeroBecomesAmountOfZero() = dbTest {
        insertRep(measurableHabit, 100, 0)
        migrateTo(26)
        assertEquals(Triple(AMOUNT, 0, null), rep(measurableHabit, 100))
    }

    @Test
    fun testMeasurableOtherValuesBecomeAmount() = dbTest {
        insertRep(measurableHabit, 100, 2500, "morning")
        insertRep(measurableHabit, 200, 1)
        insertRep(measurableHabit, 300, 4)
        migrateTo(26)
        assertEquals(Triple(AMOUNT, 2500, "morning"), rep(measurableHabit, 100))
        assertEquals(Triple(AMOUNT, 1, null), rep(measurableHabit, 200))
        assertEquals(Triple(AMOUNT, 4, null), rep(measurableHabit, 300))
    }

    @Test
    fun testKeepsEveryRow() = dbTest {
        val before = db.queryLong("select count(*) from Repetitions")
        insertRep(yesNoHabit, 100, 2)
        insertRep(measurableHabit, 100, -1)
        migrateTo(26)
        assertEquals(before + 2, db.queryLong("select count(*) from Repetitions"))
    }

    @Test
    fun testRemovesValueColumn() = dbTest {
        migrateTo(26)
        val columns = mutableListOf<String>()
        db.query("pragma table_info(Repetitions)") { columns.add(it.getText(1)) }
        assertEquals(listOf("id", "habit", "timestamp", "state", "amount", "notes"), columns)
    }

    @Test
    fun testKeepsOneEntryPerHabitAndDay() = dbTest {
        migrateTo(26)
        insertNewRep(yesNoHabit, 100)
        val failure = runCatching { insertNewRep(yesNoHabit, 100) }.exceptionOrNull()
        assertEquals(true, failure?.message?.contains("constraint"))
    }

    private fun insertNewRep(habit: Long, timestamp: Long) {
        db.run("insert into Repetitions(habit, timestamp, state) values ($habit, $timestamp, 2)")
    }

    @Test
    fun testExistingHabitsGetJudgingSettingDefaults() = dbTest {
        migrateTo(26)
        db.query(
            "select target_period_mode, assume_none, skip_reminder_if_logged from Habits"
        ) { stmt ->
            assertEquals(0, stmt.getInt(0))
            assertEquals(1, stmt.getInt(1))
            assertEquals(0, stmt.getInt(2))
        }
    }

    @Test
    fun testAmountIsNullableButStateIsNot() = dbTest {
        migrateTo(26)
        db.run("insert into Repetitions(habit, timestamp, state) values ($yesNoHabit, 500, 3)")
        assertNull(rep(yesNoHabit, 500).second)
        val failure = runCatching {
            db.run("insert into Repetitions(habit, timestamp, amount) values ($yesNoHabit, 600, 5)")
        }.exceptionOrNull()
        assertEquals(true, failure?.message?.contains("constraint"))
    }
}
