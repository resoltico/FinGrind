package dev.erst.fingrind.buildlogic

internal enum class FinGrindPmdRulesetSurface(
    val repositoryRelativePath: String,
    val projectRelativePath: String,
) {
    MAIN_PRODUCTION("gradle/pmd/ruleset.xml", "gradle/pmd/ruleset.xml"),
    MAIN_TEST("gradle/pmd/test-ruleset.xml", "gradle/pmd/test-ruleset.xml"),
    JAZZER_PRODUCTION("jazzer/gradle/pmd/ruleset.xml", "gradle/pmd/ruleset.xml"),
    JAZZER_TEST("jazzer/gradle/pmd/test-ruleset.xml", "gradle/pmd/test-ruleset.xml"),
    JAZZER_FUZZ("jazzer/gradle/pmd/fuzz-ruleset.xml", "gradle/pmd/fuzz-ruleset.xml"),
}
