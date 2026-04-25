package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

object DistributionContractReader {
    private val objectMapper = JsonMapper.builder().build()
    private const val CONTRACT_SCHEMA_KEYS_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/contract-schema-keys.json"
    private const val PUBLIC_DISTRIBUTION_CONTRACT_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/public-distribution-contract.json"
    private const val MANAGED_SQLITE_CONTRACT_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/managed-sqlite-contract.json"
    private const val BUNDLE_LAYOUT_CONTRACT_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/bundle-layout-contract.json"
    private const val OPERATION_ID_CONTRACT_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/operation-id-contract.json"
    private const val RUNTIME_SURFACE_CONTRACT_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/runtime-surface-contract.json"

    fun publicCliBundleTargets(projectRootDirectory: Path): List<String> =
        publicDistributionContract(projectRootDirectory).supportedPublicCliBundleTargets

    fun unsupportedPublicCliBundleTargets(projectRootDirectory: Path): List<String> =
        publicDistributionContract(projectRootDirectory).unsupportedPublicCliBundleTargets

    fun requiredMinimumSqliteVersion(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            MANAGED_SQLITE_CONTRACT_PATH,
            contractSchema(projectRootDirectory).managedSqlite.requiredMinimumSqliteVersion,
        )

    fun requiredSqlite3mcVersion(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            MANAGED_SQLITE_CONTRACT_PATH,
            contractSchema(projectRootDirectory).managedSqlite.requiredSqlite3mcVersion,
        )

    fun sourceCheckoutRuntimeDistribution(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            RUNTIME_SURFACE_CONTRACT_PATH,
            contractSchema(projectRootDirectory).runtimeSurface.sourceCheckoutRuntimeDistribution,
        )

    fun containerRuntimeDistribution(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            RUNTIME_SURFACE_CONTRACT_PATH,
            contractSchema(projectRootDirectory).runtimeSurface.containerRuntimeDistribution,
        )

    fun bundleRuntimeDistribution(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            RUNTIME_SURFACE_CONTRACT_PATH,
            contractSchema(projectRootDirectory).runtimeSurface.bundleRuntimeDistribution,
        )

    fun publicCliDistribution(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            RUNTIME_SURFACE_CONTRACT_PATH,
            contractSchema(projectRootDirectory).runtimeSurface.publicCliDistribution,
        )

    fun storageDriver(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            RUNTIME_SURFACE_CONTRACT_PATH,
            contractSchema(projectRootDirectory).runtimeSurface.storageDriver,
        )

    fun storageEngine(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            RUNTIME_SURFACE_CONTRACT_PATH,
            contractSchema(projectRootDirectory).runtimeSurface.storageEngine,
        )

    fun bookProtectionMode(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            RUNTIME_SURFACE_CONTRACT_PATH,
            contractSchema(projectRootDirectory).runtimeSurface.bookProtectionMode,
        )

    fun defaultBookCipher(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            RUNTIME_SURFACE_CONTRACT_PATH,
            contractSchema(projectRootDirectory).runtimeSurface.defaultBookCipher,
        )

    fun sqliteLibraryMode(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            RUNTIME_SURFACE_CONTRACT_PATH,
            contractSchema(projectRootDirectory).runtimeSurface.sqliteLibraryMode,
        )

    fun sqliteBundleHomeSystemProperty(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            RUNTIME_SURFACE_CONTRACT_PATH,
            contractSchema(projectRootDirectory).runtimeSurface.sqliteBundleHomeSystemProperty,
        )

    fun helpOperationName(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            OPERATION_ID_CONTRACT_PATH,
            contractSchema(projectRootDirectory).operationIds.help,
        )

    fun capabilitiesOperationName(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            OPERATION_ID_CONTRACT_PATH,
            contractSchema(projectRootDirectory).operationIds.capabilities,
        )

    fun requestTemplateOperationName(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            OPERATION_ID_CONTRACT_PATH,
            contractSchema(projectRootDirectory).operationIds.printRequestTemplate,
        )

    fun planTemplateOperationName(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            OPERATION_ID_CONTRACT_PATH,
            contractSchema(projectRootDirectory).operationIds.printPlanTemplate,
        )

