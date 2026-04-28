package dev.erst.fingrind.contract.protocol;

import java.io.InputStream;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Shared schema-key owner for protocol-owned JSON contract resources. */
final class ProtocolContractSchemaKeys {
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/contract-schema-keys.json";
  private static final ProtocolContractSchemaKeys CURRENT = loadCurrent();

  private final RuntimeSurface runtimeSurface;
  private final PublicDistribution publicDistribution;
  private final ManagedSqlite managedSqlite;
  private final BundleLayout bundleLayout;
  private final OperationIds operationIds;

  private ProtocolContractSchemaKeys(
      RuntimeSurface runtimeSurface,
      PublicDistribution publicDistribution,
      ManagedSqlite managedSqlite,
      BundleLayout bundleLayout,
      OperationIds operationIds) {
    this.runtimeSurface = Objects.requireNonNull(runtimeSurface, "runtimeSurface");
    this.publicDistribution = Objects.requireNonNull(publicDistribution, "publicDistribution");
    this.managedSqlite = Objects.requireNonNull(managedSqlite, "managedSqlite");
    this.bundleLayout = Objects.requireNonNull(bundleLayout, "bundleLayout");
    this.operationIds = Objects.requireNonNull(operationIds, "operationIds");
  }

  static ProtocolContractSchemaKeys current() {
    return CURRENT;
  }

  RuntimeSurface runtimeSurface() {
    return runtimeSurface;
  }

  PublicDistribution publicDistribution() {
    return publicDistribution;
  }

  ManagedSqlite managedSqlite() {
    return managedSqlite;
  }

  BundleLayout bundleLayout() {
    return bundleLayout;
  }

  OperationIds operationIds() {
    return operationIds;
  }

  static ProtocolContractSchemaKeys loadFromResource(
      @Nullable InputStream resourceStream, String resourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath");
    JsonNode document =
        JsonContractResourceSupport.loadObject(
            resourceStream, resourcePath, "protocol contract schema keys");
    JsonNode runtimeSurfaceNode = requiredObject(document, "runtimeSurface");
    JsonNode publicDistributionNode = requiredObject(document, "publicDistribution");
    JsonNode managedSqliteNode = requiredObject(document, "managedSqlite");
    JsonNode bundleLayoutNode = requiredObject(document, "bundleLayout");
    JsonNode operationIdsNode = requiredObject(document, "operationIdContract");
    return new ProtocolContractSchemaKeys(
        new RuntimeSurface(
            requireText(runtimeSurfaceNode, "directJavaRuntimeDistribution"),
            requireText(runtimeSurfaceNode, "sourceCheckoutRuntimeDistribution"),
            requireText(runtimeSurfaceNode, "containerRuntimeDistribution"),
            requireText(runtimeSurfaceNode, "bundleRuntimeDistribution"),
            requireText(runtimeSurfaceNode, "publicCliDistribution"),
            requireText(runtimeSurfaceNode, "storageDriver"),
            requireText(runtimeSurfaceNode, "storageEngine"),
            requireText(runtimeSurfaceNode, "bookProtectionMode"),
            requireText(runtimeSurfaceNode, "defaultBookCipher"),
            requireText(runtimeSurfaceNode, "sqliteLibraryMode"),
            requireText(runtimeSurfaceNode, "sqliteLibraryEnvironmentVariable"),
            requireText(runtimeSurfaceNode, "sqliteBundleHomeSystemProperty")),
        new PublicDistribution(
            requireText(publicDistributionNode, "supportedPublicCliBundleTargets"),
            requireText(publicDistributionNode, "unsupportedPublicCliBundleTargets")),
        new ManagedSqlite(
            requireText(managedSqliteNode, "requiredMinimumSqliteVersion"),
            requireText(managedSqliteNode, "requiredSqlite3mcVersion"),
            requireText(managedSqliteNode, "requiredSqliteSourceId"),
            requireText(managedSqliteNode, "requiredCompileOptions")),
        new BundleLayout(
            requireText(bundleLayoutNode, "bundleTargets"),
            requireText(bundleLayoutNode, "operatingSystemId"),
            requireText(bundleLayoutNode, "architectureId"),
            requireText(bundleLayoutNode, "archiveFormat"),
            requireText(bundleLayoutNode, "launcherPath"),
            requireText(bundleLayoutNode, "launcherCommand"),
            requireText(bundleLayoutNode, "sqliteLibraryFileName")),
        new OperationIds(
            requireText(operationIdsNode, "help"),
            requireText(operationIdsNode, "version"),
            requireText(operationIdsNode, "capabilities"),
            requireText(operationIdsNode, "printRequestTemplate"),
            requireText(operationIdsNode, "printPlanTemplate"),
            requireText(operationIdsNode, "generateBookKeyFile"),
            requireText(operationIdsNode, "openBook"),
            requireText(operationIdsNode, "rekeyBook"),
            requireText(operationIdsNode, "declareAccount"),
            requireText(operationIdsNode, "inspectBook"),
            requireText(operationIdsNode, "listAccounts"),
            requireText(operationIdsNode, "getPosting"),
            requireText(operationIdsNode, "listPostings"),
            requireText(operationIdsNode, "accountBalance"),
            requireText(operationIdsNode, "trialBalance"),
            requireText(operationIdsNode, "accountLedger"),
            requireText(operationIdsNode, "periodSummary"),
            requireText(operationIdsNode, "executePlan"),
            requireText(operationIdsNode, "preflightEntry"),
            requireText(operationIdsNode, "postEntry")));
  }

