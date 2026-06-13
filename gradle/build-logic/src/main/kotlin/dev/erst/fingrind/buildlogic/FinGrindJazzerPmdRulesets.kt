package dev.erst.fingrind.buildlogic

internal fun jazzerTestRuleset() =
    PmdRulesetSpecification(
        name = "FinGrind Java Test Quality",
        description =
            listOf(
                "High-signal PMD rules for FinGrind Jazzer support and regression tests. Structural, design,",
                "performance, security, and concurrency checks stay aligned with repository-wide standards,",
                "while narrowly-scoped relaxations cover reflective fixtures and test-only failure plumbing.",
            ),
        rules =
            listOf(
                errorProneRule(
                    excludedRule(
                        "AvoidAccessibilityAlteration",
                        "Harness-side reflective fixtures intentionally widen access for white-box fault injection.",
                    ),
                ),
                bestPracticesRule(
                    excludedRule(
                        "PreserveStackTrace",
                        "Fault-injection helpers sometimes replace stack traces to assert normalized wrapper output.",
                    ),
                    excludedRule(
                        "UnitTestAssertionsShouldIncludeMessage",
                        "Jazzer regression tests optimize for assertion locality over repeated message boilerplate.",
                    ),
                    excludedRule(
                        "UnitTestContainsTooManyAsserts",
                        "One regression often checks several correlated crash-shape facets in one executable flow.",
                    ),
                ),
                jazzerTestDesignBundle(),
                plainRule("category/java/design.xml/GodClass"),
                cyclomaticComplexityRule(methodReportLevel = 18, classReportLevel = 140),
                cognitiveComplexityRule(reportLevel = 22),
                couplingBetweenObjectsRule(threshold = 72),
                multithreadingRule(
                    excludedRule(
                        "DoNotUseThreads",
                        "Tests are allowed to create threads explicitly for concurrency scenarios.",
                    ),
                ),
                plainRule("category/java/performance.xml"),
                plainRule("category/java/security.xml"),
            ),
    )

internal fun jazzerFuzzRuleset() =
    PmdRulesetSpecification(
        name = "FinGrind Java Fuzz Quality",
        description =
            listOf(
                "High-signal PMD rules for Jazzer harness code. Fuzz classes are long-lived contract",
                "executables, so they keep the production-quality structural and correctness checks, with",
                "narrowly-scoped relaxations for the single-@FuzzTest class model.",
            ),
        rules =
            listOf(
                errorProneRule(
                    excludedRule(
                        "TestClassWithoutTestCases",
                        "@FuzzTest harnesses are intentionally executable entrypoints rather than named JUnit suites.",
                    ),
                ),
                bestPracticesRule(),
                fuzzDesignBundle(),
                plainRule("category/java/design.xml/GodClass"),
                cyclomaticComplexityRule(methodReportLevel = 18, classReportLevel = 140),
                cognitiveComplexityRule(reportLevel = 22),
                couplingBetweenObjectsRule(threshold = 60),
                plainRule("category/java/multithreading.xml"),
                plainRule("category/java/performance.xml"),
                plainRule("category/java/security.xml"),
                productionCommentRequiredRule(),
                plainRule("category/java/documentation.xml/UncommentedEmptyMethodBody"),
            ),
    )
