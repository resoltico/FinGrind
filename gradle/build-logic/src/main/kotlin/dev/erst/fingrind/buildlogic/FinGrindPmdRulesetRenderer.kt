package dev.erst.fingrind.buildlogic

internal fun renderPmdRuleset(specification: PmdRulesetSpecification): String =
    buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("<ruleset")
        appendLine("    name=\"${specification.name}\"")
        appendLine("    xmlns=\"http://pmd.sourceforge.net/ruleset/2.0.0\"")
        appendLine("    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
        appendLine(
            """    xsi:schemaLocation="http://pmd.sourceforge.net/ruleset/2.0.0 https://pmd.github.io/pmd-7.0.0/ruleset_xml_schema.xsd">""",
        )
        appendLine("    <description>")
        specification.description.forEach { line ->
            appendLine("        $line")
        }
        appendLine("    </description>")
        specification.rules.forEach { rule ->
            appendLine()
            append(renderPmdRule(rule))
        }
        appendLine()
        appendLine("</ruleset>")
    }

private fun renderPmdRule(rule: PmdRuleSpecification): String =
    buildString {
        if (rule.exclusions.isEmpty() && rule.properties.isEmpty()) {
            appendLine("""    <rule ref="${rule.ref}"/>""")
            return@buildString
        }
        appendLine("""    <rule ref="${rule.ref}">""")
        rule.exclusions.forEach { exclusion ->
            exclusion.reason?.let { reason ->
                renderPmdComment(reason).forEach { commentLine ->
                    appendLine("        $commentLine")
                }
            }
            appendLine("""        <exclude name="${exclusion.name}"/>""")
        }
        if (rule.properties.isNotEmpty()) {
            appendLine("        <properties>")
            rule.properties.forEach { property ->
                appendLine("""            <property name="${property.name}" value="${property.value}"/>""")
            }
            appendLine("        </properties>")
        }
        appendLine("    </rule>")
    }

private fun renderPmdComment(reason: String): List<String> {
    val lines = reason.trim().split('\n').map(String::trim).filter(String::isNotEmpty)
    return if (lines.size == 1) {
        listOf("<!-- ${lines.single()} -->")
    } else {
        buildList {
            add("<!-- ${lines.first()}")
            lines.drop(1).dropLast(1).forEach { line ->
                add("     $line")
            }
            add("     ${lines.last()} -->")
        }
    }
}
