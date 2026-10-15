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

import org.isoron.platform.gui.Canvas
import org.isoron.platform.gui.Color
import org.isoron.platform.gui.DataView
import org.isoron.platform.gui.TextAlign
import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.LocalDateFormatter
import org.isoron.uhabits.core.models.PaletteColor
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

interface OnDateClickedListener {
    fun onDateShortPress(date: LocalDate) {}
    fun onDateLongPress(date: LocalDate) {}
}

class HistoryChart(
    var dateFormatter: LocalDateFormatter,
    var firstWeekday: DayOfWeek,
    var paletteColor: PaletteColor,
    var series: List<Square>,
    var defaultSquare: Square,
    var notesIndicators: List<Boolean>,
    var theme: Theme,
    var today: LocalDate,
    var onDateClickedListener: OnDateClickedListener = object : OnDateClickedListener {},
    var padding: Double = 0.0
) : DataView {

    enum class Square {
        ON,
        OFF,
        GREY,
        DIMMED,
        HATCHED,

        /** GREY with a red corner: the day an "at most" period went over its cap. */
        GREY_OVER_CAP,

        /** HATCHED in dimmed grey: skipped, in a missed period. */
        GREY_HATCHED,

        /** DIMMED, outlined in the habit colour: entered, in a period still open. */
        DIMMED_OUTLINED,

        /** OFF, outlined in the habit colour: nothing entered, in a period still open. */
        OFF_OUTLINED
    }

    var squareSpacing = 1.0
    override var dataOffset = 0

    private var squareSize = 0.0
    private var width = 0.0
    private var height = 0.0
    private var nColumns = 0
    private var topLeftOffset = 0
    private var topLeftDate = LocalDate(2020, 1, 1)
    private var lastPrintedMonth = ""
    private var lastPrintedYear = ""
    private var headerOverflow = 0.0

    override val dataColumnWidth: Double
        get() = squareSpacing + squareSize

    override fun onClick(x: Double, y: Double) {
        onDateClicked(x, y, false)
    }

    override fun onLongClick(x: Double, y: Double) {
        onDateClicked(x, y, true)
    }

    private fun onDateClicked(x: Double, y: Double, isLongClick: Boolean) {
        if (width <= 0.0) throw IllegalStateException("onClick must be called after draw(canvas)")
        val col = ((x - padding) / squareSize).toInt()
        val row = ((y - padding) / squareSize).toInt()
        val offset = col * 7 + (row - 1)
        if (x - padding < 0 || row == 0 || row > 7 || col == nColumns) return
        val clickedDate = topLeftDate.plus(offset)
        if (clickedDate.isNewerThan(today)) return
        if (isLongClick) {
            onDateClickedListener.onDateLongPress(clickedDate)
        } else {
            onDateClickedListener.onDateShortPress(clickedDate)
        }
    }

    override fun draw(canvas: Canvas) {
        width = canvas.getWidth()
        height = canvas.getHeight()

        canvas.setColor(theme.cardBackgroundColor)
        canvas.fill()

        squareSize = round((height - 2 * padding) / 8.0)
        canvas.setFontSize(min(14.0, height * 0.06))

        val weekdayColumnWidth = DayOfWeek.values().map { weekday ->
            canvas.measureText(dateFormatter.shortWeekdayName(weekday)) + squareSize * 0.15
        }.maxOrNull() ?: 0.0

        nColumns = floor((width - 2 * padding - weekdayColumnWidth) / squareSize).toInt()
        val firstWeekdayOffset = (
            today.dayOfWeek.daysSinceSunday -
                firstWeekday.daysSinceSunday + 7
            ) % 7
        topLeftOffset = (nColumns - 1 + dataOffset) * 7 + firstWeekdayOffset
        topLeftDate = today.minus(topLeftOffset)

        lastPrintedYear = ""
        lastPrintedMonth = ""
        headerOverflow = 0.0

        // Draw main columns
        repeat(nColumns) { column ->
            val topOffset = topLeftOffset - 7 * column
            val topDate = topLeftDate.plus(7 * column)
            drawColumn(canvas, column, topDate, topOffset)
        }

        // Draw week day names
        canvas.setColor(theme.mediumContrastTextColor)
        repeat(7) { row ->
            val date = topLeftDate.plus(row)
            canvas.setTextAlign(TextAlign.LEFT)
            canvas.drawText(
                dateFormatter.shortWeekdayName(date),
                padding + nColumns * squareSize + squareSize * 0.15,
                padding + squareSize * (row + 1) + squareSize / 2
            )
        }
    }

    private fun drawColumn(
        canvas: Canvas,
        column: Int,
        topDate: LocalDate,
        topOffset: Int
    ) {
        drawHeader(canvas, column, topDate)
        repeat(7) { row ->
            val offset = topOffset - row
            val date = topDate.plus(row)
            if (offset < 0) return
            drawSquare(
                canvas,
                padding + column * squareSize,
                padding + (row + 1) * squareSize,
                squareSize - squareSpacing,
                squareSize - squareSpacing,
                date,
                offset
            )
        }
    }

    private fun drawHeader(canvas: Canvas, column: Int, date: LocalDate) {
        canvas.setColor(theme.mediumContrastTextColor)
        val monthText = dateFormatter.shortMonthName(date)
        val yearText = date.year.toString()
        val headerText: String
        when {
            monthText != lastPrintedMonth -> {
                headerText = monthText
                lastPrintedMonth = monthText
            }
            yearText != lastPrintedYear -> {
                headerText = yearText
                lastPrintedYear = headerText
            }
            else -> {
                headerText = ""
            }
        }
        canvas.setTextAlign(TextAlign.LEFT)
        canvas.drawText(
            headerText,
            headerOverflow + padding + column * squareSize,
            padding + squareSize / 2
        )

        headerOverflow += canvas.measureText(headerText) + 0.1 * squareSize
        headerOverflow = max(0.0, headerOverflow - squareSize)
    }

    private fun drawSquare(
        canvas: Canvas,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        date: LocalDate,
        offset: Int
    ) {
        val value = if (offset >= series.size) defaultSquare else series[offset]
        val hasNotes = if (offset >= notesIndicators.size) false else notesIndicators[offset]
        val squareColor: Color
        val circleColor: Color
        val color = theme.color(paletteColor.paletteIndex)
        squareColor = when (value) {
            Square.ON -> {
                color
            }
            Square.OFF, Square.OFF_OUTLINED -> {
                theme.lowContrastTextColor
            }
            Square.GREY, Square.GREY_OVER_CAP -> {
                theme.mediumContrastTextColor
            }
            Square.GREY_HATCHED -> {
                theme.mediumContrastTextColor.blendWith(theme.cardBackgroundColor, 0.5)
            }
            Square.DIMMED, Square.HATCHED, Square.DIMMED_OUTLINED -> {
                color.blendWith(theme.cardBackgroundColor, 0.5)
            }
        }

        canvas.setColor(squareColor)
        canvas.fillRoundRect(x, y, width, height, width * 0.15)

        if (value == Square.DIMMED_OUTLINED || value == Square.OFF_OUTLINED) {
            canvas.setColor(color)
            fillOutline(canvas, x, y, width, height, width * 0.15, max(1.5, width * 0.08))
        }

        if (value == Square.HATCHED || value == Square.GREY_HATCHED) {
            canvas.setStrokeWidth(0.75)
            canvas.setColor(theme.cardBackgroundColor)
            var k = width / 10
            repeat(5) {
                canvas.drawLine(x + k, y, x, y + k)
                canvas.drawLine(
                    x + width - k,
                    y + height,
                    x + width,
                    y + height - k
                )
                k += width / 5
            }
        }

        if (value == Square.GREY_OVER_CAP) {
            // A triangle in the top-left corner, following the rounded
            // corner. The canvas has no paths, so it is filled row by row.
            canvas.setColor(OVER_CAP_COLOR)
            val side = width * 0.42
            var row = 0.0
            while (row < side) {
                val inset = roundedInset(row, height, width * 0.15)
                canvas.fillRect(x + inset, y + row, side - row - inset, ROW_HEIGHT)
                row += ROW_HEIGHT
            }
        }

        val textColor = if (theme.cardBackgroundColor == Color.TRANSPARENT) {
            theme.highContrastTextColor
        } else {
            val c1 = squareColor.contrast(theme.cardBackgroundColor)
            val c2 = squareColor.contrast(theme.mediumContrastTextColor)
            if (c1 > c2) theme.cardBackgroundColor else theme.mediumContrastTextColor
        }

        canvas.setColor(textColor)
        canvas.setTextAlign(TextAlign.CENTER)
        canvas.drawText(date.day.toString(), x + width / 2, y + width / 2)

        if (hasNotes) {
            circleColor = when (value) {
                Square.ON, Square.GREY, Square.GREY_OVER_CAP -> theme.lowContrastTextColor
                else -> color
            }
            canvas.setColor(circleColor)
            canvas.fillCircle(x + width - width / 5, y + width / 5, width / 12)
        }
    }

    /**
     * Fills the band between a rounded square and the same square inset by
     * [stroke], row by row. Covering a filled square with a smaller one would
     * be simpler, but the widget's see-through squares would show the
     * colour beneath.
     */
    private fun fillOutline(
        canvas: Canvas,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        radius: Double,
        stroke: Double
    ) {
        val innerHeight = height - 2 * stroke
        val innerRadius = max(0.0, radius - stroke)
        var row = 0.0
        while (row < height) {
            val left = x + roundedInset(row, height, radius)
            val right = x + width - roundedInset(row, height, radius)
            val innerRow = row - stroke
            if (innerRow < 0 || innerRow >= innerHeight) {
                canvas.fillRect(left, y + row, right - left, ROW_HEIGHT)
            } else {
                val inner = stroke + roundedInset(innerRow, innerHeight, innerRadius)
                canvas.fillRect(left, y + row, x + inner - left, ROW_HEIGHT)
                canvas.fillRect(x + width - inner, y + row, right - (x + width - inner), ROW_HEIGHT)
            }
            row += ROW_HEIGHT
        }
    }

    /** How far a rounded square's edge sits inside its bounding box on a row. */
    private fun roundedInset(row: Double, height: Double, radius: Double): Double {
        val dy = max(0.0, max(radius - row, row - (height - radius)))
        return radius - sqrt(max(0.0, radius * radius - dy * dy))
    }

    companion object {
        /** The red of the corner on the day an "at most" period went over its cap. */
        private val OVER_CAP_COLOR = Color(0xE53935)

        /** The height of the rows outlines and corners are filled with. */
        private const val ROW_HEIGHT = 0.5
    }
}
