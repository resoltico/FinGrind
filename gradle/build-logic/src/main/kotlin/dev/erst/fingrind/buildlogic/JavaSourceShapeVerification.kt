package dev.erst.fingrind.buildlogic

import com.sun.source.tree.BlockTree
import com.sun.source.tree.CaseTree
import com.sun.source.tree.ClassTree
import com.sun.source.tree.MethodTree
import com.sun.source.tree.SwitchExpressionTree
import com.sun.source.tree.SwitchTree
import com.sun.source.tree.Tree
import com.sun.source.tree.VariableTree
import com.sun.source.util.JavacTask
import com.sun.source.util.TreeScanner
import java.io.File
import java.nio.charset.StandardCharsets
import javax.lang.model.element.Modifier
import javax.tools.ToolProvider
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

private data class JavaSourceShapeBudget(
    val roleName: String,
    val maxPhysicalLines: Int,
    val maxLogicalLines: Int,
    val maxImports: Int,
    val maxNestedTypes: Int,
    val maxMethodsPerTopLevelType: Int,
    val maxFieldsPerTopLevelType: Int,
    val maxSwitchArmsPerMethod: Int,
)

private val productionDefaultBudget =
    JavaSourceShapeBudget(
        roleName = "production-default",
        maxPhysicalLines = 700,
        maxLogicalLines = 650,
        maxImports = 45,
        maxNestedTypes = 4,
        maxMethodsPerTopLevelType = 18,
        maxFieldsPerTopLevelType = 12,
        maxSwitchArmsPerMethod = 12,
    )

private val translationHeavyBudget =
    JavaSourceShapeBudget(
        roleName = "translation-heavy",
        maxPhysicalLines = 700,
        maxLogicalLines = 680,
        maxImports = 60,
        maxNestedTypes = 6,
        maxMethodsPerTopLevelType = 24,
        maxFieldsPerTopLevelType = 18,
        maxSwitchArmsPerMethod = 16,
    )

private val catalogHeavyBudget =
    JavaSourceShapeBudget(
        roleName = "catalog-heavy",
        maxPhysicalLines = 950,
        maxLogicalLines = 900,
        maxImports = 45,
        maxNestedTypes = 24,
        maxMethodsPerTopLevelType = 28,
        maxFieldsPerTopLevelType = 32,
        maxSwitchArmsPerMethod = 18,
    )

private val aggregateModelBudget =
    JavaSourceShapeBudget(
        roleName = "aggregate-model",
        maxPhysicalLines = 1200,
        maxLogicalLines = 1100,
        maxImports = 45,
        maxNestedTypes = 80,
        maxMethodsPerTopLevelType = 80,
        maxFieldsPerTopLevelType = 80,
        maxSwitchArmsPerMethod = 24,
    )

private val testBudget =
    JavaSourceShapeBudget(
        roleName = "test",
        maxPhysicalLines = 1300,
        maxLogicalLines = 1200,
        maxImports = 80,
        maxNestedTypes = 64,
        maxMethodsPerTopLevelType = 40,
        maxFieldsPerTopLevelType = 32,
        maxSwitchArmsPerMethod = 20,
    )

private val testFixturesBudget =
    JavaSourceShapeBudget(
        roleName = "test-fixtures",
        maxPhysicalLines = 1200,
        maxLogicalLines = 1100,
        maxImports = 70,
        maxNestedTypes = 32,
        maxMethodsPerTopLevelType = 36,
        maxFieldsPerTopLevelType = 28,
        maxSwitchArmsPerMethod = 20,
    )

private val fuzzBudget =
    JavaSourceShapeBudget(
        roleName = "fuzz",
        maxPhysicalLines = 1200,
        maxLogicalLines = 1100,
        maxImports = 70,
        maxNestedTypes = 32,
        maxMethodsPerTopLevelType = 24,
        maxFieldsPerTopLevelType = 18,
        maxSwitchArmsPerMethod = 20,
    )

internal fun Project.registerJavaSourceShapeTask() =
    if ("verifyJavaSourceShape" in tasks.names) {
        tasks.named<VerifyJavaSourceShapeTask>("verifyJavaSourceShape")
    } else {
        tasks.register<VerifyJavaSourceShapeTask>("verifyJavaSourceShape") {
            group = "verification"
            description =
                "Fails the build when Java source files exceed FinGrind's structural shape budgets."
            projectPathValue.set(path)
            projectDirectoryPath.set(projectDir.invariantSeparatorsPath())
            sourceFiles.from(
                fileTree(projectDir) {
                    include("src/*/java/**/*.java")
                    exclude("**/build/**", "**/.gradle/**")
                },
            )
        }
    }

abstract class VerifyJavaSourceShapeTask : DefaultTask() {
    @get:Input
    abstract val projectPathValue: Property<String>

