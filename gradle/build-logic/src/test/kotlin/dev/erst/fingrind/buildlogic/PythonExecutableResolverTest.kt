package dev.erst.fingrind.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PythonExecutableResolverTest {
    @Test
    fun selectExactPythonExecutable_prefersExactPathCandidatesBeforeGenericNames() {
        val selected =
            selectExactPythonExecutable(
                requiredPythonVersion = "3.12",
                osName = "Linux",
                probeVersion = { command ->
                    when (command) {
                        "python3.12" -> 3 to 12
                        "python3" -> 3 to 12
                        else -> null
                    }
                },
                findUvManagedPythonExecutable = { error("uv-managed fallback should not be used") },
            )

        assertEquals("python3.12", selected)
    }

    @Test
    fun selectExactPythonExecutable_rejectsHigherVersionGenericInterpreter() {
        val selected =
            selectExactPythonExecutable(
                requiredPythonVersion = "3.12",
                osName = "Linux",
                probeVersion = { command ->
                    when (command) {
                        "python3.12" -> null
                        "python3" -> 3 to 13
                        "python" -> 3 to 13
                        else -> null
                    }
                },
                findUvManagedPythonExecutable = { _ -> null },
            )

        assertNull(selected)
    }

    @Test
    fun selectExactPythonExecutable_acceptsExactUvManagedInterpreterOnlyWhenVersionMatches() {
        val selected =
            selectExactPythonExecutable(
                requiredPythonVersion = "3.12",
                osName = "Windows 11",
                probeVersion = { command ->
                    when (command) {
                        "python" -> 3 to 13
                        "C:/Users/test/.local/bin/python3.12" -> 3 to 12
                        else -> null
                    }
                },
                findUvManagedPythonExecutable = { "C:/Users/test/.local/bin/python3.12" },
            )

        assertEquals("C:/Users/test/.local/bin/python3.12", selected)
    }
}
