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

import me.tatarka.inject.annotations.Inject
import org.isoron.platform.io.UserFile
import org.isoron.platform.io.parseCsvLine
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.HabitType
import org.isoron.uhabits.core.models.ModelFactory

/**
 * Class that imports data from HabitBull CSV files.
 */
@Inject
class HabitBullCSVImporter(
    private val habitList: HabitList,
    private val modelFactory: ModelFactory,
    logging: Logging
) : AbstractImporter() {

    private val logger = logging.getLogger("HabitBullCSVImporter")

    override suspend fun canHandle(file: UserFile): Boolean {
        return try {
            val lines = file.lines()
            if (lines.isEmpty()) return false
            lines[0].startsWith("HabitName,HabitDescription,HabitCategory")
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun importHabitsFromFile(file: UserFile) {
        val rows = file.lines()
            .map { parseCsvLine(it) }
            .filter { it.size >= 6 && it[0] != "HabitName" }
            .map { cols -> Row(cols[0], cols[1], parseDate(cols[3]), parseInt(cols[4]), cols[5]) }

        // First pass: any value above 1 makes the habit measurable, so the
        // type is known before any of its entries are written.
        val measurableNames = rows.filter { it.value > 1 }.map { it.name }.toSet()

        val map = HashMap<String, Habit>()
        for (row in rows) {
            var h = map[row.name]
            if (h == null) {
                h = modelFactory.buildHabit()
                h.name = row.name
                h.description = row.description
                h.frequency = Frequency.DAILY
                if (row.name in measurableNames) {
                    logger.info("Found a value above 1, considering this habit as measurable.")
                    h.type = HabitType.NUMERICAL
                }
                habitList.add(h)
                map[row.name] = h
                logger.info("Creating habit: ${row.name}")
            }
            // In a yes-or-no habit anything but 1 is NO: parseInt already turns
            // an unparseable value into 0, so it can't be told apart from one.
            val entry = when {
                h.isNumerical -> Entry(row.date, Entry.AMOUNT, row.value * 1000, row.notes)
                row.value == 1 -> Entry(row.date, Entry.YES_MANUAL, null, row.notes)
                else -> Entry(row.date, Entry.NO, null, row.notes)
            }
            h.originalEntries.add(entry)
        }

        map.forEach { (_, habit) -> habit.recompute() }
    }

    private class Row(
        val name: String,
        val description: String,
        val date: LocalDate,
        val value: Int,
        val notes: String
    )

    private fun parseDate(rawValue: String): LocalDate {
        if (rawValue.contains("-")) {
            val parts = rawValue.split("-")
            return LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        }
        if (rawValue.contains("/")) {
            val parts = rawValue.split("/")
            return LocalDate(parts[2].toInt(), parts[0].toInt(), parts[1].toInt())
        }
        throw Exception("Unrecognized date format: $rawValue")
    }

    private fun parseInt(rawValue: String): Int {
        return try {
            rawValue.toInt()
        } catch (e: NumberFormatException) {
            logger.error("Could not parse int: $rawValue. Replacing by zero.")
            0
        }
    }
}
