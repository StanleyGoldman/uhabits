package org.isoron.uhabits.core.database

import org.isoron.platform.io.Database
import org.isoron.platform.io.StepResult
import org.isoron.platform.io.queryLong
import org.isoron.platform.io.run

data class EntryData(
    var id: Long? = null,
    var habitId: Long? = null,
    var timestamp: Long = 0,
    var state: Int = 0,
    var amount: Int? = null,
    var notes: String = ""
)

class EntryRepository(private val db: Database) {
    private val findAllByHabitStmt by lazy {
        db.prepareStatement(
            "SELECT id, habit, timestamp, state, amount, notes FROM Repetitions WHERE habit = ? ORDER BY timestamp DESC"
        )
    }

    private val insertStmt by lazy {
        db.prepareStatement(
            "INSERT INTO Repetitions(habit, timestamp, state, amount, notes) VALUES (?, ?, ?, ?, ?)"
        )
    }

    private val deleteByHabitAndTimestampStmt by lazy {
        db.prepareStatement("DELETE FROM Repetitions WHERE habit = ? AND timestamp = ?")
    }

    private val deleteByHabitStmt by lazy {
        db.prepareStatement("DELETE FROM Repetitions WHERE habit = ?")
    }

    fun findAllByHabitId(habitId: Long): List<EntryData> {
        findAllByHabitStmt.reset()
        findAllByHabitStmt.bindLong(1, habitId)
        val results = mutableListOf<EntryData>()
        while (findAllByHabitStmt.step() == StepResult.ROW) {
            results.add(
                EntryData(
                    id = findAllByHabitStmt.getLong(0),
                    habitId = findAllByHabitStmt.getLong(1),
                    timestamp = findAllByHabitStmt.getLong(2),
                    state = findAllByHabitStmt.getInt(3),
                    amount = findAllByHabitStmt.getIntOrNull(4),
                    notes = findAllByHabitStmt.getTextOrNull(5) ?: ""
                )
            )
        }
        return results
    }

    fun insert(data: EntryData): Long {
        insertStmt.reset()
        insertStmt.bindLong(1, data.habitId!!)
        insertStmt.bindLong(2, data.timestamp)
        insertStmt.bindInt(3, data.state)
        if (data.amount != null) insertStmt.bindInt(4, data.amount!!) else insertStmt.bindNull(4)
        insertStmt.bindText(5, data.notes)
        insertStmt.step()
        return db.queryLong("SELECT last_insert_rowid()")
    }

    fun deleteByHabitIdAndTimestamp(habitId: Long, timestamp: Long) {
        deleteByHabitAndTimestampStmt.reset()
        deleteByHabitAndTimestampStmt.bindLong(1, habitId)
        deleteByHabitAndTimestampStmt.bindLong(2, timestamp)
        deleteByHabitAndTimestampStmt.step()
    }

    fun deleteByHabitId(habitId: Long) {
        deleteByHabitStmt.reset()
        deleteByHabitStmt.bindLong(1, habitId)
        deleteByHabitStmt.step()
    }

    fun execSQL(sql: String) = db.run(sql)
}
