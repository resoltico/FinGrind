package dev.erst.fingrind.contract.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Properties;
import org.jspecify.annotations.Nullable;

/** Protocol-owned canonical mapping from operation enum names to public wire identifiers. */
final class OperationIdContract {
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/operation-id-contract.properties";
  private static final OperationIdContract CURRENT = loadCurrent();

  private final Properties operationNames;

  private OperationIdContract(Properties operationNames) {
    this.operationNames = new Properties();
    this.operationNames.putAll(operationNames);
  }

  static OperationIdContract current() {
    return CURRENT;
  }

  String wireName(String operationConstantName) {
    Objects.requireNonNull(operationConstantName, "operationConstantName");
    String wireName = operationNames.getProperty(operationConstantName);
    if (wireName == null || wireName.isBlank()) {
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
    Properties properties = new Properties();
    try (resourceStream) {
      if (resourceStream == null) {
        throw new IllegalStateException("Missing operation-id contract resource: " + resourcePath);
      }
      properties.load(resourceStream);
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to load operation-id contract resource: " + resourcePath, exception);
    }
    return new OperationIdContract(properties);
  }
}
