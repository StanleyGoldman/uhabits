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
package org.isoron.uhabits.core.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NumberFieldTest {
    @Test
    fun testAccepts_eitherSeparatorWhateverTheLocale() {
        assertTrue(NumberField.accepts("1.5"))
        assertTrue(NumberField.accepts("1,5"))
    }

    @Test
    fun testAccepts_notOtherKeys() {
        assertFalse(NumberField.accepts("1a"))
        assertFalse(NumberField.accepts("-1"))
        assertFalse(NumberField.accepts("1 5"))
    }

    @Test
    fun testAccepts_onlyOneSeparator() {
        assertFalse(NumberField.accepts("1.5.2"))
        assertFalse(NumberField.accepts("1,5.2"))
    }
}
