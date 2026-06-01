package dev.erst.fingrind.buildlogic

import com.sun.source.tree.CaseTree
import com.sun.source.tree.CatchTree
import com.sun.source.tree.CompilationUnitTree
import com.sun.source.tree.ConditionalExpressionTree
import com.sun.source.tree.DoWhileLoopTree
import com.sun.source.tree.EnhancedForLoopTree
import com.sun.source.tree.ForLoopTree
import com.sun.source.tree.IfTree
import com.sun.source.tree.MethodTree
import com.sun.source.tree.SwitchExpressionTree
import com.sun.source.tree.SwitchTree
import com.sun.source.tree.Tree
import com.sun.source.tree.WhileLoopTree
import com.sun.source.util.SourcePositions
import com.sun.source.util.TreeScanner
import javax.tools.Diagnostic

internal data class MethodShapeMetrics(
    val lineSpan: Int,
    val parameterCount: Int,
    val decisionPoints: Int,
    val maxSwitchArms: Int,
) {
    companion object {
        fun measure(
            method: MethodTree,
            unit: CompilationUnitTree,
            sourcePositions: SourcePositions,
        ): MethodShapeMetrics =
            MethodShapeMetrics(
                lineSpan = lineSpan(method, unit, sourcePositions),
                parameterCount = method.parameters.size,
                decisionPoints = MethodDecisionScanner.measure(method),
                maxSwitchArms = MaxSwitchArmScanner.measure(method.body),
            )

        private fun lineSpan(
            tree: Tree,
            unit: CompilationUnitTree,
            sourcePositions: SourcePositions,
        ): Int {
            val startPosition = sourcePositions.getStartPosition(unit, tree)
            val endPosition = sourcePositions.getEndPosition(unit, tree)
            if (startPosition == Diagnostic.NOPOS || endPosition == Diagnostic.NOPOS) {
                return 0
            }
            val lineMap = unit.lineMap ?: return 0
            val startLine = lineMap.getLineNumber(startPosition)
            val endLine = lineMap.getLineNumber(endPosition)
            return (endLine - startLine + 1L).toInt()
        }
    }
}

private object MethodDecisionScanner : TreeScanner<Unit, MutableInt>() {
    fun measure(tree: Tree?): Int {
        if (tree == null) {
            return 0
        }
        val decisionPoints = MutableInt()
        scan(tree, decisionPoints)
        return decisionPoints.value
    }

    override fun visitIf(node: IfTree, accumulator: MutableInt) {
        accumulator.value += 1
        super.visitIf(node, accumulator)
    }

    override fun visitForLoop(node: ForLoopTree, accumulator: MutableInt) {
        accumulator.value += 1
        super.visitForLoop(node, accumulator)
    }

    override fun visitEnhancedForLoop(node: EnhancedForLoopTree, accumulator: MutableInt) {
        accumulator.value += 1
        super.visitEnhancedForLoop(node, accumulator)
    }

    override fun visitWhileLoop(node: WhileLoopTree, accumulator: MutableInt) {
        accumulator.value += 1
        super.visitWhileLoop(node, accumulator)
    }

    override fun visitDoWhileLoop(node: DoWhileLoopTree, accumulator: MutableInt) {
        accumulator.value += 1
        super.visitDoWhileLoop(node, accumulator)
    }

    override fun visitConditionalExpression(
        node: ConditionalExpressionTree,
        accumulator: MutableInt,
    ) {
        accumulator.value += 1
        super.visitConditionalExpression(node, accumulator)
    }

    override fun visitCatch(node: CatchTree, accumulator: MutableInt) {
        accumulator.value += 1
        super.visitCatch(node, accumulator)
    }

    override fun visitSwitch(node: SwitchTree, accumulator: MutableInt) {
        accumulator.value += 1
        super.visitSwitch(node, accumulator)
    }

    override fun visitSwitchExpression(node: SwitchExpressionTree, accumulator: MutableInt) {
        accumulator.value += 1
        super.visitSwitchExpression(node, accumulator)
    }
}

internal object MaxSwitchArmScanner : TreeScanner<Unit, MutableInt>() {
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

internal class MutableInt(var value: Int = 0)
