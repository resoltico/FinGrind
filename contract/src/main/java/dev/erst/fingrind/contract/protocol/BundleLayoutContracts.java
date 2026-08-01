package dev.erst.fingrind.contract.protocol;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Loads and publishes the current bundle-layout contract snapshot. */
final class BundleLayoutContracts {
  private static final ProtocolContractSchemaKeys.BundleLayout SCHEMA_KEYS =
      ProtocolContractSchemaKeys.current().bundleLayout();
  private static final String PUBLICATION_RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/bundle-publication-contract.json";
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/bundle-layout-contract.json";
  private static final String BUNDLE_TARGETS_KEY = SCHEMA_KEYS.bundleTargets();
  private static final BundleLayoutContract CURRENT = loadCurrent();

  private BundleLayoutContracts() {}

  static BundleLayoutContract current() {
    return CURRENT;
  }

  static BundleLayoutContract loadFromResource(
      @Nullable InputStream resourceStream, String resourcePath) {
    return loadFromResources(
        resourceStream,
        resourcePath,
        BundleLayoutContracts.class.getResourceAsStream(PUBLICATION_RESOURCE_PATH),
        PUBLICATION_RESOURCE_PATH);
  }

  static BundleLayoutContract loadFromResources(
      @Nullable InputStream resourceStream,
      String resourcePath,
      @Nullable InputStream publicationStream,
      String publicationResourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath");
    Objects.requireNonNull(publicationResourcePath, "publicationResourcePath");
    ObjectNode document =
        JsonContractResourceSupport.loadObject(
            resourceStream, resourcePath, "bundle layout contract");
    BundlePublicationContract publicationContract =
        BundlePublicationContracts.loadFromResource(publicationStream, publicationResourcePath);
    ObjectNode bundleTargetsNode =
        JsonContractResourceSupport.requireObject(
            document, BUNDLE_TARGETS_KEY, BUNDLE_TARGETS_KEY + " must be one JSON object.");
    List<Map.Entry<PublicCliBundleTarget, BundleLayoutContract.BundleTarget>> bundleTargetEntries =
        new ArrayList<>();
    bundleTargetsNode
        .propertyStream()
        .forEach(
            entry -> {
              String classifier = entry.getKey();
              PublicCliBundleTarget target = PublicCliBundleTarget.fromWireValue(classifier);
              BundleLayoutContract.BundleTarget bundleTarget =
                  loadBundleTarget(
                      target, entry.getValue(), publicationContract, publicationResourcePath);
              String recomposedClassifier =
                  bundleTarget.operatingSystemId() + "-" + bundleTarget.architectureId();
              if (!classifier.equals(recomposedClassifier)) {
                throw new IllegalStateException(
                    "Bundle layout target "
                        + classifier
                        + " must agree with "
                        + recomposedClassifier
                        + ".");
              }
              bundleTargetEntries.add(Map.entry(target, bundleTarget));
            });
    if (bundleTargetEntries.size() != publicationContract.bundleTargets().size()) {
      throw new IllegalStateException(
          "bundle layout and bundle publication contracts must cover the same bundle-target set.");
    }
    return new BundleLayoutContract(
        bundleTargetEntries.stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  private static BundleLayoutContract.BundleTarget loadBundleTarget(
      PublicCliBundleTarget target,
      JsonNode node,
      BundlePublicationContract publicationContract,
      String publicationResourcePath) {
    ObjectNode document =
        JsonContractResourceSupport.requireObjectNode(
            node, BUNDLE_TARGETS_KEY + " entry must be one JSON object.");
    String operatingSystemId =
        JsonContractResourceSupport.requireExactText(document, SCHEMA_KEYS.operatingSystemId());
    String architectureId =
        JsonContractResourceSupport.requireExactText(document, SCHEMA_KEYS.architectureId());
    BundleLayoutContract.PublicBundlePublication publication =
        publicationContract.bundleTargets().get(target);
    if (publication == null) {
      throw new IllegalStateException(
          "bundle publication contract must declare one publication object for "
              + target
              + " in "
              + publicationResourcePath
              + ".");
    }
    return new BundleLayoutContract.BundleTarget(
        operatingSystemId,
        architectureId,
        JsonContractResourceSupport.requireExactText(document, SCHEMA_KEYS.archiveFormat()),
        JsonContractResourceSupport.requireExactText(document, SCHEMA_KEYS.launcherPath()),
        JsonContractResourceSupport.requireExactText(document, SCHEMA_KEYS.launcherCommand()),
        JsonContractResourceSupport.requireExactText(document, SCHEMA_KEYS.sqliteLibraryFileName()),
        JsonContractResourceSupport.requireText(document, SCHEMA_KEYS.compatibilityLabel()),
        optionalExactText(document, SCHEMA_KEYS.minimumGlibcVersion()),
        optionalExactText(document, SCHEMA_KEYS.compatibilitySmokeContainerImage()),
        publication);
  }

  private static BundleLayoutContract loadCurrent() {
    return loadFromResources(
        BundleLayoutContracts.class.getResourceAsStream(RESOURCE_PATH),
        RESOURCE_PATH,
        BundleLayoutContracts.class.getResourceAsStream(PUBLICATION_RESOURCE_PATH),
        PUBLICATION_RESOURCE_PATH);
  }

  private static Optional<String> optionalExactText(ObjectNode document, String key) {
    JsonNode node = document.path(key);
    if (node.isMissingNode() || node.isNull()) {
      return Optional.empty();
    }
    return Optional.of(JsonContractResourceSupport.requireExactText(document, key));
  }
}
