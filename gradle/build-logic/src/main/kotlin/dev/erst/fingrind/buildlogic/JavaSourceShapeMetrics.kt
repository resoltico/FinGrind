package dev.erst.fingrind.buildlogic

import java.io.File

internal data class JavaSourceShapeMetrics(
    val physicalLineCount: Int,
    val logicalLineCount: Int,
    val importCount: Int,
    val nestedTypeCount: Int,
    val maxMethodsPerTopLevelType: Int,
    val maxFieldsPerTopLevelType: Int,
    val maxSwitchArmsPerMethod: Int,
    val maxMethodLineSpan: Int,
    val maxMethodParameters: Int,
    val maxMethodDecisionPoints: Int,
) {
    companion object {
        fun measure(file: File): JavaSourceShapeMetrics {
            val source = file.readText()
            val sanitizedSource = sanitize(source)
            val lines = physicalLines(sanitizedSource)
            val logicalLineCount =
                lines.count { line ->
                    val trimmed = line.trim()
                    trimmed.isNotEmpty()
                }
            val importCount = lines.count { it.trim().startsWith("import ") }
            val astMetrics = JavaAstShapeMetrics.measure(file)
            return JavaSourceShapeMetrics(
                physicalLineCount = physicalLineCount(source),
                logicalLineCount = logicalLineCount,
                importCount = importCount,
                nestedTypeCount = astMetrics.nestedTypeCount,
                maxMethodsPerTopLevelType = astMetrics.maxMethodsPerTopLevelType,
                maxFieldsPerTopLevelType = astMetrics.maxFieldsPerTopLevelType,
                maxSwitchArmsPerMethod = astMetrics.maxSwitchArmsPerMethod,
                maxMethodLineSpan = astMetrics.maxMethodLineSpan,
                maxMethodParameters = astMetrics.maxMethodParameters,
                maxMethodDecisionPoints = astMetrics.maxMethodDecisionPoints,
            )
        }

        private fun physicalLineCount(source: String): Int =
            if (source.isEmpty()) {
                0
            } else {
                source.count { it == '\n' } + if (source.endsWith('\n')) 0 else 1
            }

        private fun physicalLines(source: String): List<String> =
            if (source.isEmpty()) {
                emptyList()
            } else {
                source.split('\n')
            }

        private fun sanitize(source: String): String {
            val builder = StringBuilder(source.length)
            var inBlockComment = false
            var inLineComment = false
            var inString = false
            var inChar = false
            var escape = false
            var index = 0
            while (index < source.length) {
                val character = source[index]
                val nextCharacter = source.getOrNull(index + 1)
                when {
                    inLineComment -> {
                        if (character == '\n') {
                            inLineComment = false
                            builder.append(character)
                        } else {
                            builder.append(' ')
                        }
                    }
                    inBlockComment -> {
                        if (character == '\n') {
                            builder.append(character)
                        } else {
                            builder.append(' ')
                        }
                        if (character == '*' && nextCharacter == '/') {
                            builder.append(' ')
                            index += 1
                            inBlockComment = false
                        }
                    }
                    inString -> {
                        builder.append(if (character == '\n') character else ' ')
                        if (!escape && character == '"') {
                            inString = false
                        }
                        escape = !escape && character == '\\'
                    }
                    inChar -> {
                        builder.append(if (character == '\n') character else ' ')
                        if (!escape && character == '\'') {
                            inChar = false
                        }
                        escape = !escape && character == '\\'
                    }
                    character == '/' && nextCharacter == '/' -> {
                        builder.append("  ")
                        index += 1
                        inLineComment = true
                    }
                    character == '/' && nextCharacter == '*' -> {
                        builder.append("  ")
                        index += 1
                        inBlockComment = true
                    }
                    character == '"' -> {
                        builder.append(' ')
                        inString = true
                        escape = false
                    }
                    character == '\'' -> {
                        builder.append(' ')
                        inChar = true
                        escape = false
                    }
                    else -> builder.append(character)
                }
                index += 1
            }
            return builder.toString()
        }
    }
}