    @get:Input
    abstract val projectDirectoryPath: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val projectDirectory = File(projectDirectoryPath.get())
        val violations = mutableListOf<String>()
        sourceFiles.files
            .sortedBy { it.invariantSeparatorsPath() }
            .forEach { file ->
                val relativePath = file.displayPath(projectDirectory)
                if (file.name == "module-info.java" || file.name == "package-info.java") {
                    return@forEach
                }
                val budget = budgetFor(file)
                val metrics = JavaSourceShapeMetrics.measure(file)
                if (
                    file.invariantSeparatorsPath().contains("/src/main/java/") &&
                        forbiddenGenericClassNamePattern.matches(file.name)
                ) {
                    violations +=
                        "$relativePath: generic production class names like ${file.name} are forbidden; give the file one explicit domain or boundary role."
                }
                if (metrics.physicalLineCount > budget.maxPhysicalLines) {
                    violations +=
                        "$relativePath: ${metrics.physicalLineCount} physical lines exceeds ${budget.maxPhysicalLines} for ${budget.roleName}; split the file by responsibility."
                }
                if (metrics.logicalLineCount > budget.maxLogicalLines) {
                    violations +=
                        "$relativePath: ${metrics.logicalLineCount} logical lines exceeds ${budget.maxLogicalLines} for ${budget.roleName}; remove responsibility accretion."
                }
                if (metrics.importCount > budget.maxImports) {
                    violations +=
                        "$relativePath: ${metrics.importCount} imports exceeds ${budget.maxImports} for ${budget.roleName}; reduce fan-out or split the class."
                }
                if (metrics.nestedTypeCount > budget.maxNestedTypes) {
                    violations +=
                        "$relativePath: ${metrics.nestedTypeCount} nested type declarations exceeds ${budget.maxNestedTypes} for ${budget.roleName}; promote focused collaborators into their own files."
                }
                if (metrics.maxMethodsPerTopLevelType > budget.maxMethodsPerTopLevelType) {
                    violations +=
                        "$relativePath: ${metrics.maxMethodsPerTopLevelType} methods/constructors on one top-level type exceeds ${budget.maxMethodsPerTopLevelType} for ${budget.roleName}; split the type by responsibility."
                }
                if (metrics.maxFieldsPerTopLevelType > budget.maxFieldsPerTopLevelType) {
                    violations +=
                        "$relativePath: ${metrics.maxFieldsPerTopLevelType} fields on one top-level type exceeds ${budget.maxFieldsPerTopLevelType} for ${budget.roleName}; reduce retained state or split collaborators."
                }
                if (metrics.maxSwitchArmsPerMethod > budget.maxSwitchArmsPerMethod) {
                    violations +=
                        "$relativePath: ${metrics.maxSwitchArmsPerMethod} switch arms in one method exceeds ${budget.maxSwitchArmsPerMethod} for ${budget.roleName}; break the dispatcher into narrower owners."
                }
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("FinGrind Java source-shape violations:")
                    violations.forEach(::appendLine)
                },
            )
        }
    }

    private fun budgetFor(file: File): JavaSourceShapeBudget {
        val normalizedPath = file.invariantSeparatorsPath()
        return when {
            normalizedPath.contains("/src/testFixtures/java/") -> testFixturesBudget
            normalizedPath.contains("/src/test/java/") -> testBudget
            normalizedPath.contains("/src/fuzz/java/") -> fuzzBudget
            aggregateModelClassNamePattern.matches(file.name) -> aggregateModelBudget
            catalogHeavyClassNamePattern.matches(file.name) -> catalogHeavyBudget
            translationHeavyClassNamePattern.matches(file.name) -> translationHeavyBudget
            else -> productionDefaultBudget
        }
    }
}

