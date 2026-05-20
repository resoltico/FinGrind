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
  private final ProtectedBookFormat protectedBookFormat;
  private final PublicDistribution publicDistribution;
  private final ManagedSqlite managedSqlite;
  private final BundleLayout bundleLayout;
  private final OperationIds operationIds;

  private ProtocolContractSchemaKeys(
      RuntimeSurface runtimeSurface,
      ProtectedBookFormat protectedBookFormat,
      PublicDistribution publicDistribution,
      ManagedSqlite managedSqlite,
      BundleLayout bundleLayout,
      OperationIds operationIds) {
    this.runtimeSurface = Objects.requireNonNull(runtimeSurface, "runtimeSurface");
    this.protectedBookFormat = Objects.requireNonNull(protectedBookFormat, "protectedBookFormat");
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

  ProtectedBookFormat protectedBookFormat() {
    return protectedBookFormat;
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
    JsonNode protectedBookFormatNode = requiredObject(document, "protectedBookFormat");
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
            requireText(runtimeSurfaceNode, "sqliteBundleHomeSystemProperty")),
        new ProtectedBookFormat(
            requireText(protectedBookFormatNode, "applicationId"),
            requireText(protectedBookFormatNode, "formatVersion"),
            requireText(protectedBookFormatNode, "cipher"),
            requireText(protectedBookFormatNode, "legacyMode"),
            requireText(protectedBookFormatNode, "pageSize"),
            requireText(protectedBookFormatNode, "reservedBytes"),
            requireText(protectedBookFormatNode, "legacyPageSize"),
            requireText(protectedBookFormatNode, "kdfIter"),
            requireText(protectedBookFormatNode, "plaintextHeaderSize")),
        new PublicDistribution(
            requireText(publicDistributionNode, "supportedPublicCliBundleTargets"),
            requireText(publicDistributionNode, "unsupportedPublicCliBundleTargets")),
        new ManagedSqlite(
            requireText(managedSqliteNode, "requiredMinimumSqliteVersion"),
            requireText(managedSqliteNode, "requiredSqlite3mcVersion"),
            requireText(managedSqliteNode, "requiredSqliteSourceId"),
            requireText(managedSqliteNode, "requiredSourcePackageId"),
            requireText(managedSqliteNode, "vendoredReleaseFiles"),
            requireText(managedSqliteNode, "nativeHardening"),
            requireText(managedSqliteNode, "nativeHardeningUnixCompilerFlags"),
            requireText(managedSqliteNode, "nativeHardeningLinuxLinkerFlags"),
            requireText(managedSqliteNode, "nativeHardeningMacosLinkerFlags"),
            requireText(managedSqliteNode, "nativeHardeningWindowsCompilerFlags"),
            requireText(managedSqliteNode, "nativeHardeningWindowsLinkerFlags"),
            requireText(managedSqliteNode, "requiredCompileOptions"),
            requireText(managedSqliteNode, "forbiddenCompileOptions"),
            requireText(managedSqliteNode, "requiresSecureMemorySupport")),
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
            requireText(operationIdsNode, "backupBook"),
            requireText(operationIdsNode, "restoreBook"),
            requireText(operationIdsNode, "inspectRekeyRollback"),
            requireText(operationIdsNode, "deleteRekeyRollback"),
            requireText(operationIdsNode, "restoreRekeyRollback"),
            requireText(operationIdsNode, "declareAccount"),
            requireText(operationIdsNode, "closePeriod"),
            requireText(operationIdsNode, "inspectBook"),
            requireText(operationIdsNode, "listAccounts"),
            requireText(operationIdsNode, "getPosting"),
            requireText(operationIdsNode, "listPostings"),
            requireText(operationIdsNode, "accountBalance"),
            requireText(operationIdsNode, "trialBalance"),
            requireText(operationIdsNode, "accountLedger"),
            requireText(operationIdsNode, "periodSummary"),
            requireText(operationIdsNode, "financialPosition"),
            requireText(operationIdsNode, "incomeStatement"),
            requireText(operationIdsNode, "changesInEquity"),
            requireText(operationIdsNode, "executePlan"),
            requireText(operationIdsNode, "preflightEntry"),
            requireText(operationIdsNode, "postEntry")));
  }

  private static ProtocolContractSchemaKeys loadCurrent() {
    return loadFromResource(
        ProtocolContractSchemaKeys.class.getResourceAsStream(RESOURCE_PATH), RESOURCE_PATH);
  }

  private static JsonNode requiredObject(JsonNode document, String key) {
    return JsonContractResourceSupport.requireObject(
        document, key, key + " must be one JSON object of schema keys.");
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
      String sqliteBundleHomeSystemProperty) {}

  /** Canonical external field names for the protected-book-format contract resource. */
  record ProtectedBookFormat(
      String applicationId,
      String formatVersion,
      String cipher,
      String legacyMode,
      String pageSize,
      String reservedBytes,
      String legacyPageSize,
      String kdfIter,
      String plaintextHeaderSize) {}

  /** Canonical external field names for the public-distribution contract resource. */
  record PublicDistribution(
      String supportedPublicCliBundleTargets, String unsupportedPublicCliBundleTargets) {}

  /** Canonical external field names for the managed-SQLite contract resource. */
  record ManagedSqlite(
      String requiredMinimumSqliteVersion,
      String requiredSqlite3mcVersion,
      String requiredSqliteSourceId,
      String requiredSourcePackageId,
      String vendoredReleaseFiles,
      String nativeHardening,
      String nativeHardeningUnixCompilerFlags,
      String nativeHardeningLinuxLinkerFlags,
      String nativeHardeningMacosLinkerFlags,
      String nativeHardeningWindowsCompilerFlags,
      String nativeHardeningWindowsLinkerFlags,
      String requiredCompileOptions,
      String forbiddenCompileOptions,
      String requiresSecureMemorySupport) {}

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
      String backupBook,
      String restoreBook,
      String inspectRekeyRollback,
      String deleteRekeyRollback,
      String restoreRekeyRollback,
      String declareAccount,
      String closePeriod,
      String inspectBook,
      String listAccounts,
      String getPosting,
      String listPostings,
      String accountBalance,
      String trialBalance,
      String accountLedger,
      String periodSummary,
      String financialPosition,
      String incomeStatement,
      String changesInEquity,
      String executePlan,
      String preflightEntry,
      String postEntry) {}
}
