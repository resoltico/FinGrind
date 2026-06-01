package dev.erst.fingrind.buildlogic

import com.sun.source.tree.BlockTree
import com.sun.source.tree.ClassTree
import com.sun.source.tree.CompilationUnitTree
import com.sun.source.tree.MethodTree
import com.sun.source.tree.Tree
import com.sun.source.tree.VariableTree
import com.sun.source.util.JavacTask
import com.sun.source.util.SourcePositions
import com.sun.source.util.Trees
import java.io.File
import java.nio.charset.StandardCharsets
import javax.lang.model.element.Modifier
import javax.tools.ToolProvider
import org.gradle.api.GradleException

internal data class JavaAstShapeMetrics(
    val nestedTypeCount: Int,
    val maxMethodsPerTopLevelType: Int,
    val maxFieldsPerTopLevelType: Int,
    val maxSwitchArmsPerMethod: Int,
    val maxMethodLineSpan: Int,
    val maxMethodParameters: Int,
    val maxMethodDecisionPoints: Int,
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
                val trees = Trees.instance(task)
                val sourcePositions = trees.sourcePositions
                var nestedTypeCount = 0
                var maxMethods = 0
                var maxFields = 0
                var maxSwitchArms = 0
                var maxMethodLineSpan = 0
                var maxMethodParameters = 0
                var maxMethodDecisionPoints = 0
                units.forEach { unit ->
                    unit.typeDecls
                        .filterIsInstance<ClassTree>()
                        .forEach { topLevelType ->
                            val metrics =
                                TopLevelTypeShapeScanner.measure(
                                    topLevelType,
                                    unit,
                                    sourcePositions,
                                )
                            nestedTypeCount += metrics.nestedTypeCount
                            maxMethods = maxOf(maxMethods, metrics.methodCount)
                            maxFields = maxOf(maxFields, metrics.fieldCount)
                            maxSwitchArms = maxOf(maxSwitchArms, metrics.maxSwitchArms)
                            maxMethodLineSpan = maxOf(maxMethodLineSpan, metrics.maxMethodLineSpan)
                            maxMethodParameters =
                                maxOf(maxMethodParameters, metrics.maxMethodParameters)
                            maxMethodDecisionPoints =
                                maxOf(maxMethodDecisionPoints, metrics.maxMethodDecisionPoints)
                        }
                }
                return JavaAstShapeMetrics(
                    nestedTypeCount = nestedTypeCount,
                    maxMethodsPerTopLevelType = maxMethods,
                    maxFieldsPerTopLevelType = maxFields,
                    maxSwitchArmsPerMethod = maxSwitchArms,
                    maxMethodLineSpan = maxMethodLineSpan,
                    maxMethodParameters = maxMethodParameters,
                    maxMethodDecisionPoints = maxMethodDecisionPoints,
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
    val maxMethodLineSpan: Int,
    val maxMethodParameters: Int,
    val maxMethodDecisionPoints: Int,
)

private data class NestedTypeShapeMetrics(
    val nestedTypeCount: Int,
    val maxSwitchArms: Int,
    val maxMethodLineSpan: Int,
    val maxMethodParameters: Int,
    val maxMethodDecisionPoints: Int,
)

private val constantFieldModifiers = setOf(Modifier.STATIC, Modifier.FINAL)

private object TopLevelTypeShapeScanner {
    fun measure(
        topLevelType: ClassTree,
        unit: CompilationUnitTree,
        sourcePositions: SourcePositions,
    ): TopLevelTypeShapeMetrics {
        var methodCount = 0
        var fieldCount = 0
        var nestedTypeCount = 0
        var maxSwitchArms = 0
        var maxMethodLineSpan = 0
        var maxMethodParameters = 0
        var maxMethodDecisionPoints = 0
        val countFields =
            topLevelType.kind != Tree.Kind.ENUM && topLevelType.kind != Tree.Kind.RECORD
        val countMethodParameters = topLevelType.kind != Tree.Kind.RECORD
        topLevelType.members.forEach { member ->
            when (member) {
                is MethodTree -> {
                    methodCount += 1
                    val methodMetrics = MethodShapeMetrics.measure(member, unit, sourcePositions)
                    maxSwitchArms = maxOf(maxSwitchArms, methodMetrics.maxSwitchArms)
                    maxMethodLineSpan = maxOf(maxMethodLineSpan, methodMetrics.lineSpan)
                    if (countMethodParameters) {
                        maxMethodParameters =
                            maxOf(maxMethodParameters, methodMetrics.parameterCount)
                    }
                    maxMethodDecisionPoints =
                        maxOf(maxMethodDecisionPoints, methodMetrics.decisionPoints)
                }
                is VariableTree ->
                    if (countFields && !member.modifiers.flags.containsAll(constantFieldModifiers)) {
                        fieldCount += 1
                    }
                is ClassTree -> {
                    nestedTypeCount += 1
                    val nestedMetrics = NestedTypeShapeScanner.measure(member, unit, sourcePositions)
                    nestedTypeCount += nestedMetrics.nestedTypeCount
                    maxSwitchArms = maxOf(maxSwitchArms, nestedMetrics.maxSwitchArms)
                    maxMethodLineSpan =
                        maxOf(maxMethodLineSpan, nestedMetrics.maxMethodLineSpan)
                    maxMethodParameters =
                        maxOf(maxMethodParameters, nestedMetrics.maxMethodParameters)
                    maxMethodDecisionPoints =
                        maxOf(maxMethodDecisionPoints, nestedMetrics.maxMethodDecisionPoints)
                }
                is BlockTree -> {
                    maxSwitchArms = maxOf(maxSwitchArms, MaxSwitchArmScanner.measure(member))
                }
                else -> Unit
            }
        }
        return TopLevelTypeShapeMetrics(
            methodCount = methodCount,
            fieldCount = fieldCount,
            nestedTypeCount = nestedTypeCount,
            maxSwitchArms = maxSwitchArms,
            maxMethodLineSpan = maxMethodLineSpan,
            maxMethodParameters = maxMethodParameters,
            maxMethodDecisionPoints = maxMethodDecisionPoints,
        )
    }
}

private object NestedTypeShapeScanner {
    fun measure(
        typeTree: ClassTree,
        unit: CompilationUnitTree,
        sourcePositions: SourcePositions,
    ): NestedTypeShapeMetrics {
        var nestedTypeCount = 0
        var maxSwitchArms = 0
        var maxMethodLineSpan = 0
        var maxMethodParameters = 0
        var maxMethodDecisionPoints = 0
        val countMethodParameters = typeTree.kind != Tree.Kind.RECORD
        typeTree.members.forEach { member ->
            when (member) {
                is MethodTree -> {
                    val methodMetrics = MethodShapeMetrics.measure(member, unit, sourcePositions)
                    maxSwitchArms = maxOf(maxSwitchArms, methodMetrics.maxSwitchArms)
                    maxMethodLineSpan = maxOf(maxMethodLineSpan, methodMetrics.lineSpan)
                    if (countMethodParameters) {
                        maxMethodParameters =
                            maxOf(maxMethodParameters, methodMetrics.parameterCount)
                    }
                    maxMethodDecisionPoints =
                        maxOf(maxMethodDecisionPoints, methodMetrics.decisionPoints)
                }
                is ClassTree -> {
                    nestedTypeCount += 1
                    val nestedMetrics = measure(member, unit, sourcePositions)
                    nestedTypeCount += nestedMetrics.nestedTypeCount
                    maxSwitchArms = maxOf(maxSwitchArms, nestedMetrics.maxSwitchArms)
                    maxMethodLineSpan =
                        maxOf(maxMethodLineSpan, nestedMetrics.maxMethodLineSpan)
                    maxMethodParameters =
                        maxOf(maxMethodParameters, nestedMetrics.maxMethodParameters)
                    maxMethodDecisionPoints =
                        maxOf(maxMethodDecisionPoints, nestedMetrics.maxMethodDecisionPoints)
                }
                is BlockTree -> {
                    maxSwitchArms = maxOf(maxSwitchArms, MaxSwitchArmScanner.measure(member))
                }
                else -> Unit
            }
        }
        return NestedTypeShapeMetrics(
            nestedTypeCount = nestedTypeCount,
            maxSwitchArms = maxSwitchArms,
            maxMethodLineSpan = maxMethodLineSpan,
            maxMethodParameters = maxMethodParameters,
            maxMethodDecisionPoints = maxMethodDecisionPoints,
        )
    }
}