    fun operatingSystemId(osName: String = System.getProperty("os.name", "")): String {
        val operatingSystem = osName.lowercase()
        if (operatingSystem.contains("mac")) {
            return "macos"
        }
        if (operatingSystem.contains("linux")) {
            return "linux"
        }
        if (operatingSystem.contains("windows")) {
            return "windows"
        }
        throw IllegalStateException("FinGrind bundles currently support macOS, Linux, and Windows only.")
    }

    fun architectureId(architecture: String = System.getProperty("os.arch", "unknown")): String =
        when (architecture.lowercase()) {
            "arm64", "aarch64" -> "aarch64"
            "amd64", "x86_64", "x64" -> "x86_64"
            else -> architecture.lowercase().replace(Regex("[^a-z0-9]+"), "-")
        }

    fun hostBundleTarget(
        projectRootDirectory: Path,
        osName: String = System.getProperty("os.name", ""),
        architecture: String = System.getProperty("os.arch", "unknown"),
    ): BundleTargetContract =
        bundleTarget(
            projectRootDirectory,
            operatingSystemId(osName) + "-" + architectureId(architecture),
        )

    fun hostClassifier(
        projectRootDirectory: Path,
        osName: String = System.getProperty("os.name", ""),
        architecture: String = System.getProperty("os.arch", "unknown"),
    ): String = hostBundleTarget(projectRootDirectory, osName, architecture).classifier

    fun bundleTarget(projectRootDirectory: Path, classifier: String): BundleTargetContract {
        val normalizedClassifier = classifier.trim()
        if (normalizedClassifier.isEmpty()) {
            throw IllegalStateException("Bundle target classifier must not be blank.")
        }
        return bundleLayoutContract(projectRootDirectory).bundleTargets[normalizedClassifier]
            ?: throw IllegalStateException(
                "Bundle target $normalizedClassifier is not declared in $BUNDLE_LAYOUT_CONTRACT_PATH.",
            )
    }

