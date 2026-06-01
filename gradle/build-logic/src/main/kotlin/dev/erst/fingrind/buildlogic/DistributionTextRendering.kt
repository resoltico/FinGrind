package dev.erst.fingrind.buildlogic

object DistributionTextRendering {
    fun jsonString(value: String): String =
        buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
            append('"')
        }

    fun markdownBulletList(values: List<String>): String =
        if (values.isEmpty()) {
            "- none"
        } else {
            values.joinToString(separator = System.lineSeparator()) { value -> "- `$value`" }
        }
}