private data class JavaSourceShapeMetrics(
    val physicalLineCount: Int,
    val logicalLineCount: Int,
    val importCount: Int,
    val nestedTypeCount: Int,
    val maxMethodsPerTopLevelType: Int,
    val maxFieldsPerTopLevelType: Int,
    val maxSwitchArmsPerMethod: Int,
) {
    companion object {
        fun measure(file: File): JavaSourceShapeMetrics {
            val sanitizedSource = sanitize(file.readText())
            val lines = sanitizedSource.lines()
            val logicalLineCount =
                lines.count { line ->
                    val trimmed = line.trim()
                    trimmed.isNotEmpty()
                }
            val importCount = lines.count { it.trim().startsWith("import ") }
            val astMetrics = JavaAstShapeMetrics.measure(file)
            return JavaSourceShapeMetrics(
                physicalLineCount = lines.size,
                logicalLineCount = logicalLineCount,
                importCount = importCount,
                nestedTypeCount = astMetrics.nestedTypeCount,
                maxMethodsPerTopLevelType = astMetrics.maxMethodsPerTopLevelType,
                maxFieldsPerTopLevelType = astMetrics.maxFieldsPerTopLevelType,
                maxSwitchArmsPerMethod = astMetrics.maxSwitchArmsPerMethod,
            )
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

private data class JavaAstShapeMetrics(
    val nestedTypeCount: Int,
    val maxMethodsPerTopLevelType: Int,
    val maxFieldsPerTopLevelType: Int,
    val maxSwitchArmsPerMethod: Int,
) {
    companion object {
        fun measure(file: File): JavaAstShapeMetrics {
            val compiler =
                ToolProvider.getSystemJavaCompiler()
                    ?: throw GradleException("JDK compiler is required to inspect Java source shape.")
            compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8).use { fileManager ->
                val javaFileObjects = fileManager.getJavaFileObjects(file)
                val task =
                    compiler.getTask(
                        null,
                        fileManager,
                        null,
                        listOf("-proc:none"),
                        null,
                        javaFileObjects,
                    ) as JavacTask
                val units = task.parse().toList()
                var nestedTypeCount = 0
                var maxMethods = 0
                var maxFields = 0
                var maxSwitchArms = 0
                units.forEach { unit ->
                    unit.typeDecls
                        .filterIsInstance<ClassTree>()
                        .forEach { topLevelType ->
                            val metrics = TopLevelTypeShapeScanner.measure(topLevelType)
                            nestedTypeCount += metrics.nestedTypeCount
                            maxMethods = maxOf(maxMethods, metrics.methodCount)
                            maxFields = maxOf(maxFields, metrics.fieldCount)
                            maxSwitchArms = maxOf(maxSwitchArms, metrics.maxSwitchArms)
                        }
                }
                return JavaAstShapeMetrics(
                    nestedTypeCount = nestedTypeCount,
                    maxMethodsPerTopLevelType = maxMethods,
                    maxFieldsPerTopLevelType = maxFields,
                    maxSwitchArmsPerMethod = maxSwitchArms,
                )
            }
        }
    }
}

private data class TopLevelTypeShapeMetrics(
    val methodCount: Int,
    val fieldCount: Int,
    val nestedTypeCount: Int,
    val maxSwitchArms: Int,
)

private val constantFieldModifiers = setOf(Modifier.STATIC, Modifier.FINAL)

private object TopLevelTypeShapeScanner {
    fun measure(topLevelType: ClassTree): TopLevelTypeShapeMetrics {
        var methodCount = 0
        var fieldCount = 0
        var nestedTypeCount = 0
        var maxSwitchArms = 0
        val countFields =
            topLevelType.kind != Tree.Kind.ENUM && topLevelType.kind != Tree.Kind.RECORD
        topLevelType.members.forEach { member ->
            when (member) {
                is MethodTree -> {
                    methodCount += 1
                    maxSwitchArms =
                        maxOf(maxSwitchArms, MaxSwitchArmScanner.measure(member.body))
                }
                is VariableTree ->
                    if (countFields && !member.modifiers.flags.containsAll(constantFieldModifiers)) {
                        fieldCount += 1
                    }
                is ClassTree -> {
                    nestedTypeCount += 1
                    val nestedMetrics = NestedTypeShapeScanner.measure(member)
                    nestedTypeCount += nestedMetrics.nestedTypeCount
                    maxSwitchArms = maxOf(maxSwitchArms, nestedMetrics.maxSwitchArms)
                }
                is BlockTree -> {
                    maxSwitchArms =
                        maxOf(maxSwitchArms, MaxSwitchArmScanner.measure(member))
                }
                else -> Unit
            }
        }
        return TopLevelTypeShapeMetrics(
            methodCount = methodCount,
            fieldCount = fieldCount,
            nestedTypeCount = nestedTypeCount,
            maxSwitchArms = maxSwitchArms,
        )
    }
}

private data class NestedTypeShapeMetrics(
    val nestedTypeCount: Int,
    val maxSwitchArms: Int,
)

private object NestedTypeShapeScanner {
    fun measure(typeTree: ClassTree): NestedTypeShapeMetrics {
        var nestedTypeCount = 0
        var maxSwitchArms = 0
        typeTree.members.forEach { member ->
            when (member) {
                is MethodTree -> {
                    maxSwitchArms =
                        maxOf(maxSwitchArms, MaxSwitchArmScanner.measure(member.body))
                }
                is ClassTree -> {
                    nestedTypeCount += 1
                    val nestedMetrics = measure(member)
                    nestedTypeCount += nestedMetrics.nestedTypeCount
                    maxSwitchArms = maxOf(maxSwitchArms, nestedMetrics.maxSwitchArms)
                }
                is BlockTree -> {
                    maxSwitchArms =
                        maxOf(maxSwitchArms, MaxSwitchArmScanner.measure(member))
                }
                else -> Unit
            }
        }
        return NestedTypeShapeMetrics(
            nestedTypeCount = nestedTypeCount,
            maxSwitchArms = maxSwitchArms,
        )
    }
}

private object MaxSwitchArmScanner : TreeScanner<Unit, MutableInt>() {
    fun measure(tree: Tree?): Int {
        if (tree == null) {
            return 0
        }
        val maxSwitchArms = MutableInt()
        scan(tree, maxSwitchArms)
        return maxSwitchArms.value
    }

    override fun visitSwitch(node: SwitchTree, accumulator: MutableInt) {
        accumulator.value = maxOf(accumulator.value, node.cases.armCount())
        super.visitSwitch(node, accumulator)
    }

    override fun visitSwitchExpression(node: SwitchExpressionTree, accumulator: MutableInt) {
        accumulator.value = maxOf(accumulator.value, node.cases.armCount())
        super.visitSwitchExpression(node, accumulator)
    }
}

private fun List<CaseTree>.armCount(): Int = size

private class MutableInt(var value: Int = 0)
