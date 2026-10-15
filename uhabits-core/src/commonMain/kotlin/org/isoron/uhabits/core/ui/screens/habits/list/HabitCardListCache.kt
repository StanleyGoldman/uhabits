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
package org.isoron.uhabits.core.ui.screens.habits.list

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import org.isoron.platform.Synchronized
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.AppScope
import org.isoron.uhabits.core.commands.Command
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.io.Logging
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.HabitList.Order
import org.isoron.uhabits.core.models.HabitMatcher
import org.isoron.uhabits.core.tasks.Task
import org.isoron.uhabits.core.tasks.TaskRunner
import kotlin.time.Duration.Companion.seconds

/**
 * A HabitCardListCache fetches and keeps a cache of all the data necessary to
 * render a HabitCardListView.
 *
 *
 * This is needed since performing database lookups during scrolling can make
 * the ListView very slow. It also registers itself as an observer of the
 * models, in order to update itself automatically.
 *
 *
 * Note that this class is singleton-scoped, therefore it is shared among all
 * activities.
 *
 *
 * A habit toggled on the list gets a grace period: its row stays visible for
 * [GRACE_PERIOD] after the last toggle, even if "Hide completed" or "Hide
 * entered" would hide it, so that the user can keep tapping through values.
 */
@AppScope
@Inject
class HabitCardListCache(
    private val allHabits: HabitList,
    private val commandRunner: CommandRunner,
    taskRunner: TaskRunner,
    logging: Logging,
    graceDispatcher: CoroutineDispatcher = Dispatchers.Main
) : CommandRunner.Listener {

    private val logger = logging.getLogger("HabitCardListCache")

    private var checkmarkCount = 0
    private var currentFetchTask: Task? = null
    private var listener: Listener
    private val data: CacheData
    private var filteredHabits: HabitList
    private var matcher = HabitMatcher()
    private val gracePeriods = mutableMapOf<Long, Job?>()
    private val graceScope = CoroutineScope(SupervisorJob() + graceDispatcher)
    private val taskRunner: TaskRunner

    @Synchronized
    fun cancelTasks() {
        currentFetchTask?.cancel()
    }

    /** Each day's amount ×1000, newest first; 0 on a day without one. */
    @Synchronized
    fun getAmounts(habitId: Long): IntArray {
        return data.amounts[habitId]!!
    }

    @Synchronized
    fun getStates(habitId: Long): IntArray {
        return data.states[habitId]!!
    }

    @Synchronized
    fun getNotes(habitId: Long): Array<String> {
        return data.notes[habitId]!!
    }

    @Synchronized
    fun hasNoHabit(): Boolean {
        return allHabits.isEmpty
    }

    /**
     * Returns the habits that occupies a certain position on the list.
     *
     * @param position the position of the habit
     * @return the habit at given position or null if position is invalid
     */
    @Synchronized
    fun getHabitByPosition(position: Int): Habit? {
        return if (position < 0 || position >= data.habits.size) null else data.habits[position]
    }

    @get:Synchronized
    val habitCount: Int
        get() = data.habits.size

    @get:Synchronized
    @set:Synchronized
    var primaryOrder: Order
        get() = filteredHabits.primaryOrder
        set(order) {
            allHabits.primaryOrder = order
            filteredHabits.primaryOrder = order
            refreshAllHabits()
        }

    @get:Synchronized
    @set:Synchronized
    var secondaryOrder: Order
        get() = filteredHabits.secondaryOrder
        set(order) {
            allHabits.secondaryOrder = order
            filteredHabits.secondaryOrder = order
            refreshAllHabits()
        }

    @Synchronized
    fun getScore(habitId: Long): Double {
        return data.scores[habitId]!!
    }

    @Synchronized
    fun onAttached() {
        refreshAllHabits()
        commandRunner.addListener(this)
    }

    @Synchronized
    override fun onCommandFinished(command: Command) {
        if (command is CreateRepetitionCommand) {
            command.habit.id?.let { refreshHabit(it) }
        } else {
            refreshAllHabits()
        }
    }

    @Synchronized
    fun onDetached() {
        commandRunner.removeListener(this)
        gracePeriods.values.forEach { it?.cancel() }
        gracePeriods.clear()
    }

    @Synchronized
    fun refreshAllHabits() {
        if (currentFetchTask != null) currentFetchTask!!.cancel()
        val task = RefreshTask()
        currentFetchTask = task
        taskRunner.execute(task)
    }

    @Synchronized
    fun refreshHabit(id: Long) {
        taskRunner.execute(RefreshTask(id))
    }

    @Synchronized
    fun remove(id: Long) {
        val h = data.idToHabit[id] ?: return
        val position = data.habits.indexOf(h)
        data.habits.removeAt(position)
        data.idToHabit.remove(id)
        data.amounts.remove(id)
        data.states.remove(id)
        data.notes.remove(id)
        data.scores.remove(id)
        listener.onItemRemoved(position)
    }

    @Synchronized
    fun reorder(from: Int, to: Int) {
        val fromHabit = data.habits[from]
        data.habits.removeAt(from)
        data.habits.add(to, fromHabit)
        listener.onItemMoved(from, to)
    }

    @Synchronized
    fun setCheckmarkCount(checkmarkCount: Int) {
        this.checkmarkCount = checkmarkCount
    }

    @Synchronized
    fun setFilter(matcher: HabitMatcher) {
        this.matcher = matcher
        filteredHabits = allHabits.getFiltered(matcher)
    }

    /**
     * Keeps the habit's row visible for [GRACE_PERIOD], restarting the timer
     * if it is already running. Call before saving a toggle.
     */
    @Synchronized
    fun startGracePeriod(habitId: Long) {
        gracePeriods.remove(habitId)?.cancel()
        gracePeriods[habitId] = graceScope.launch {
            delay(GRACE_PERIOD)
            onGraceTimerExpired(habitId, currentCoroutineContext()[Job])
        }
    }

    @Synchronized
    private fun onGraceTimerExpired(habitId: Long, timer: Job?) {
        // A later tap or popup may have replaced this timer
        if (gracePeriods[habitId] === timer) endGracePeriod(habitId)
    }

    /**
     * Keeps the habit's row visible, with no timer, until [endGracePeriod].
     * Used while a popup for the habit is open.
     */
    @Synchronized
    fun holdUntilEnded(habitId: Long) {
        gracePeriods.remove(habitId)?.cancel()
        gracePeriods[habitId] = null
    }

    /**
     * Ends the habit's grace period now, so the hide rules apply to its row.
     */
    @Synchronized
    fun endGracePeriod(habitId: Long) {
        if (!gracePeriods.containsKey(habitId)) return
        gracePeriods.remove(habitId)?.cancel()
        refreshHabit(habitId)
    }

    /**
     * The habits to list: those the matcher keeps, plus those in [heldIds]
     * that only the hide rules would remove. Keeps the list's order.
     */
    private fun visibleHabits(heldIds: Set<Long>, matcher: HabitMatcher): List<Habit> {
        if (heldIds.isEmpty()) return filteredHabits.toList()
        val filteredIds = filteredHabits.mapNotNull { it.id }.toSet()
        val heldMatcher = matcher.copy(isCompletedAllowed = true, isEnteredAllowed = true)
        return allHabits.filter { h ->
            h.id in filteredIds || (h.id in heldIds && heldMatcher.matches(h))
        }
    }

    @Synchronized
    fun setListener(listener: Listener) {
        this.listener = listener
    }

    /**
     * Interface definition for a callback to be invoked when the data on the
     * cache has been modified.
     */
    interface Listener {
        fun onItemChanged(position: Int) {}
        fun onItemInserted(position: Int) {}
        fun onItemMoved(oldPosition: Int, newPosition: Int) {}
        fun onItemRemoved(position: Int) {}
        fun onRefreshFinished() {}
    }

    private inner class CacheData {
        val idToHabit: MutableMap<Long?, Habit> = mutableMapOf()
        val habits: MutableList<Habit>
        val amounts: MutableMap<Long?, IntArray>
        val states: MutableMap<Long?, IntArray>
        val scores: MutableMap<Long?, Double>
        val notes: MutableMap<Long?, Array<String>>

        @Synchronized
        fun copyEntriesFrom(oldData: CacheData) {
            val empty = IntArray(checkmarkCount)
            for (id in idToHabit.keys) {
                if (oldData.amounts.containsKey(id)) {
                    amounts[id] =
                        oldData.amounts[id]!!
                    states[id] = oldData.states[id]!!
                } else {
                    amounts[id] = empty
                    states[id] = empty
                }
            }
        }

        @Synchronized
        fun copyNoteIndicatorsFrom(oldData: CacheData) {
            val empty = (0..checkmarkCount).map { "" }.toTypedArray()
            for (id in idToHabit.keys) {
                if (oldData.notes.containsKey(id)) {
                    notes[id] =
                        oldData.notes[id]!!
                } else {
                    notes[id] = empty
                }
            }
        }

        @Synchronized
        fun copyScoresFrom(oldData: CacheData) {
            for (id in idToHabit.keys) {
                if (oldData.scores.containsKey(id)) {
                    scores[id] =
                        oldData.scores[id]!!
                } else {
                    scores[id] = 0.0
                }
            }
        }

        @Synchronized
        fun fetchHabits(heldIds: Set<Long>, matcher: HabitMatcher) {
            for (h in visibleHabits(heldIds, matcher)) {
                if (h.id == null) continue
                habits.add(h)
                idToHabit[h.id] = h
            }
        }

        /**
         * Creates a new CacheData without any content.
         */
        init {
            habits = mutableListOf()
            amounts = mutableMapOf()
            states = mutableMapOf()
            scores = mutableMapOf()
            notes = mutableMapOf()
        }
    }

    private inner class RefreshTask : Task {
        private val newData: CacheData
        private val targetId: Long?

        // Snapshotted on the caller's thread, since doInBackground runs on another
        private val heldIds = gracePeriods.keys.toSet()
        private val matcher = this@HabitCardListCache.matcher
        private var isCancelled = false
        private var runner: TaskRunner? = null

        constructor() {
            newData = CacheData()
            targetId = null
            isCancelled = false
        }

        constructor(targetId: Long) {
            newData = CacheData()
            this.targetId = targetId
        }

        @Synchronized
        override fun cancel() {
            isCancelled = true
        }

        @Synchronized
        override suspend fun doInBackground() {
            newData.fetchHabits(heldIds, matcher)
            newData.copyScoresFrom(data)
            newData.copyEntriesFrom(data)
            newData.copyNoteIndicatorsFrom(data)
            val today = getToday()
            val dateFrom = today.minus(checkmarkCount - 1)
            if (runner != null) runner!!.publishProgress(this, -1)
            for (position in newData.habits.indices) {
                if (isCancelled) return
                val habit = newData.habits[position]
                if (targetId != null && targetId != habit.id) continue
                newData.scores[habit.id] = habit.scores[today].value
                val amountList = mutableListOf<Int>()
                val stateList = mutableListOf<Int>()
                val noteList = mutableListOf<String>()
                for (entry in habit.computedEntries.getByInterval(dateFrom, today)) {
                    amountList.add(entry.amount ?: 0)
                    stateList.add(entry.state)
                    noteList.add(entry.notes)
                }
                newData.amounts[habit.id] = amountList.toIntArray()
                newData.states[habit.id] = stateList.toIntArray()
                newData.notes[habit.id] = noteList.toTypedArray()
                runner!!.publishProgress(this, position)
            }
        }

        @Synchronized
        override fun onAttached(runner: TaskRunner) {
            this.runner = runner
        }

        @Synchronized
        override fun onPostExecute() {
            currentFetchTask = null
            listener.onRefreshFinished()
        }

        @Synchronized
        override fun onProgressUpdate(currentPosition: Int) {
            if (currentPosition < 0) processRemovedHabits() else processPosition(currentPosition)
        }

        @Synchronized
        private fun performInsert(habit: Habit, position: Int) {
            val id = habit.id
            data.habits.add(position, habit)
            data.idToHabit[id] = habit
            data.scores[id] = newData.scores[id]!!
            data.amounts[id] = newData.amounts[id]!!
            data.states[id] = newData.states[id]!!
            data.notes[id] = newData.notes[id]!!
            listener.onItemInserted(position)
        }

        @Synchronized
        private fun performMove(
            habit: Habit,
            fromPosition: Int,
            toPosition: Int
        ) {
            data.habits.removeAt(fromPosition)

            // Workaround for https://github.com/iSoron/uhabits/issues/968
            val checkedToPosition = if (toPosition > data.habits.size) {
                logger.error("performMove: $toPosition is strictly higher than ${data.habits.size}")
                data.habits.size
            } else {
                toPosition
            }

            data.habits.add(checkedToPosition, habit)
            listener.onItemMoved(fromPosition, checkedToPosition)
        }

        @Synchronized
        private fun performUpdate(id: Long, position: Int) {
            val oldScore = data.scores[id]!!
            val oldAmounts = data.amounts[id]
            val oldStates = data.states[id]
            val oldNoteIndicators = data.notes[id]
            val newScore = newData.scores[id]!!
            val newAmounts = newData.amounts[id]!!
            val newStates = newData.states[id]!!
            val newNoteIndicators = newData.notes[id]!!
            var unchanged = true
            if (oldScore != newScore) unchanged = false
            if (!oldAmounts.contentEquals(newAmounts)) unchanged = false
            if (!oldStates.contentEquals(newStates)) unchanged = false
            if (!oldNoteIndicators.contentEquals(newNoteIndicators)) unchanged = false
            if (unchanged) return
            data.scores[id] = newScore
            data.amounts[id] = newAmounts
            data.states[id] = newStates
            data.notes[id] = newNoteIndicators
            listener.onItemChanged(position)
        }

        @Synchronized
        private fun processPosition(currentPosition: Int) {
            val habit = newData.habits[currentPosition]
            val id = habit.id
            val prevPosition = data.habits.indexOf(habit)
            if (prevPosition < 0) {
                performInsert(habit, currentPosition)
            } else {
                if (prevPosition != currentPosition) {
                    performMove(
                        habit,
                        prevPosition,
                        currentPosition
                    )
                }
                if (id == null) throw NullPointerException()
                performUpdate(id, currentPosition)
            }
        }

        @Synchronized
        private fun processRemovedHabits() {
            val before: Set<Long?> = data.idToHabit.keys
            val after: Set<Long?> = newData.idToHabit.keys
            val removed = before - after
            for (id in removed) remove(id!!)
        }
    }

    companion object {
        val GRACE_PERIOD = 2.seconds
    }

    init {
        filteredHabits = allHabits
        this.taskRunner = taskRunner
        listener = object : Listener {}
        data = CacheData()
    }
}
