package dev.erst.fingrind.buildlogic

internal fun errorProneRule(vararg extraExclusions: PmdRuleExclusion) =
    bundleRule(
        ref = "category/java/errorprone.xml",
        exclusions =
            listOf(
                excludedRule("AvoidCatchingGenericException"),
                excludedRule("AvoidDuplicateLiterals"),
                excludedRule("AvoidFieldNameMatchingMethodName"),
                excludedRule("AvoidLiteralsInIfCondition"),
                excludedRule("NullAssignment"),
            ) + extraExclusions,
    )

internal fun bestPracticesRule(vararg extraExclusions: PmdRuleExclusion) =
    bundleRule(
        ref = "category/java/bestpractices.xml",
        exclusions = extraExclusions.toList(),
    )

internal fun productionDesignBundle() =
    bundleRule(
        ref = "category/java/design.xml",
        exclusions =
            structuralBaseDesignExclusions() +
                listOf(
                    excludedRule(
                        "ExcessiveImports",
                        "FinGrind bans wildcard imports through a dedicated source-policy gate. Retaining ExcessiveImports here would push developers back toward forbidden wildcard imports instead of enforcing the real rule we care about.",
                    ),
                    excludedRule(
                        "UseObjectForClearerAPI",
                        "withExceptionData() takes 3-4 nullable Strings representing distinct exception location fields. A wrapper record would add ceremony for no semantic gain.",
                    ),
                ),
    )

internal fun mainTestDesignBundle() =
    bundleRule(
        ref = "category/java/design.xml",
        exclusions = sharedTestDesignExclusions(),
    )

internal fun jazzerTestDesignBundle() =
    bundleRule(
        ref = "category/java/design.xml",
        exclusions =
            sharedTestDesignExclusions() +
                listOf(
                    excludedRule(
                        "SignatureDeclareThrowsException",
                        "Reflection-heavy tests sometimes declare broad throws clauses to keep fixture control flow readable.",
                    ),
                    excludedRule(
                        "PublicMemberInNonPublicType",
                        "Some enum fixtures intentionally expose public wire-value factories on package-private types to simulate reflective contracts under test.",
                    ),
                ),
    )

internal fun fuzzDesignBundle() =
    bundleRule(
        ref = "category/java/design.xml",
        exclusions =
            structuralBaseDesignExclusions() +
                listOf(
                    excludedRule(
                        "ExcessiveImports",
                        "FinGrind bans wildcard imports through a dedicated source-policy gate. Retaining ExcessiveImports here would push developers back toward forbidden wildcard imports instead of enforcing the real rule we care about.",
                    ),
                    excludedRule(
                        "UseObjectForClearerAPI",
                        "@FuzzTest harnesses are intentionally single-method entrypoints rather than JUnit-style suites with named @Test cases.",
                    ),
                ),
    )

internal fun structuralBaseDesignExclusions(): List<PmdRuleExclusion> =
    listOf(
        excludedRule(
            "LawOfDemeter",
            "Demeter violations are too noisy for a layered facade API.",
        ),
        excludedRule(
            "LoosePackageCoupling",
            "LoosePackageCoupling requires explicit package configurations to be useful.",
        ),
        excludedRule(
            "DataClass",
            "DataClass produces false positives on well-structured value and command objects.",
        ),
        excludedRule("GodClass"),
        excludedRule("TooManyMethods"),
        excludedRule("CyclomaticComplexity"),
        excludedRule("CognitiveComplexity"),
        excludedRule("CouplingBetweenObjects"),
        excludedRule(
            "NcssCount",
            "File and method size governance lives in FinGrind's structural-governance engine rather than PMD's ambient NCSS defaults.",
        ),
    )

