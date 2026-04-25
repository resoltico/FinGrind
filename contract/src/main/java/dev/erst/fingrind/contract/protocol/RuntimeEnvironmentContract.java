package dev.erst.fingrind.contract.protocol;

import java.io.InputStream;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Protocol-owned runtime facts that must stay aligned with the build configuration. */
final class RuntimeEnvironmentContract {
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/runtime-environment-contract.json";
  private static final String SOURCE_CHECKOUT_JAVA_KEY = "sourceCheckoutJava";
  private static final RuntimeEnvironmentContract CURRENT = loadCurrent();

  private final String sourceCheckoutJava;

  /** Validates one runtime-environment contract snapshot. */
  RuntimeEnvironmentContract(String sourceCheckoutJava) {
    this.sourceCheckoutJava = requireSourceCheckoutJava(sourceCheckoutJava);
  }

  /** Returns the current protocol-owned runtime-environment contract. */
  static RuntimeEnvironmentContract current() {
    return CURRENT;
  }

  String sourceCheckoutJava() {
    return sourceCheckoutJava;
  }

  private static RuntimeEnvironmentContract loadCurrent() {
    return loadFromResource(
        RuntimeEnvironmentContract.class.getResourceAsStream(RESOURCE_PATH), RESOURCE_PATH);
  }

  static RuntimeEnvironmentContract loadFromResource(
      @Nullable InputStream resourceStream, String resourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath");
    JsonNode document =
        JsonContractResourceSupport.loadObject(
            resourceStream, resourcePath, "runtime-environment contract");
    return new RuntimeEnvironmentContract(
        JsonContractResourceSupport.requireText(document, SOURCE_CHECKOUT_JAVA_KEY));
  }

  private static String requireSourceCheckoutJava(String sourceCheckoutJava) {
    Objects.requireNonNull(sourceCheckoutJava, SOURCE_CHECKOUT_JAVA_KEY);
    if (sourceCheckoutJava.isBlank()) {
      throw new IllegalArgumentException(SOURCE_CHECKOUT_JAVA_KEY + " must not be blank.");
    }
    return sourceCheckoutJava;
  }
}
