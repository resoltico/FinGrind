package dev.erst.fingrind.buildlogic

internal fun pmdRulesetSpecification(surface: FinGrindPmdRulesetSurface): PmdRulesetSpecification =
    when (surface) {
        FinGrindPmdRulesetSurface.MAIN_PRODUCTION,
        FinGrindPmdRulesetSurface.JAZZER_PRODUCTION -> productionRuleset()
        FinGrindPmdRulesetSurface.MAIN_TEST -> mainTestRuleset()
        FinGrindPmdRulesetSurface.JAZZER_TEST -> jazzerTestRuleset()
        FinGrindPmdRulesetSurface.JAZZER_FUZZ -> jazzerFuzzRuleset()
    }
