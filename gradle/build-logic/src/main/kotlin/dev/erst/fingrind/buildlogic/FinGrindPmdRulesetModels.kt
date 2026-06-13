package dev.erst.fingrind.buildlogic

internal data class PmdRulesetSpecification(
    val name: String,
    val description: List<String>,
    val rules: List<PmdRuleSpecification>,
)

internal data class PmdRuleSpecification(
    val ref: String,
    val exclusions: List<PmdRuleExclusion> = emptyList(),
    val properties: List<PmdRuleProperty> = emptyList(),
)

internal data class PmdRuleExclusion(
    val name: String,
    val reason: String? = null,
)

internal data class PmdRuleProperty(
    val name: String,
    val value: String,
)
