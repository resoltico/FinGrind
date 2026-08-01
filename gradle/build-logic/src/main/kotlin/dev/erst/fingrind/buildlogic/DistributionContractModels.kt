package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import tools.jackson.databind.JsonNode

internal object DistributionContractModels {
    fun bundleLayoutContract(projectRootDirectory: Path): BundleLayoutContract {
        val schema = DistributionContractReader.loadContractSchema(projectRootDirectory).bundleLayout
        val publicationSchema =
            DistributionContractReader.loadContractSchema(projectRootDirectory).bundlePublication
        val document = DistributionContractJson.loadJson(projectRootDirectory, DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH)
        val publicationDocument =
            DistributionContractJson.loadJson(
                projectRootDirectory,
                DistributionContractPaths.BUNDLE_PUBLICATION_CONTRACT_PATH,
            )
        val bundleTargetsNode =
            DistributionContractJson.objectProperty(
                document,
                schema.bundleTargets,
                DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH,
            )
        val publicationTargetsNode =
            DistributionContractJson.objectProperty(
                publicationDocument,
                publicationSchema.bundleTargets,
                DistributionContractPaths.BUNDLE_PUBLICATION_CONTRACT_PATH,
        )
        val bundleTargets = linkedMapOf<String, BundleTargetContract>()
        bundleTargetsNode.properties().forEach { entry ->
            val classifier = entry.key
            if (classifier.isBlank()) {
                throw IllegalStateException(
                    "Bundle layout target names must be non-blank in ${DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH}.",
                )
            }
            if (bundleTargets.containsKey(classifier)) {
                throw IllegalStateException("Duplicate bundle layout target: $classifier")
            }
            val publicationNode = publicationTargetsNode.path(classifier)
            if (!publicationNode.isObject) {
                throw IllegalStateException(
                    "Bundle publication contract must declare one publication object for $classifier in ${DistributionContractPaths.BUNDLE_PUBLICATION_CONTRACT_PATH}.",
                )
            }
            bundleTargets[classifier] =
                bundleTargetContract(
                    classifier = classifier,
                    node = entry.value,
                    publicationNode = publicationNode,
                    schema = schema,
                    publicationSchema = publicationSchema,
                )
        }
        publicationTargetsNode.properties().forEach { entry ->
            if (!bundleTargets.containsKey(entry.key)) {
                throw IllegalStateException(
                    "Bundle publication contract declared unknown target ${entry.key} in ${DistributionContractPaths.BUNDLE_PUBLICATION_CONTRACT_PATH}.",
                )
            }
        }
        if (bundleTargets.isEmpty()) {
            throw IllegalStateException(
                "Bundle layout contract must declare at least one bundle target in ${DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH}.",
            )
        }
        return BundleLayoutContract(bundleTargets.toMap())
    }

    private fun bundleTargetContract(
        classifier: String,
        node: JsonNode,
        publicationNode: JsonNode,
        schema: BundleLayoutSchema,
        publicationSchema: BundlePublicationSchema,
    ): BundleTargetContract {
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
        return BundleTargetContract(
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
            compatibilityLabel =
                DistributionContractJson.requiredText(
                    document,
                    schema.compatibilityLabel,
                    DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH,
                ),
            publicBundlePublication =
                publicBundlePublicationContract(
                    classifier = classifier,
                    node = publicationNode,
                    schema = publicationSchema,
                ),
            minimumGlibcVersion =
                document.path(schema.minimumGlibcVersion).takeIf { !it.isMissingNode && !it.isNull }
                    ?.stringValue()
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    .also { value ->
                        if (operatingSystemId == "linux" && value == null) {
                            throw IllegalStateException(
                                "Bundle layout target $classifier must declare ${schema.minimumGlibcVersion} in ${DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH}.",
                            )
                        }
                        if (operatingSystemId != "linux" && value != null) {
                            throw IllegalStateException(
                                "Bundle layout target $classifier must omit ${schema.minimumGlibcVersion} outside Linux in ${DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH}.",
                            )
                        }
                    },
            compatibilitySmokeContainerImage =
                document
                    .path(schema.compatibilitySmokeContainerImage)
                    .takeIf { !it.isMissingNode && !it.isNull }
                    ?.stringValue()
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    .also { value ->
                        if (operatingSystemId == "linux" && value == null) {
                            throw IllegalStateException(
                                "Bundle layout target $classifier must declare ${schema.compatibilitySmokeContainerImage} in ${DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH}.",
                            )
                        }
                        if (operatingSystemId != "linux" && value != null) {
                            throw IllegalStateException(
                                "Bundle layout target $classifier must omit ${schema.compatibilitySmokeContainerImage} outside Linux in ${DistributionContractPaths.BUNDLE_LAYOUT_CONTRACT_PATH}.",
                            )
                        }
                    },
        )
    }

    private fun publicBundlePublicationContract(
        classifier: String,
        node: JsonNode,
        schema: BundlePublicationSchema,
    ): PublicBundlePublicationContract {
        val publicationNode =
            DistributionContractJson.requireObjectNode(
                node,
                "bundle publication target $classifier",
                DistributionContractPaths.BUNDLE_PUBLICATION_CONTRACT_PATH,
            )
        val status =
            DistributionContractJson.requiredText(
                publicationNode,
                schema.publicationStatus,
                DistributionContractPaths.BUNDLE_PUBLICATION_CONTRACT_PATH,
            )
        if (
            status != PUBLICATION_STATUS_PUBLISHED &&
                status != PUBLICATION_STATUS_NOT_PUBLISHED
        ) {
            throw IllegalStateException(
                "Bundle publication target $classifier declared unsupported publication status $status in ${DistributionContractPaths.BUNDLE_PUBLICATION_CONTRACT_PATH}.",
            )
        }
        DistributionContractJson.requireOnlyProperties(
            publicationNode,
            setOf(schema.publicationStatus),
            "bundle publication target $classifier",
            DistributionContractPaths.BUNDLE_PUBLICATION_CONTRACT_PATH,
        )
        return PublicBundlePublicationContract(
            status = status,
        )
    }
}
