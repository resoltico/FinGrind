package dev.erst.fingrind.buildlogic

internal fun productionRuleset() =
    PmdRulesetSpecification(
        name = "FinGrind Java Quality",
        description =
            listOf(
                "High-signal PMD rules for FinGrind Java code. Formatting is enforced by Spotless,",
                "compiler-grade correctness by Error Prone, and PMD covers structural, best-practice,",
                "design, performance, security, and concurrency issues.",
            ),
        rules =
            listOf(
                errorProneRule(),
                bestPracticesRule(),
                productionDesignBundle(),
                plainRule("category/java/design.xml/GodClass"),
                tooManyMethodsRule(maxMethods = 16),
                cyclomaticComplexityRule(methodReportLevel = 14, classReportLevel = 110),
                cognitiveComplexityRule(reportLevel = 18),
                couplingBetweenObjectsRule(threshold = 50),
                plainRule("category/java/multithreading.xml"),
                plainRule("category/java/performance.xml"),
                plainRule("category/java/security.xml"),
                productionCommentRequiredRule(),
                plainRule("category/java/documentation.xml/UncommentedEmptyMethodBody"),
            ),
    )

internal fun mainTestRuleset() =
    PmdRulesetSpecification(
        name = "FinGrind Java Test Quality",
        description =
            listOf(
                "High-signal PMD rules for FinGrind test code. Structural, design, performance, security,",
                "and concurrency checks are enforced to the same standard as production code. Test-specific",
                "relaxations are limited to assertion ergonomics, fixture import breadth, method count, and",
                "explicitly thread-owning concurrency scenarios.",
            ),
        rules =
            listOf(
                errorProneRule(),
                bestPracticesRule(
                    excludedRule(
                        "UnitTestAssertionsShouldIncludeMessage",
                        "Tests optimize for assertion intent and locality rather than repeated message boilerplate.",
                    ),
                    excludedRule(
                        "UnitTestContainsTooManyAsserts",
                        "Wide CLI and protocol matrices legitimately assert several facets of one scenario.",
                    ),
                ),
                mainTestDesignBundle(),
                plainRule("category/java/design.xml/GodClass"),
                cyclomaticComplexityRule(methodReportLevel = 16, classReportLevel = 130),
                cognitiveComplexityRule(reportLevel = 20),
                couplingBetweenObjectsRule(threshold = 72),
                multithreadingRule(
                    excludedRule(
                        "DoNotUseThreads",
                        "Tests are allowed to create threads explicitly for concurrency scenarios.",
                    ),
                ),
                plainRule("category/java/performance.xml"),
                plainRule("category/java/security.xml"),
                testCommentRequiredRule(),
            ),
    )