  private static ProtocolContractSchemaKeys loadCurrent() {
    return loadFromResource(
        ProtocolContractSchemaKeys.class.getResourceAsStream(RESOURCE_PATH), RESOURCE_PATH);
  }

  private static JsonNode requiredObject(JsonNode document, String key) {
    JsonNode node = document.get(key);
    if (node == null || !node.isObject()) {
      throw new IllegalArgumentException(key + " must be one JSON object of schema keys.");
    }
    return node;
  }

  private static String requireText(JsonNode document, String key) {
    return JsonContractResourceSupport.requireText(document, key);
  }

  /** Canonical external field names for the runtime-surface contract resource. */
  record RuntimeSurface(
      String directJavaRuntimeDistribution,
      String sourceCheckoutRuntimeDistribution,
      String containerRuntimeDistribution,
      String bundleRuntimeDistribution,
      String publicCliDistribution,
      String storageDriver,
      String storageEngine,
      String bookProtectionMode,
      String defaultBookCipher,
      String sqliteLibraryMode,
      String sqliteLibraryEnvironmentVariable,
      String sqliteBundleHomeSystemProperty) {}

  /** Canonical external field names for the public-distribution contract resource. */
  record PublicDistribution(
      String supportedPublicCliBundleTargets, String unsupportedPublicCliBundleTargets) {}

  /** Canonical external field names for the managed-SQLite contract resource. */
  record ManagedSqlite(
      String requiredMinimumSqliteVersion,
      String requiredSqlite3mcVersion,
      String requiredSqliteSourceId,
      String requiredCompileOptions) {}

  /** Canonical external field names for the per-target bundle-layout contract resource. */
  record BundleLayout(
      String bundleTargets,
      String operatingSystemId,
      String architectureId,
      String archiveFormat,
      String launcherPath,
      String launcherCommand,
      String sqliteLibraryFileName) {}

  /** Canonical external property names for the operation-id contract resource. */
  record OperationIds(
      String help,
      String version,
      String capabilities,
      String printRequestTemplate,
      String printPlanTemplate,
      String generateBookKeyFile,
      String openBook,
      String rekeyBook,
      String declareAccount,
      String inspectBook,
      String listAccounts,
      String getPosting,
      String listPostings,
      String accountBalance,
      String trialBalance,
      String accountLedger,
      String periodSummary,
      String executePlan,
      String preflightEntry,
      String postEntry) {}
}
