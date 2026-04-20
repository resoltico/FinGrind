package dev.erst.fingrind.contract.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Properties;
import org.jspecify.annotations.Nullable;

/** Protocol-owned runtime facts that must stay aligned with the build configuration. */
final class RuntimeEnvironmentContract {
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/runtime-environment-contract.properties";
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
    Properties properties = new Properties();
    try (resourceStream) {
      if (resourceStream == null) {
        throw new IllegalStateException(
            "Missing runtime-environment contract resource: " + resourcePath);
      }
      properties.load(resourceStream);
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to load runtime-environment contract resource: " + resourcePath, exception);
    }
    return new RuntimeEnvironmentContract(properties.getProperty(SOURCE_CHECKOUT_JAVA_KEY));
  }

  private static String requireSourceCheckoutJava(String sourceCheckoutJava) {
    Objects.requireNonNull(sourceCheckoutJava, SOURCE_CHECKOUT_JAVA_KEY);
    if (sourceCheckoutJava.isBlank()) {
      throw new IllegalArgumentException(SOURCE_CHECKOUT_JAVA_KEY + " must not be blank.");
    }
    return sourceCheckoutJava;
  }
}
