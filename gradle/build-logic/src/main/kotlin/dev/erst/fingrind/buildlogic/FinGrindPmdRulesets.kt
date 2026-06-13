package dev.erst.fingrind.buildlogic

internal object FinGrindPmdRulesets {
    fun render(surface: FinGrindPmdRulesetSurface): String =
        renderPmdRuleset(pmdRulesetSpecification(surface))

    fun surfaces(): List<FinGrindPmdRulesetSurface> = FinGrindPmdRulesetSurface.entries.toList()
}
