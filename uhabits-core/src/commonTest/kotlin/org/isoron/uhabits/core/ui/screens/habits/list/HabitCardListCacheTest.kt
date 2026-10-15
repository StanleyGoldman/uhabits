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

import dev.mokkery.mock
import dev.mokkery.resetCalls
import dev.mokkery.verify
import dev.mokkery.verifyNoMoreCalls
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.commands.DeleteHabitsCommand
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitMatcher
import org.isoron.uhabits.core.ui.screens.habits.list.HabitCardListCache.Companion.GRACE_PERIOD
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class HabitCardListCacheTest : BaseUnitTest() {
    private lateinit var cache: HabitCardListCache
    private lateinit var listener: HabitCardListCache.Listener
    private val scheduler = TestCoroutineScheduler()
    var today = LocalDate(2015, 1, 25)

    @BeforeTest
    override fun setUp() {
        super.setUp()
        habitList.removeAll()
        for (i in 0..9) {
            if (i == 3) habitList.add(fixtures.createLongHabit()) else habitList.add(fixtures.createShortHabit())
        }
        cache = HabitCardListCache(habitList, commandRunner, taskRunner, mock(), StandardTestDispatcher(scheduler))
        cache.setCheckmarkCount(10)
        cache.refreshAllHabits()
        cache.onAttached()
        listener = mock()
        cache.setListener(listener)
    }

    fun tearDown() {
        cache.onDetached()
    }

    @Test
    fun testCommandListener_all() {
        assertEquals(10, cache.habitCount)
        val h = habitList.getByPosition(0)
        commandRunner.run(
            DeleteHabitsCommand(habitList, listOf(h))
        )
        verify { listener.onItemRemoved(0) }
        verify { listener.onRefreshFinished() }
        assertEquals(9, cache.habitCount)
    }

    @Test
    fun testCommandListener_single() {
        val h2 = habitList.getByPosition(2)
        commandRunner.run(CreateRepetitionCommand(habitList, h2, today, Entry.NO, ""))
        verify { listener.onItemChanged(2) }
        verify { listener.onRefreshFinished() }
        verifyNoMoreCalls(listener)
    }

    @Test
    fun testGet() {
        assertEquals(10, cache.habitCount)
        val h = habitList.getByPosition(3)
        val score = h.scores[today].value
        assertEquals(h, cache.getHabitByPosition(3))
        assertEquals(score, cache.getScore(h.id!!))
        val actualCheckmarks = cache.getCheckmarks(h.id!!)

        val expectedCheckmarks = h
            .computedEntries
            .getByInterval(today.minus(9), today)
            .map { it.value }.toIntArray()
        assertContentEquals(expectedCheckmarks, actualCheckmarks)
    }

    @Test
    fun testRemoval() {
        removeHabitAt(0)
        removeHabitAt(3)
        cache.refreshAllHabits()
        verify { listener.onItemRemoved(0) }
        verify { listener.onItemRemoved(3) }
        verify { listener.onRefreshFinished() }
        assertEquals(8, cache.habitCount)
    }

    @Test
    fun testRefreshWithNoChanges() {
        cache.refreshAllHabits()
        verify { listener.onRefreshFinished() }
        verifyNoMoreCalls(listener)
    }

    @Test
    fun testReorder_onCache() {
        val h2 = cache.getHabitByPosition(2)
        val h3 = cache.getHabitByPosition(3)
        val h7 = cache.getHabitByPosition(7)
        cache.reorder(2, 7)
        assertEquals(h3, cache.getHabitByPosition(2))
        assertEquals(h2, cache.getHabitByPosition(7))
        assertEquals(h7, cache.getHabitByPosition(6))
        verify { listener.onItemMoved(2, 7) }
        verifyNoMoreCalls(listener)
    }

    @Test
    fun testReorder_onList() {
        val h2 = habitList.getByPosition(2)
        val h3 = habitList.getByPosition(3)
        val h7 = habitList.getByPosition(7)
        assertEquals(h2, cache.getHabitByPosition(2))
        assertEquals(h7, cache.getHabitByPosition(7))
        resetCalls(listener)
        habitList.reorder(h2, h7)
        cache.refreshAllHabits()
        assertEquals(h3, cache.getHabitByPosition(2))
        assertEquals(h2, cache.getHabitByPosition(7))
        assertEquals(h7, cache.getHabitByPosition(6))
        verify { listener.onItemMoved(3, 2) }
        verify { listener.onItemMoved(4, 3) }
        verify { listener.onItemMoved(5, 4) }
        verify { listener.onItemMoved(6, 5) }
        verify { listener.onItemMoved(7, 6) }
        verify { listener.onRefreshFinished() }
        verifyNoMoreCalls(listener)
    }

    @Test
    fun testGracePeriod_keepsToggledRowVisibleUntilItExpires() {
        val h = addHabitHiddenOnceEntered()
        toggle(h, Entry.YES_MANUAL)
        assertTrue(isVisible(h))
        advanceBy(GRACE_PERIOD - 1.milliseconds)
        assertTrue(isVisible(h))
        advanceBy(1.milliseconds)
        assertFalse(isVisible(h))
    }

    @Test
    fun testGracePeriod_eachTapRestartsTheTimer() {
        val h = addHabitHiddenOnceEntered()
        toggle(h, Entry.YES_MANUAL)
        advanceBy(GRACE_PERIOD / 2)
        toggle(h, Entry.SKIP, date = getToday().minus(1))
        advanceBy(GRACE_PERIOD - 1.milliseconds)
        assertTrue(isVisible(h))
        advanceBy(1.milliseconds)
        assertFalse(isVisible(h))
    }

    @Test
    fun testGracePeriod_onlyHoldsTheTappedRow() {
        val h = addHabitHiddenOnceEntered()
        val other = addHabitHiddenOnceEntered()
        toggle(h, Entry.YES_MANUAL)
        commandRunner.run(CreateRepetitionCommand(habitList, other, getToday(), Entry.YES_MANUAL, ""))
        assertTrue(isVisible(h))
        assertFalse(isVisible(other))
    }

    @Test
    fun testGracePeriod_endingItAppliesHideRulesImmediately() {
        val h = addHabitHiddenOnceEntered()
        toggle(h, Entry.YES_MANUAL)
        cache.endGracePeriod(h.id!!)
        assertFalse(isVisible(h))
        advanceBy(GRACE_PERIOD)
        assertFalse(isVisible(h))
    }

    @Test
    fun testHoldUntilEnded_outlastsAnEarlierTimer() {
        val h = addHabitHiddenOnceEntered()
        toggle(h, Entry.YES_MANUAL)
        cache.holdUntilEnded(h.id!!)
        advanceBy(GRACE_PERIOD * 10)
        assertTrue(isVisible(h))
        cache.endGracePeriod(h.id!!)
        assertFalse(isVisible(h))
    }

    @Test
    fun testOnDetached_endsAllGracePeriods() {
        val h = addHabitHiddenOnceEntered()
        cache.holdUntilEnded(h.id!!)
        commandRunner.run(CreateRepetitionCommand(habitList, h, getToday(), Entry.YES_MANUAL, ""))
        assertTrue(isVisible(h))
        cache.onDetached()
        cache.refreshAllHabits()
        assertFalse(isVisible(h))
    }

    private fun addHabitHiddenOnceEntered(): Habit {
        val h = fixtures.createEmptyHabit()
        habitList.add(h)
        cache.setFilter(HabitMatcher(isEnteredAllowed = false))
        cache.refreshAllHabits()
        assertTrue(isVisible(h))
        return h
    }

    private fun toggle(h: Habit, value: Int, date: LocalDate = getToday()) {
        cache.startGracePeriod(h.id!!)
        commandRunner.run(CreateRepetitionCommand(habitList, h, date, value, ""))
    }

    private fun advanceBy(duration: Duration) {
        scheduler.advanceTimeBy(duration)
        scheduler.runCurrent()
    }

    private fun isVisible(h: Habit): Boolean {
        return (0 until cache.habitCount).any { cache.getHabitByPosition(it) == h }
    }

    private fun removeHabitAt(position: Int) {
        val h = habitList.getByPosition(position)
        habitList.remove(h)
    }
}
