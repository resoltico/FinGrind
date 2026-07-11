package dev.erst.fingrind.buildlogic

import groovy.json.JsonSlurper
import org.gradle.api.Project

internal data class JazzerHarnessSpec(
    val key: String,
    val displayName: String,
    val className: String,
    val methodName: String,
) {
    fun inputResourceDirectory(): String {
        val packagePath = className.substringBeforeLast('.').replace('.', '/')
        val simpleName = className.substringAfterLast('.')
        return "$packagePath/${simpleName}Inputs/$methodName"
    }
}

internal data class JazzerRunTargetSpec(
    val key: String,
    val displayName: String,
    val taskName: String,
    val workingDirectory: String,
    val activeFuzzing: Boolean,
    val harnessKeys: List<String>,
)

internal data class JazzerTopology(
    val harnesses: List<JazzerHarnessSpec>,
    val runTargets: List<JazzerRunTargetSpec>,
) {
    private val harnessesByKey: Map<String, JazzerHarnessSpec> = harnesses.associateBy(JazzerHarnessSpec::key)
    private val runTargetsByKey: Map<String, JazzerRunTargetSpec> = runTargets.associateBy(JazzerRunTargetSpec::key)

    fun harness(key: String): JazzerHarnessSpec =
        harnessesByKey[key] ?: throw IllegalArgumentException("Unknown Jazzer harness key: $key")

    fun runTarget(key: String): JazzerRunTargetSpec =
        runTargetsByKey[key] ?: throw IllegalArgumentException("Unknown Jazzer run target key: $key")

    companion object {
        private const val HARNESSES_PATH = "src/main/resources/dev/erst/fingrind/jazzer/support/jazzer-harnesses.json"
        private const val RUN_TARGETS_PATH = "src/main/resources/dev/erst/fingrind/jazzer/support/jazzer-run-targets.json"

        fun load(project: Project): JazzerTopology {
            val harnessesFile = project.layout.projectDirectory.file(HARNESSES_PATH).asFile
            val runTargetsFile = project.layout.projectDirectory.file(RUN_TARGETS_PATH).asFile
            val harnessesRoot = JsonSlurper().parse(harnessesFile) as? List<*>
                ?: throw IllegalArgumentException("Jazzer harness catalog must be a JSON array: $HARNESSES_PATH")
            val runTargetsRoot = JsonSlurper().parse(runTargetsFile) as? List<*>
                ?: throw IllegalArgumentException("Jazzer run-target catalog must be a JSON array: $RUN_TARGETS_PATH")
            val harnesses =
                maps(harnessesRoot, "harnesses").map { map ->
                    JazzerHarnessSpec(
                        key = string(map, "key"),
                        displayName = string(map, "displayName"),
                        className = string(map, "className"),
                        methodName = string(map, "methodName"),
                    )
                }
            val runTargets =
                maps(runTargetsRoot, "runTargets").map { map ->
                    JazzerRunTargetSpec(
                        key = string(map, "key"),
                        displayName = string(map, "displayName"),
                        taskName = string(map, "taskName"),
                        workingDirectory = string(map, "workingDirectory"),
                        activeFuzzing = boolean(map, "activeFuzzing"),
                        harnessKeys = strings(map["harnessKeys"], "harnessKeys"),
                    )
                }
            require(harnesses.isNotEmpty()) { "Jazzer topology must declare at least one harness" }
            require(harnesses.map(JazzerHarnessSpec::key).toSet().size == harnesses.size) {
                "Jazzer topology harness keys must be unique"
            }
            require(runTargets.isNotEmpty()) { "Jazzer topology must declare at least one run target" }
            require(runTargets.map(JazzerRunTargetSpec::key).toSet().size == runTargets.size) {
                "Jazzer topology run target keys must be unique"
            }
            val topology = JazzerTopology(harnesses = harnesses, runTargets = runTargets)
            topology.validate()
            return topology
        }

        private fun string(map: Map<*, *>, key: String): String =
            map[key] as? String ?: throw IllegalArgumentException("Missing string field '$key' in Jazzer topology")

        private fun boolean(map: Map<*, *>, key: String): Boolean =
            map[key] as? Boolean ?: throw IllegalArgumentException("Missing boolean field '$key' in Jazzer topology")

        private fun maps(value: List<*>, key: String): List<Map<*, *>> =
            value.map { item ->
                item as? Map<*, *> ?: throw IllegalArgumentException("Field '$key' must contain JSON objects")
            }

        private fun strings(value: Any?, key: String): List<String> =
            (value as? List<*>)?.map { item ->
                item as? String ?: throw IllegalArgumentException("Field '$key' must contain strings")
            } ?: throw IllegalArgumentException("Missing array field '$key' in Jazzer topology")
    }

    private fun validate() {
        runTargets.forEach { target ->
            require(target.harnessKeys.isNotEmpty()) {
                "Jazzer run target '${target.key}' must reference at least one harness"
            }
            target.harnessKeys.forEach { harness(it) }
            if (target.activeFuzzing) {
                require(target.harnessKeys.size == 1) {
                    "Active Jazzer run target '${target.key}' must reference exactly one harness"
                }
                require(target.harnessKeys.single() == target.key) {
                    "Active Jazzer run target '${target.key}' must share its key with its harness"
                }
            }
        }
    }
}