    fun jsonString(value: String): String =
        buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
            append('"')
        }

    fun jsonStringArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { value -> jsonString(value) }

    fun markdownBulletList(values: List<String>): String =
        if (values.isEmpty()) {
            "- none"
        } else {
            values.joinToString(separator = System.lineSeparator()) { value -> "- `$value`" }
        }

    private fun publicDistributionContract(projectRootDirectory: Path): PublicDistributionContract {
        val schema = contractSchema(projectRootDirectory).publicDistribution
        val declaredBundleTargets = bundleLayoutContract(projectRootDirectory).bundleTargets.keys
        val supportedPublicCliBundleTargets =
            validatedBundleTargetList(
                listProperty(
                    projectRootDirectory,
                    PUBLIC_DISTRIBUTION_CONTRACT_PATH,
                    schema.supportedPublicCliBundleTargets,
                ),
                declaredBundleTargets,
                schema.supportedPublicCliBundleTargets,
            )
        val unsupportedPublicCliBundleTargets =
            validatedBundleTargetList(
                listProperty(
                    projectRootDirectory,
                    PUBLIC_DISTRIBUTION_CONTRACT_PATH,
                    schema.unsupportedPublicCliBundleTargets,
                ),
                declaredBundleTargets,
                schema.unsupportedPublicCliBundleTargets,
            )
        val overlap =
            supportedPublicCliBundleTargets.toSet().intersect(unsupportedPublicCliBundleTargets.toSet())
        if (overlap.isNotEmpty()) {
            throw IllegalStateException(
                "${schema.supportedPublicCliBundleTargets} and ${schema.unsupportedPublicCliBundleTargets} must be disjoint: ${overlap.joinToString()}",
            )
        }
        val declaredTargets =
            (supportedPublicCliBundleTargets + unsupportedPublicCliBundleTargets).toSet()
        if (declaredTargets != declaredBundleTargets) {
            val missingTargets = declaredBundleTargets - declaredTargets
            throw IllegalStateException(
                "Public distribution contract must classify every declared bundle target. Missing: ${missingTargets.joinToString()}",
            )
        }
        return PublicDistributionContract(
            supportedPublicCliBundleTargets,
            unsupportedPublicCliBundleTargets,
        )
    }

    private fun bundleLayoutContract(projectRootDirectory: Path): BundleLayoutContract {
        val schema = contractSchema(projectRootDirectory).bundleLayout
        val document = loadJson(projectRootDirectory, BUNDLE_LAYOUT_CONTRACT_PATH)
        val bundleTargetsNode = objectProperty(document, schema.bundleTargets, BUNDLE_LAYOUT_CONTRACT_PATH)
        val bundleTargets = linkedMapOf<String, BundleTargetContract>()
        bundleTargetsNode.properties().forEach { entry ->
            val classifier = entry.key.trim()
            if (classifier.isEmpty()) {
                throw IllegalStateException(
                    "Bundle layout target names must be non-blank in $BUNDLE_LAYOUT_CONTRACT_PATH.",
                )
            }
            if (bundleTargets.containsKey(classifier)) {
                throw IllegalStateException("Duplicate bundle layout target: $classifier")
            }
            bundleTargets[classifier] =
                bundleTargetContract(classifier, entry.value, schema)
        }
        if (bundleTargets.isEmpty()) {
            throw IllegalStateException(
                "Bundle layout contract must declare at least one bundle target in $BUNDLE_LAYOUT_CONTRACT_PATH.",
            )
        }
        return BundleLayoutContract(bundleTargets.toMap())
    }

    private fun bundleTargetContract(
        classifier: String,
        node: JsonNode,
        schema: BundleLayoutSchema,
    ): BundleTargetContract {
        val document = requireObjectNode(node, "$classifier entry", BUNDLE_LAYOUT_CONTRACT_PATH)
        val operatingSystemId =
            requiredText(document, schema.operatingSystemId, BUNDLE_LAYOUT_CONTRACT_PATH)
        val architectureId =
            requiredText(document, schema.architectureId, BUNDLE_LAYOUT_CONTRACT_PATH)
        val recomposedClassifier = operatingSystemId + "-" + architectureId
        if (classifier != recomposedClassifier) {
            throw IllegalStateException(
                "Bundle layout target $classifier must agree with $recomposedClassifier in $BUNDLE_LAYOUT_CONTRACT_PATH.",
            )
        }
        return BundleTargetContract(
            classifier = classifier,
            operatingSystemId = operatingSystemId,
            architectureId = architectureId,
            archiveFormat = requiredText(document, schema.archiveFormat, BUNDLE_LAYOUT_CONTRACT_PATH),
            launcherPath = requiredText(document, schema.launcherPath, BUNDLE_LAYOUT_CONTRACT_PATH),
            launcherCommand = requiredText(document, schema.launcherCommand, BUNDLE_LAYOUT_CONTRACT_PATH),
            sqliteLibraryFileName =
                requiredText(document, schema.sqliteLibraryFileName, BUNDLE_LAYOUT_CONTRACT_PATH),
        )
    }

    private fun contractSchema(projectRootDirectory: Path): ContractSchema =
        loadContractSchema(projectRootDirectory)

    private fun loadJson(projectRootDirectory: Path, relativePath: String): JsonNode {
        Files.newInputStream(contractPath(projectRootDirectory, relativePath)).use { stream ->
            val document = objectMapper.readTree(stream)
            if (document == null || !document.isObject) {
                throw IllegalStateException("Contract resource $relativePath must contain one top-level JSON object.")
            }
            return document
        }
    }

    private fun contractPath(projectRootDirectory: Path, relativePath: String): Path =
        sequenceOf(
                projectRootDirectory.resolve(relativePath),
                projectRootDirectory.resolve("..").resolve(relativePath),
            )
            .map(Path::normalize)
            .firstOrNull(Files::isRegularFile)
            ?: throw IllegalStateException(
                "Missing contract resource $relativePath for $projectRootDirectory.",
            )

    private fun requiredProperty(
        projectRootDirectory: Path,
        relativePath: String,
        key: String,
    ): String {
        val valueNode = loadJson(projectRootDirectory, relativePath).path(key)
        val value = if (valueNode.isString) valueNode.stringValue()?.trim().orEmpty() else ""
        if (value.isEmpty()) {
            throw IllegalStateException("Missing required contract property $key in $relativePath.")
        }
        return value
    }

    private fun requiredText(document: JsonNode, key: String, relativePath: String): String {
        val valueNode = document.path(key)
        val value = if (valueNode.isString) valueNode.stringValue()?.trim().orEmpty() else ""
        if (value.isEmpty()) {
            throw IllegalStateException("Missing required contract property $key in $relativePath.")
        }
        return value
    }

    private fun listProperty(
        projectRootDirectory: Path,
        relativePath: String,
        key: String,
    ): List<String> {
        val valuesNode = loadJson(projectRootDirectory, relativePath).path(key)
        if (valuesNode.isMissingNode || valuesNode.isNull) {
            return emptyList()
        }
        if (!valuesNode.isArray) {
            throw IllegalStateException("Expected JSON array contract property $key in $relativePath.")
        }
        val values = linkedSetOf<String>()
        valuesNode.forEach { node ->
            val value = if (node.isString) node.stringValue()?.trim().orEmpty() else ""
            if (value.isNotEmpty()) {
                if (!values.add(value)) {
                    throw IllegalStateException(
                        "Duplicate contract list element $value in $key from $relativePath.",
                    )
                }
            } else {
                throw IllegalStateException("Expected JSON string elements in contract property $key in $relativePath.")
            }
        }
        return values.toList()
    }

    private fun validatedBundleTargetList(
        values: List<String>,
        declaredBundleTargets: Set<String>,
        key: String,
    ): List<String> {
        values.forEach { value ->
            if (value !in declaredBundleTargets) {
                throw IllegalStateException(
                    "Contract property $key references undeclared bundle target $value.",
                )
            }
        }
        return values
    }

    private fun objectProperty(document: JsonNode, key: String, relativePath: String): JsonNode =
        requireObjectNode(document.path(key), key, relativePath)

    private fun requireObjectNode(node: JsonNode, key: String, relativePath: String): JsonNode {
        if (!node.isObject) {
            throw IllegalStateException(
                "Contract property $key must be one JSON object in $relativePath.",
            )
        }
        return node
    }

    internal fun loadContractSchema(projectRootDirectory: Path): ContractSchema {
        val document = loadJson(projectRootDirectory, CONTRACT_SCHEMA_KEYS_PATH)
        return ContractSchema(
            runtimeSurface = RuntimeSurfaceSchema(
                directJavaRuntimeDistribution = schemaKey(document, "runtimeSurface", "directJavaRuntimeDistribution"),
                sourceCheckoutRuntimeDistribution =
                    schemaKey(document, "runtimeSurface", "sourceCheckoutRuntimeDistribution"),
                containerRuntimeDistribution =
                    schemaKey(document, "runtimeSurface", "containerRuntimeDistribution"),
                bundleRuntimeDistribution = schemaKey(document, "runtimeSurface", "bundleRuntimeDistribution"),
                publicCliDistribution = schemaKey(document, "runtimeSurface", "publicCliDistribution"),
                storageDriver = schemaKey(document, "runtimeSurface", "storageDriver"),
                storageEngine = schemaKey(document, "runtimeSurface", "storageEngine"),
                bookProtectionMode = schemaKey(document, "runtimeSurface", "bookProtectionMode"),
                defaultBookCipher = schemaKey(document, "runtimeSurface", "defaultBookCipher"),
                sqliteLibraryMode = schemaKey(document, "runtimeSurface", "sqliteLibraryMode"),
                sqliteLibraryEnvironmentVariable =
                    schemaKey(document, "runtimeSurface", "sqliteLibraryEnvironmentVariable"),
                sqliteBundleHomeSystemProperty =
                    schemaKey(document, "runtimeSurface", "sqliteBundleHomeSystemProperty"),
            ),
            publicDistribution = PublicDistributionSchema(
                supportedPublicCliBundleTargets =
                    schemaKey(document, "publicDistribution", "supportedPublicCliBundleTargets"),
                unsupportedPublicCliBundleTargets =
                    schemaKey(document, "publicDistribution", "unsupportedPublicCliBundleTargets"),
            ),
            managedSqlite = ManagedSqliteSchema(
                requiredMinimumSqliteVersion =
                    schemaKey(document, "managedSqlite", "requiredMinimumSqliteVersion"),
                requiredSqlite3mcVersion =
                    schemaKey(document, "managedSqlite", "requiredSqlite3mcVersion"),
            ),
            bundleLayout = BundleLayoutSchema(
                bundleTargets = schemaKey(document, "bundleLayout", "bundleTargets"),
                operatingSystemId = schemaKey(document, "bundleLayout", "operatingSystemId"),
                architectureId = schemaKey(document, "bundleLayout", "architectureId"),
                archiveFormat = schemaKey(document, "bundleLayout", "archiveFormat"),
                launcherPath = schemaKey(document, "bundleLayout", "launcherPath"),
                launcherCommand = schemaKey(document, "bundleLayout", "launcherCommand"),
                sqliteLibraryFileName =
                    schemaKey(document, "bundleLayout", "sqliteLibraryFileName"),
            ),
            operationIds = OperationIdSchema(
                help = schemaKey(document, "operationIdContract", "help"),
                capabilities = schemaKey(document, "operationIdContract", "capabilities"),
                printRequestTemplate = schemaKey(document, "operationIdContract", "printRequestTemplate"),
                printPlanTemplate = schemaKey(document, "operationIdContract", "printPlanTemplate"),
            ),
        )
    }

    private fun schemaKey(document: JsonNode, objectKey: String, fieldKey: String): String {
        val objectNode = document.path(objectKey)
        if (!objectNode.isObject) {
            throw IllegalStateException("Contract schema key object $objectKey must exist in $CONTRACT_SCHEMA_KEYS_PATH.")
        }
        val keyNode = objectNode.path(fieldKey)
        val key = if (keyNode.isString) keyNode.stringValue()?.trim().orEmpty() else ""
        if (key.isEmpty()) {
            throw IllegalStateException(
                "Contract schema key $objectKey.$fieldKey must be one non-blank JSON string in $CONTRACT_SCHEMA_KEYS_PATH.",
            )
        }
        return key
    }

    internal data class ContractSchema(
        val runtimeSurface: RuntimeSurfaceSchema,
        val publicDistribution: PublicDistributionSchema,
        val managedSqlite: ManagedSqliteSchema,
        val bundleLayout: BundleLayoutSchema,
        val operationIds: OperationIdSchema,
    )

    internal data class RuntimeSurfaceSchema(
        val directJavaRuntimeDistribution: String,
        val sourceCheckoutRuntimeDistribution: String,
        val containerRuntimeDistribution: String,
        val bundleRuntimeDistribution: String,
        val publicCliDistribution: String,
        val storageDriver: String,
        val storageEngine: String,
        val bookProtectionMode: String,
        val defaultBookCipher: String,
        val sqliteLibraryMode: String,
        val sqliteLibraryEnvironmentVariable: String,
        val sqliteBundleHomeSystemProperty: String,
    )

    internal data class PublicDistributionSchema(
        val supportedPublicCliBundleTargets: String,
        val unsupportedPublicCliBundleTargets: String,
    )

    internal data class ManagedSqliteSchema(
        val requiredMinimumSqliteVersion: String,
        val requiredSqlite3mcVersion: String,
    )

    internal data class BundleLayoutSchema(
        val bundleTargets: String,
        val operatingSystemId: String,
        val architectureId: String,
        val archiveFormat: String,
        val launcherPath: String,
        val launcherCommand: String,
        val sqliteLibraryFileName: String,
    )

    internal data class OperationIdSchema(
        val help: String,
        val capabilities: String,
        val printRequestTemplate: String,
        val printPlanTemplate: String,
    )

    internal data class PublicDistributionContract(
        val supportedPublicCliBundleTargets: List<String>,
        val unsupportedPublicCliBundleTargets: List<String>,
    )

    internal data class BundleLayoutContract(val bundleTargets: Map<String, BundleTargetContract>)

    data class BundleTargetContract(
        val classifier: String,
        val operatingSystemId: String,
        val architectureId: String,
        val archiveFormat: String,
        val launcherPath: String,
        val launcherCommand: String,
        val sqliteLibraryFileName: String,
    )
}
