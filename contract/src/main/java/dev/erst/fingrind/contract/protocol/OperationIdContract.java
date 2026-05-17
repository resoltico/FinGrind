package dev.erst.fingrind.contract.protocol;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/** Protocol-owned canonical mapping from operation enum names to public wire identifiers. */
final class OperationIdContract {
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/operation-id-contract.json";
  private static final OperationIdContract CURRENT = loadCurrent();

  private final Map<String, String> operationNames;

  private OperationIdContract(Map<String, String> operationNames) {
    this.operationNames = Map.copyOf(operationNames);
  }

  static OperationIdContract current() {
    return CURRENT;
  }

  String wireName(String operationConstantName) {
    Objects.requireNonNull(operationConstantName, "operationConstantName");
    String wireName = operationNames.get(operationConstantName);
    if (wireName == null) {
      throw new IllegalStateException(
          "Missing public operation wire name for " + operationConstantName + ".");
    }
    return wireName;
  }

  private static OperationIdContract loadCurrent() {
    return loadFromResource(
        OperationIdContract.class.getResourceAsStream(RESOURCE_PATH), RESOURCE_PATH);
  }

  static OperationIdContract loadFromResource(
      @Nullable InputStream resourceStream, String resourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath");
    ObjectNode document =
        JsonContractResourceSupport.loadObject(
            resourceStream, resourcePath, "operation-id contract");
    Map<String, String> operationNames =
        document
            .propertyStream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry -> JsonContractResourceSupport.requireText(document, entry.getKey())));
    return new OperationIdContract(operationNames);
  }
}
