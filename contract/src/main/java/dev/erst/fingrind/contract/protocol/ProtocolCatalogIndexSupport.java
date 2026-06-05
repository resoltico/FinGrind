package dev.erst.fingrind.contract.protocol;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Indexing helpers for the published protocol operation catalog. */
final class ProtocolCatalogIndexSupport {
  private ProtocolCatalogIndexSupport() {}

  static Map<OperationId, ProtocolOperation> indexById(List<ProtocolOperation> operations) {
    Map<OperationId, ProtocolOperation> indexed = new ConcurrentHashMap<>();
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
    Map<String, ProtocolOperation> indexed = new ConcurrentHashMap<>();
    operations.stream()
        .flatMap(ProtocolCatalogIndexSupport::tokensFor)
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

  private static Stream<Map.Entry<String, ProtocolOperation>> tokensFor(
      ProtocolOperation operation) {
    return Stream.concat(
        Stream.of(new AbstractMap.SimpleImmutableEntry<>(operation.id().wireName(), operation)),
        operation.aliases().stream()
            .map(alias -> new AbstractMap.SimpleImmutableEntry<>(alias, operation)));
  }
}
