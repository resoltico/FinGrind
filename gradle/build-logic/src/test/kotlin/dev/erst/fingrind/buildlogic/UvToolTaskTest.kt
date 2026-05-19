package dev.erst.fingrind.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UvToolTaskTest {
    @Test
    fun parsePythonVersionBanner_acceptsStandardBanner() {
        assertEquals("Python 3.12.10", parsePythonVersionBanner("Python 3.12.10"))
        assertEquals(3 to 12, parsePythonMajorMinor("Python 3.12.10"))
    }

    @Test
    fun parsePythonVersionBanner_acceptsStderrStyleOutput() {
        val output = "warning text\nPython 3.13.2\r\n"

        assertEquals("Python 3.13.2", parsePythonVersionBanner(output))
        assertEquals(3 to 13, parsePythonMajorMinor(output))
    }

    @Test
    fun parsePythonVersionBanner_rejectsMalformedOutput() {
        assertNull(parsePythonVersionBanner("File \"<string>\", line 2"))
        assertNull(parsePythonMajorMinor("File \"<string>\", line 2"))
    }

    @Test
    fun parseRequiredPythonMajorMinor_readsMajorAndMinor() {
        assertEquals(3 to 12, parseRequiredPythonMajorMinor("3.12"))
        assertEquals(3 to 13, parseRequiredPythonMajorMinor("3.13.1"))
    }

    @Test
    fun pythonVersionSatisfiesRequirement_comparesMajorMinorOnly() {
        assertTrue(pythonVersionSatisfiesRequirement(3 to 12, 3 to 12))
        assertTrue(pythonVersionSatisfiesRequirement(3 to 13, 3 to 12))
        assertFalse(pythonVersionSatisfiesRequirement(3 to 11, 3 to 12))
    }
}
