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

package org.isoron.uhabits.core.ui.views

import dev.mokkery.mock
import dev.mokkery.resetCalls
import dev.mokkery.verify
import dev.mokkery.verifyNoMoreCalls
import kotlinx.coroutines.test.runTest
import org.isoron.platform.gui.Color
import org.isoron.platform.gui.assertRenders
import org.isoron.platform.io.createTestCanvas
import org.isoron.platform.io.createTestDateFormatter
import org.isoron.platform.io.ensureFontsLoaded
import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.DayOfWeek.SUNDAY
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.DIMMED
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.DIMMED_OUTLINED
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.GREY
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.GREY_HATCHED
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.GREY_OVER_CAP
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.HATCHED
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.OFF
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.OFF_OUTLINED
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.ON
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryChartTest {
    val base = "views/HistoryChart"

    private val dateClickedListener: OnDateClickedListener = mock()

    val view = HistoryChart(
        today = LocalDate(2015, 1, 25),
        paletteColor = PaletteColor(7),
        theme = LightTheme(),
        dateFormatter = createTestDateFormatter(),
        firstWeekday = SUNDAY,
        onDateClickedListener = dateClickedListener,
        defaultSquare = OFF,
        series = listOf(
            2, // today
            2, 1, 2, 1, 2, 1, 2,
            2, 3, 3, 3, 3, 1, 2,
            2, 1, 2, 1, 2, 2, 1,
            1, 1, 1, 1, 2, 2, 2,
            1, 3, 3, 3, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 1, 1, 1, 1,
            2, 2, 2, 3, 3, 3, 1,
            1, 2, 1, 2, 1, 1, 2,
            1, 2, 1, 1, 1, 1, 2,
            2, 2, 2, 2, 2, 1, 1,
            1, 1, 2, 2, 1, 2, 1,
            1, 1, 1, 1, 2, 2, 2
        ).map {
            when (it) {
                3 -> HATCHED
                2 -> ON
                1 -> DIMMED
                else -> OFF
            }
        },
        notesIndicators = MutableList(85) { index: Int ->
            index % 3 == 0
        }
    )

    @Test
    fun testDraw() = runTest {
        assertRenders(400, 200, "$base/base.png", view)
    }

    @Test
    fun testClick() = runTest {
        assertRenders(400, 200, "$base/base.png", view)

        // Click top left date
        view.onClick(20.0, 46.0)
        verify { dateClickedListener.onDateShortPress(LocalDate(2014, 10, 26)) }
        resetCalls(dateClickedListener)
        view.onClick(2.0, 28.0)
        verify { dateClickedListener.onDateShortPress(LocalDate(2014, 10, 26)) }
        resetCalls(dateClickedListener)

        // Click date in the middle
        view.onClick(163.0, 113.0)
        verify { dateClickedListener.onDateShortPress(LocalDate(2014, 12, 10)) }
        resetCalls(dateClickedListener)

        // Click today
        view.onClick(336.0, 37.0)
        verify { dateClickedListener.onDateShortPress(LocalDate(2015, 1, 25)) }
        resetCalls(dateClickedListener)

        // Click header
        view.onClick(160.0, 15.0)
        verifyNoMoreCalls(dateClickedListener)

        // Click right axis
        view.onClick(360.0, 60.0)
        verifyNoMoreCalls(dateClickedListener)
    }

    @Test
    fun testLongClick() = runTest {
        assertRenders(400, 200, "$base/base.png", view)

        // Click top left date
        view.onLongClick(20.0, 46.0)
        verify { dateClickedListener.onDateLongPress(LocalDate(2014, 10, 26)) }
        resetCalls(dateClickedListener)
        view.onLongClick(2.0, 28.0)
        verify { dateClickedListener.onDateLongPress(LocalDate(2014, 10, 26)) }
        resetCalls(dateClickedListener)

        // Click date in the middle
        view.onLongClick(163.0, 113.0)
        verify { dateClickedListener.onDateLongPress(LocalDate(2014, 12, 10)) }
        resetCalls(dateClickedListener)

        // Click today
        view.onLongClick(336.0, 37.0)
        verify { dateClickedListener.onDateLongPress(LocalDate(2015, 1, 25)) }
        resetCalls(dateClickedListener)

        // Click header
        view.onLongClick(160.0, 15.0)
        verifyNoMoreCalls(dateClickedListener)

        // Click right axis
        view.onLongClick(360.0, 60.0)
        verifyNoMoreCalls(dateClickedListener)
    }

    @Test
    fun testDrawWeekDay() = runTest {
        view.firstWeekday = DayOfWeek.MONDAY
        assertRenders(400, 200, "$base/weekday.png", view)
    }

    @Test
    fun testDrawDifferentSize() = runTest {
        assertRenders(200, 200, "$base/small.png", view)
    }

    @Test
    fun testDrawDarkTheme() = runTest {
        view.theme = DarkTheme()
        assertRenders(400, 200, "$base/themeDark.png", view)
    }

    @Test
    fun testDrawWidgetTheme() = runTest {
        view.theme = WidgetTheme()
        assertRenders(400, 200, "$base/themeWidget.png", view)
    }

    @Test
    fun testDrawOffset() = runTest {
        view.dataOffset = 2
        assertRenders(400, 200, "$base/scroll.png", view)
    }

    /** A view showing every square a weekly or monthly measurable habit can draw. */
    private fun periodSquaresView() = HistoryChart(
        today = LocalDate(2015, 1, 25),
        paletteColor = PaletteColor(7),
        theme = LightTheme(),
        dateFormatter = createTestDateFormatter(),
        firstWeekday = SUNDAY,
        defaultSquare = OFF,
        series = listOf(
            OFF_OUTLINED, // today
            OFF, GREY_HATCHED, OFF, GREY, GREY_OVER_CAP, GREY, GREY,
            DIMMED, ON, DIMMED, HATCHED, ON, DIMMED, ON,
            OFF_OUTLINED, DIMMED_OUTLINED, HATCHED, DIMMED_OUTLINED, OFF_OUTLINED, OFF_OUTLINED, DIMMED_OUTLINED
        ),
        notesIndicators = MutableList(22) { index: Int -> index % 4 == 0 }
    )

    @Test
    fun testDrawPeriodSquares() = runTest {
        assertRenders(260, 400, "$base/periodSquares.png", periodSquaresView())
    }

    @Test
    fun testDrawPeriodSquaresDarkTheme() = runTest {
        val view = periodSquaresView()
        view.theme = DarkTheme()
        assertRenders(260, 400, "$base/periodSquaresDark.png", view)
    }

    @Test
    fun testDrawPeriodSquaresWidgetTheme() = runTest {
        val view = periodSquaresView()
        view.theme = WidgetTheme()
        assertRenders(260, 400, "$base/periodSquaresWidget.png", view)
    }

    @Test
    fun testDrawOverCap_redCornerTopLeft_greyElsewhere() = runTest {
        // One week column, Sunday 18 to Saturday 24 January.
        val view = HistoryChart(
            today = LocalDate(2015, 1, 24),
            paletteColor = PaletteColor(7),
            theme = LightTheme(),
            dateFormatter = createTestDateFormatter(),
            firstWeekday = SUNDAY,
            defaultSquare = OFF,
            series = List(7) { GREY_OVER_CAP },
            notesIndicators = List(7) { false }
        )
        ensureFontsLoaded()
        val canvas = createTestCanvas(100, 400)
        view.draw(canvas)
        val image = canvas.toImage()
        val scale = image.width / canvas.getWidth()

        // Squares are 50 wide; Sunday's square starts at (0, 50).
        fun pixelAt(x: Double, y: Double) = image.getPixel((x * scale).toInt(), (y * scale).toInt())
        assertEquals(Color(0xE53935), pixelAt(8.0, 58.0), "corner")
        assertEquals(LightTheme().mediumContrastTextColor, pixelAt(40.0, 90.0), "rest of the square")
    }
}
