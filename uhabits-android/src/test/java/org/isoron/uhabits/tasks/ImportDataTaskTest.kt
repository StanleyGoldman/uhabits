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
package org.isoron.uhabits.tasks

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.isoron.platform.io.JavaDatabaseOpener
import org.isoron.platform.io.JavaFileOpener
import org.isoron.uhabits.BaseAndroidJVMTest
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.io.GenericImporter
import org.isoron.uhabits.core.io.HabitBullCSVImporter
import org.isoron.uhabits.core.io.LoopDBImporter
import org.isoron.uhabits.core.io.RewireDBImporter
import org.isoron.uhabits.core.io.StandardLogging
import org.isoron.uhabits.core.io.TickmateDBImporter
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.memory.MemoryModelFactory
import org.isoron.uhabits.core.models.sqlite.SQLModelFactory
import org.isoron.uhabits.core.tasks.CoroutineTaskRunner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.InputStream

class ImportDataTaskTest : BaseAndroidJVMTest() {
    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var habitList: HabitList
    private var result: Int? = null

    @Test
    fun validBackupSucceeds() = runTest {
        runImport { ByteArrayInputStream(HABITBULL_CSV.toByteArray()) }
        assertEquals(ImportDataTask.SUCCESS, result)
        assertEquals(1, habitList.size())
        assertNoTempFilesLeft()
    }

    @Test
    fun sourceThatThrowsOnOpenFails() = runTest {
        runImport { throw RuntimeException("NetworkOnMainThreadException") }
        assertEquals(ImportDataTask.FAILED, result)
        assertNoTempFilesLeft()
    }

    @Test
    fun sourceThatThrowsOnReadFails() = runTest {
        runImport {
            object : InputStream() {
                override fun read(): Int = throw RuntimeException("provider failed")
            }
        }
        assertEquals(ImportDataTask.FAILED, result)
        assertNoTempFilesLeft()
    }

    @Test
    fun nullSourceStreamFails() = runTest {
        runImport { null }
        assertEquals(ImportDataTask.FAILED, result)
        assertNoTempFilesLeft()
    }

    @Test
    fun unknownFileIsNotRecognized() = runTest {
        runImport { ByteArrayInputStream("hello world".toByteArray()) }
        assertEquals(ImportDataTask.NOT_RECOGNIZED, result)
        assertNoTempFilesLeft()
    }

    private suspend fun runImport(openSource: () -> InputStream?) {
        val modelFactory = MemoryModelFactory()
        habitList = modelFactory.buildHabitList()
        val dbOpener = JavaDatabaseOpener()
        val commandRunner = CommandRunner(
            CoroutineTaskRunner(UnconfinedTestDispatcher(), UnconfinedTestDispatcher())
        )
        val importer = GenericImporter(
            LoopDBImporter(
                habitList,
                modelFactory,
                dbOpener,
                commandRunner,
                StandardLogging(),
                JavaFileOpener()
            ),
            RewireDBImporter(habitList, modelFactory, dbOpener),
            TickmateDBImporter(habitList, modelFactory, dbOpener),
            HabitBullCSVImporter(habitList, modelFactory, StandardLogging())
        )
        val sqlModelFactory = SQLModelFactory(dbOpener.open(":memory:"))
        val task = ImportDataTask(
            importer,
            sqlModelFactory,
            openSource,
            { tempDir.root }
        ) { result = it }
        task.doInBackground()
        task.onPostExecute()
    }

    private fun assertNoTempFilesLeft() {
        assertEquals(emptyList<String>(), tempDir.root.list()!!.toList())
    }

    companion object {
        private const val HABITBULL_CSV =
            "HabitName,HabitDescription,HabitCategory,CalendarDate,Value,CommentText\n" +
                "Breed dragons,with love and fire,Diet & Food,2016-03-18,1,text\n"
    }
}
