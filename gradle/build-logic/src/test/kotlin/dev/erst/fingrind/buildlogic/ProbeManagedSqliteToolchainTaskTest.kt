package dev.erst.fingrind.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals

class ProbeManagedSqliteToolchainTaskTest {
    @Test
    fun compilerVersion_acceptsWindowsClBannerFromNonZeroExit() {
        val commands = mutableListOf<List<String>>()

        val compilerVersion =
            ManagedSqliteToolchainProbeSupport.compilerVersion("windows", "cl.exe") { commandLine ->
                commands += commandLine
                when (commandLine) {
                    listOf("cl.exe", "/Bv") ->
                        CommandProbe(
                            exitCode = 2,
                            stdout = "Microsoft (R) C/C++ Optimizing Compiler Version 19.44.35226 for x64",
                            stderr = "cl : Command line error D8003 : missing source filename",
                        )

                    else -> error("Unexpected command: $commandLine")
                }
            }

        assertEquals(
            "Microsoft (R) C/C++ Optimizing Compiler Version 19.44.35226 for x64\n" +
                "cl : Command line error D8003 : missing source filename",
            compilerVersion,
        )
        assertEquals(listOf(listOf("cl.exe", "/Bv")), commands)
    }

    @Test
    fun linkerVersion_acceptsWindowsLinkBannerFromNonZeroExit() {
        val linkerVersion =
            ManagedSqliteToolchainProbeSupport.linkerVersion("windows") { commandLine ->
                when (commandLine) {
                    listOf("link") ->
                        CommandProbe(
                            exitCode = 1104,
                            stdout = "Microsoft (R) Incremental Linker Version 14.44.35226.0",
                            stderr = "LINK : fatal error LNK1104: cannot open file '.obj'",
                        )

                    else -> error("Unexpected command: $commandLine")
                }
            }

        assertEquals(
            "Microsoft (R) Incremental Linker Version 14.44.35226.0\n" +
                "LINK : fatal error LNK1104: cannot open file '.obj'",
            linkerVersion,
        )
    }

    @Test
    fun compilerVersion_keepsUnixProbeStrictAboutSuccessExitCodes() {
        val commands = mutableListOf<List<String>>()

        val compilerVersion =
            ManagedSqliteToolchainProbeSupport.compilerVersion("linux", "cc") { commandLine ->
                commands += commandLine
                when (commandLine) {
                    listOf("cc", "--version") ->
                        CommandProbe(
                            exitCode = 2,
                            stdout = "cc version output that should not be trusted on failure",
                            stderr = "",
                        )

                    listOf("cc", "-v") ->
                        CommandProbe(
                            exitCode = 0,
                            stdout = "",
                            stderr = "Apple clang version 17.0.0",
                        )

                    else -> error("Unexpected command: $commandLine")
                }
            }

        assertEquals("Apple clang version 17.0.0", compilerVersion)
        assertEquals(
            listOf(
                listOf("cc", "--version"),
                listOf("cc", "-v"),
            ),
            commands,
        )
    }
}
