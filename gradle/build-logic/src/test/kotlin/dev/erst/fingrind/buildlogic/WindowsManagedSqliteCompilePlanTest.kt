package dev.erst.fingrind.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsManagedSqliteCompilePlanTest {
    @Test
    fun commandLine_preservesMsvcArgumentBoundariesForHardeningAndWindowsPaths() {
        val sourceFilePath = "C:\\FinGrind source\\SQLite ユニコード\\sqlite3mc_amalgamation.c"
        val outputLibraryFilePath = "C:\\FinGrind build\\release artifacts\\sqlite3.dll"
        val importLibraryFilePath = "C:\\FinGrind build\\release artifacts\\sqlite3.lib"
        val objectFilePath = "C:\\FinGrind build\\release artifacts\\sqlite3.obj"

        val commandLine =
            WindowsManagedSqliteCompilePlan.commandLine(
                compiler = "cl.exe",
                sourceFilePath = sourceFilePath,
                requiredCompileOptions =
                    listOf(
                        "THREADSAFE=1",
                        "OMIT_LOAD_EXTENSION",
                        "TEMP_STORE=3",
                        "SECURE_DELETE",
                    ),
                requiresSecureMemorySupport = true,
                compilerHardeningFlags = listOf("/GS", "/permissive-"),
                linkerHardeningFlags = listOf("/DYNAMICBASE", "/NXCOMPAT", "/GUARD:CF"),
                outputLibraryFilePath = outputLibraryFilePath,
                importLibraryFilePath = importLibraryFilePath,
                objectFilePath = objectFilePath,
            )

        assertEquals(
            listOf(
                "cl.exe",
                "/nologo",
                "/O2",
                "/GS",
                "/permissive-",
                "/LD",
                "/DSQLITE_THREADSAFE=1",
                "/DSQLITE_OMIT_LOAD_EXTENSION=1",
                "/DSQLITE_TEMP_STORE=3",
                "/DSQLITE_SECURE_DELETE=1",
                "/DSQLITE3MC_SECURE_MEMORY=1",
                "/DSQLITE_WIN32_HAS_WIDE=1",
                "/DSQLITE_API=__declspec(dllexport)",
                "/Fo\"$objectFilePath\"",
                sourceFilePath,
                "/link",
                "/NOLOGO",
                "/INCREMENTAL:NO",
                "/DYNAMICBASE",
                "/NXCOMPAT",
                "/GUARD:CF",
                "/OUT:\"$outputLibraryFilePath\"",
                "/IMPLIB:\"$importLibraryFilePath\"",
            ),
            commandLine,
        )
    }

    @Test
    fun commandLine_omitsSecureMemoryDefineWhenTheContractDoesNotRequireIt() {
        val commandLine =
            WindowsManagedSqliteCompilePlan.commandLine(
                compiler = "cl.exe",
                sourceFilePath = "C:\\source\\sqlite3mc_amalgamation.c",
                requiredCompileOptions = listOf("THREADSAFE=1"),
                requiresSecureMemorySupport = false,
                compilerHardeningFlags = emptyList(),
                linkerHardeningFlags = emptyList(),
                outputLibraryFilePath = "C:\\output\\sqlite3.dll",
                importLibraryFilePath = "C:\\output\\sqlite3.lib",
                objectFilePath = "C:\\output\\sqlite3.obj",
            )

        assertEquals(
            listOf(
                "cl.exe",
                "/nologo",
                "/O2",
                "/LD",
                "/DSQLITE_THREADSAFE=1",
                "/DSQLITE_WIN32_HAS_WIDE=1",
                "/DSQLITE_API=__declspec(dllexport)",
                "/Fo\"C:\\output\\sqlite3.obj\"",
                "C:\\source\\sqlite3mc_amalgamation.c",
                "/link",
                "/NOLOGO",
                "/INCREMENTAL:NO",
                "/OUT:\"C:\\output\\sqlite3.dll\"",
                "/IMPLIB:\"C:\\output\\sqlite3.lib\"",
            ),
            commandLine,
        )
    }
}
