package dev.erst.fingrind.contract.protocol;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
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
  private static final Map<OperationId, ProtocolOperation> BY_ID = indexById(OPERATIONS);
  private static final Map<String, ProtocolOperation> BY_TOKEN = indexByToken(OPERATIONS);

  private ProtocolCatalog() {}

  /** Returns every operation in stable help and capabilities order. */
  public static List<ProtocolOperation> operations() {
    return OPERATIONS;
  }

  /** Returns the operation descriptor for a canonical operation identifier. */
  public static ProtocolOperation operation(OperationId operationId) {
    return requireOperation(BY_ID, operationId);
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

  private static Stream<Map.Entry<String, ProtocolOperation>> tokensFor(
      ProtocolOperation operation) {
    return Stream.concat(
        Stream.of(new AbstractMap.SimpleImmutableEntry<>(operation.id().wireName(), operation)),
        operation.aliases().stream()
            .map(alias -> new AbstractMap.SimpleImmutableEntry<>(alias, operation)));
  }

  static Map<OperationId, ProtocolOperation> indexById(List<ProtocolOperation> operations) {
    Map<OperationId, ProtocolOperation> indexed = newProtocolOperationIndex();
    for (ProtocolOperation operation : operations) {
      ProtocolOperation prior = indexed.putIfAbsent(operation.id(), operation);
      if (prior != null) {
        throw new IllegalStateException(
            "Duplicate protocol operation id: " + operation.id().wireName());
      }
    }
    return Map.copyOf(indexed);
  }

  static Map<String, ProtocolOperation> indexByToken(List<ProtocolOperation> operations) {
    Map<String, ProtocolOperation> indexed = newTokenIndex();
    operations.stream()
        .flatMap(ProtocolCatalog::tokensFor)
        .forEach(
            token -> {
              ProtocolOperation prior = indexed.putIfAbsent(token.getKey(), token.getValue());
              if (prior != null) {
                throw new IllegalStateException(
                    "Duplicate protocol operation token: " + token.getKey());
              }
            });
    return Map.copyOf(indexed);
  }

  private static <K, V> Map<K, V> newProtocolOperationIndex() {
    return new LinkedHashMap<>();
  }

  private static Map<String, ProtocolOperation> newTokenIndex() {
    return new LinkedHashMap<>();
  }

  static ProtocolOperation requireOperation(
      Map<OperationId, ProtocolOperation> operationsById, OperationId operationId) {
    Objects.requireNonNull(operationsById, "operationsById");
    OperationId requiredOperationId = Objects.requireNonNull(operationId, "operationId");
    ProtocolOperation operation = operationsById.get(requiredOperationId);
    if (operation == null) {
      throw new IllegalStateException(
          "No protocol catalog entry is registered for operationId "
              + requiredOperationId.name()
              + ".");
    }
    return operation;
  }
}
