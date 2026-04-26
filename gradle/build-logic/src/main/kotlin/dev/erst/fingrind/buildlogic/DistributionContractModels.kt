package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import tools.jackson.databind.JsonNode

internal object DistributionContractModels {
    fun publicDistributionContract(projectRootDirectory: Path): DistributionContractReader.PublicDistributionContract {
        val schema = DistributionContractReader.loadContractSchema(projectRootDirectory).publicDistribution
        val declaredBundleTargets = bundleLayoutContract(projectRootDirectory).bundleTargets.keys
        val supportedPublicCliBundleTargets =
            validatedBundleTargetList(
                DistributionContractJson.listProperty(
                    projectRootDirectory,
                    DistributionContractPaths.PUBLIC_DISTRIBUTION_CONTRACT_PATH,
                    schema.supportedPublicCliBundleTargets,
                ),
                declaredBundleTargets,
                schema.supportedPublicCliBundleTargets,
            )
        val unsupportedPublicCliBundleTargets =
            validatedBundleTargetList(
                DistributionContractJson.listProperty(
                    projectRootDirectory,
                    DistributionContractPaths.PUBLIC_DISTRIBUTION_CONTRACT_PATH,
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
        return DistributionContractReader.PublicDistributionContract(
            supportedPublicCliBundleTargets,
            unsupportedPublicCliBundleTargets,
        )
    }

    fun bundleLayoutContract(projectRootDirectory: Path): DistributionContractReader.BundleLayoutContract {
        val schema = DistributionContractReader.loadContractSchema(projectRootDirectory).bundleLayout
        val document = DistributionContractJson.loadJson(projectRootDirectory, DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH)
        val bundleTargetsNode =
            DistributionContractJson.objectProperty(
                document,
                schema.bundleTargets,
                DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH,
            )
        val bundleTargets = linkedMapOf<String, DistributionContractReader.BundleTargetContract>()
        bundleTargetsNode.properties().forEach { entry ->
            val classifier = entry.key.trim()
            if (classifier.isEmpty()) {
                throw IllegalStateException(
                    "Bundle layout target names must be non-blank in ${DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH}.",
                )
            }
            if (bundleTargets.containsKey(classifier)) {
                throw IllegalStateException("Duplicate bundle layout target: $classifier")
            }
            bundleTargets[classifier] = bundleTargetContract(classifier, entry.value, schema)
        }
        if (bundleTargets.isEmpty()) {
            throw IllegalStateException(
                "Bundle layout contract must declare at least one bundle target in ${DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH}.",
            )
        }
        return DistributionContractReader.BundleLayoutContract(bundleTargets.toMap())
    }

    private fun bundleTargetContract(
        classifier: String,
        node: JsonNode,
        schema: DistributionContractReader.BundleLayoutSchema,
    ): DistributionContractReader.BundleTargetContract {
        val document =
            DistributionContractJson.requireObjectNode(
                node,
                "$classifier entry",
                DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH,
            )
        val operatingSystemId =
            DistributionContractJson.requiredText(
                document,
                schema.operatingSystemId,
                DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH,
            )
        val architectureId =
            DistributionContractJson.requiredText(
                document,
                schema.architectureId,
                DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH,
            )
        val recomposedClassifier = operatingSystemId + "-" + architectureId
        if (classifier != recomposedClassifier) {
            throw IllegalStateException(
                "Bundle layout target $classifier must agree with $recomposedClassifier in ${DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH}.",
            )
        }
        return DistributionContractReader.BundleTargetContract(
            classifier = classifier,
            operatingSystemId = operatingSystemId,
            architectureId = architectureId,
            archiveFormat =
                DistributionContractJson.requiredText(
                    document,
                    schema.archiveFormat,
                    DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH,
                ),
            launcherPath =
                DistributionContractJson.requiredText(
                    document,
                    schema.launcherPath,
                    DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH,
                ),
            launcherCommand =
                DistributionContractJson.requiredText(
                    document,
                    schema.launcherCommand,
                    DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH,
                ),
            sqliteLibraryFileName =
                DistributionContractJson.requiredText(
                    document,
                    schema.sqliteLibraryFileName,
                    DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH,
                ),
        )
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
}
