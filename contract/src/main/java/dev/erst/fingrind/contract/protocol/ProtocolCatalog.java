package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Core-owned protocol catalog for FinGrind public operation and model metadata. */
public final class ProtocolCatalog {
  private static final ProtocolEnvelopeCatalog ENVELOPES = ProtocolEnvelopeCatalog.INSTANCE;
  private static final ProtocolDomainCatalog DOMAIN = ProtocolDomainCatalog.INSTANCE;
  private static final ProtocolRuntimeCatalog RUNTIME = ProtocolRuntimeCatalog.INSTANCE;
  private static final ProtocolDistributionCatalog DISTRIBUTION =
      ProtocolDistributionCatalog.INSTANCE;
  private static final ProtocolManagedSqliteCatalog MANAGED_SQLITE =
      ProtocolManagedSqliteCatalog.INSTANCE;
  private static final List<ProtocolOperation> OPERATIONS =
      Stream.of(
              ProtocolDiscoveryOperations.operations(),
              ProtocolAdministrationOperations.operations(),
              ProtocolQueryOperations.operations(),
              ProtocolWriteOperations.operations())
          .flatMap(List::stream)
          .toList();
  private static final Map<OperationId, ProtocolOperation> BY_ID =
      ProtocolCatalogIndexSupport.indexById(OPERATIONS);
  private static final Map<String, ProtocolOperation> BY_TOKEN =
      ProtocolCatalogIndexSupport.indexByToken(OPERATIONS);

  private ProtocolCatalog() {}

  /** Returns every operation in stable help and capabilities order. */
  public static List<ProtocolOperation> operations() {
    return OPERATIONS;
  }

  /** Returns the operation descriptor for a canonical operation identifier. */
  public static ProtocolOperation operation(OperationId operationId) {
    return ProtocolCatalogIndexSupport.requireOperation(BY_ID, operationId);
  }

  /** Returns the stable wire name for one canonical operation identifier. */
  public static String operationName(OperationId operationId) {
    return operation(operationId).id().wireName();
  }

  /** Finds an operation by canonical operation name or public alias. */
  public static Optional<ProtocolOperation> findByToken(String token) {
    return Optional.ofNullable(BY_TOKEN.get(Objects.requireNonNull(token, "token")));
  }

  /** Returns operation names in stable order for one capabilities group. */
  public static List<String> operationNames(OperationCategory category) {
    Objects.requireNonNull(category, "category");
    return operationIds(category).stream().map(OperationId::wireName).toList();
  }

  /** Returns canonical operation identifiers in stable order for one capabilities group. */
  public static List<OperationId> operationIds(OperationCategory category) {
    Objects.requireNonNull(category, "category");
    return OPERATIONS.stream()
        .filter(operation -> operation.category() == category)
        .map(ProtocolOperation::id)
        .toList();
  }

  /** Returns the public envelope-status catalog. */
  public static ProtocolEnvelopeCatalog envelopes() {
    return ENVELOPES;
  }

  /** Returns the public domain-facts catalog. */
  public static ProtocolDomainCatalog domain() {
    return DOMAIN;
  }

  /** Returns the public runtime and distribution catalog. */
  public static ProtocolRuntimeCatalog runtime() {
    return RUNTIME;
  }

  /** Returns the public distribution and launcher catalog. */
  public static ProtocolDistributionCatalog distribution() {
    return DISTRIBUTION;
  }

  /** Returns the public managed-SQLite contract catalog. */
  public static ProtocolManagedSqliteCatalog managedSqlite() {
    return MANAGED_SQLITE;
  }
}