internal fun sharedTestDesignExclusions(): List<PmdRuleExclusion> =
    listOf(
        excludedRule(
            "LawOfDemeter",
            "Demeter violations are too noisy for a layered facade API.",
        ),
        excludedRule(
            "LoosePackageCoupling",
            "LoosePackageCoupling requires explicit package configurations to be useful.",
        ),
        excludedRule(
            "DataClass",
            "DataClass produces false positives on well-structured value and command objects.",
        ),
        excludedRule(
            "ExcessiveImports",
            "Test helpers legitimately import many assertion and fixture types.",
        ),
        excludedRule("TooManyMethods", "Test classes legitimately contain many test methods."),
        excludedRule("GodClass"),
        excludedRule(
            "NcssCount",
            "File and method size governance lives in FinGrind's structural-governance engine rather than PMD's ambient NCSS defaults.",
        ),
    )

internal fun tooManyMethodsRule(maxMethods: Int) =
    configuredRule(
        ref = "category/java/design.xml/TooManyMethods",
        properties = listOf(PmdRuleProperty(name = "maxmethods", value = maxMethods.toString())),
    )

internal fun cyclomaticComplexityRule(
    methodReportLevel: Int,
    classReportLevel: Int,
) = configuredRule(
    ref = "category/java/design.xml/CyclomaticComplexity",
    properties =
        listOf(
            PmdRuleProperty(name = "methodReportLevel", value = methodReportLevel.toString()),
            PmdRuleProperty(name = "classReportLevel", value = classReportLevel.toString()),
        ),
)

internal fun cognitiveComplexityRule(reportLevel: Int) =
    configuredRule(
        ref = "category/java/design.xml/CognitiveComplexity",
        properties = listOf(PmdRuleProperty(name = "reportLevel", value = reportLevel.toString())),
    )

internal fun couplingBetweenObjectsRule(threshold: Int) =
    configuredRule(
        ref = "category/java/design.xml/CouplingBetweenObjects",
        properties = listOf(PmdRuleProperty(name = "threshold", value = threshold.toString())),
    )

internal fun productionCommentRequiredRule() =
    configuredRule(
        ref = "category/java/documentation.xml/CommentRequired",
        properties =
            listOf(
                PmdRuleProperty(name = "classCommentRequirement", value = "required"),
                PmdRuleProperty(name = "enumCommentRequirement", value = "required"),
                PmdRuleProperty(name = "publicMethodCommentRequirement", value = "required"),
                PmdRuleProperty(name = "methodWithOverrideCommentRequirement", value = "ignored"),
                PmdRuleProperty(name = "accessorCommentRequirement", value = "ignored"),
                PmdRuleProperty(name = "fieldCommentRequirement", value = "ignored"),
                PmdRuleProperty(name = "protectedMethodCommentRequirement", value = "ignored"),
            ),
    )

internal fun testCommentRequiredRule() =
    configuredRule(
        ref = "category/java/documentation.xml/CommentRequired",
        properties =
            listOf(
                PmdRuleProperty(name = "classCommentRequirement", value = "required"),
                PmdRuleProperty(name = "enumCommentRequirement", value = "required"),
                PmdRuleProperty(name = "publicMethodCommentRequirement", value = "ignored"),
                PmdRuleProperty(name = "methodWithOverrideCommentRequirement", value = "ignored"),
                PmdRuleProperty(name = "accessorCommentRequirement", value = "ignored"),
                PmdRuleProperty(name = "fieldCommentRequirement", value = "ignored"),
                PmdRuleProperty(name = "protectedMethodCommentRequirement", value = "ignored"),
            ),
    )

internal fun multithreadingRule(vararg extraExclusions: PmdRuleExclusion) =
    bundleRule(
        ref = "category/java/multithreading.xml",
        exclusions = extraExclusions.toList(),
    )

internal fun plainRule(ref: String) = PmdRuleSpecification(ref = ref)

internal fun bundleRule(
    ref: String,
    exclusions: List<PmdRuleExclusion>,
) = PmdRuleSpecification(ref = ref, exclusions = exclusions)

internal fun configuredRule(
    ref: String,
    properties: List<PmdRuleProperty>,
) = PmdRuleSpecification(ref = ref, properties = properties)

internal fun excludedRule(
    name: String,
    reason: String? = null,
) = PmdRuleExclusion(name = name, reason = reason)
