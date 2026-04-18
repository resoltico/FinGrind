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
    return Objects.requireNonNull(
        BY_ID.get(Objects.requireNonNull(operationId, "operationId")), "operation");
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
    return OPERATIONS.stream()
        .filter(operation -> operation.category() == category)
        .map(operation -> operation.id().wireName())
        .toList();
  }

  /** Returns the canonical storage engine identifiers. */
  public static List<String> storageEngines() {
    return ProtocolCatalogFacts.storageEngines();
  }

  /** Returns the canonical success-status identifiers. */
  public static List<String> successStatuses() {
    return ProtocolCatalogFacts.successStatuses();
  }

  /** Returns the canonical deterministic rejection-status identifiers. */
  public static List<String> rejectionStatuses() {
    return ProtocolCatalogFacts.rejectionStatuses();
  }

  /** Returns the structured hard book-model facts. */
  public static BookModelFacts bookModel() {
    return ProtocolCatalogFacts.bookModel();
  }

  /** Returns the structured currency-model facts. */
  public static CurrencyFacts currency() {
    return ProtocolCatalogFacts.currency();
  }

  /** Returns the structured preflight semantics. */
  public static PreflightFacts preflight() {
    return ProtocolCatalogFacts.preflight();
  }

  /** Returns the structured ledger-plan execution semantics. */
  public static PlanExecutionFacts planExecution() {
    return ProtocolCatalogFacts.planExecution();
  }

  /** Returns supported self-contained public CLI bundle targets. */
  public static List<String> supportedPublicCliBundleTargets() {
    return ProtocolCatalogFacts.publicDistributionContract().supportedPublicCliBundleTargets();
  }

  /** Returns operating systems outside the current self-contained public CLI contract. */
  public static List<String> unsupportedPublicCliOperatingSystems() {
    return ProtocolCatalogFacts.publicDistributionContract().unsupportedPublicCliOperatingSystems();
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
}
