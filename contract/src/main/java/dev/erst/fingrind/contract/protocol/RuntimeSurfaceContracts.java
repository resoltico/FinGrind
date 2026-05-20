package dev.erst.fingrind.contract.protocol;

import java.io.InputStream;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Loads and publishes the current runtime-surface contract snapshot. */
final class RuntimeSurfaceContracts {
  private static final ProtocolContractSchemaKeys.RuntimeSurface SCHEMA_KEYS =
      ProtocolContractSchemaKeys.current().runtimeSurface();
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/runtime-surface-contract.json";
  private static final String DIRECT_JAVA_RUNTIME_DISTRIBUTION_KEY =
      SCHEMA_KEYS.directJavaRuntimeDistribution();
  private static final String SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION_KEY =
      SCHEMA_KEYS.sourceCheckoutRuntimeDistribution();
  private static final String CONTAINER_RUNTIME_DISTRIBUTION_KEY =
      SCHEMA_KEYS.containerRuntimeDistribution();
  private static final String BUNDLE_RUNTIME_DISTRIBUTION_KEY =
      SCHEMA_KEYS.bundleRuntimeDistribution();
  private static final String PUBLIC_CLI_DISTRIBUTION_KEY = SCHEMA_KEYS.publicCliDistribution();
  private static final String STORAGE_DRIVER_KEY = SCHEMA_KEYS.storageDriver();
  private static final String STORAGE_ENGINE_KEY = SCHEMA_KEYS.storageEngine();
  private static final String BOOK_PROTECTION_MODE_KEY = SCHEMA_KEYS.bookProtectionMode();
  private static final String DEFAULT_BOOK_CIPHER_KEY = SCHEMA_KEYS.defaultBookCipher();
  private static final String SQLITE_LIBRARY_MODE_KEY = SCHEMA_KEYS.sqliteLibraryMode();
  private static final String SQLITE_BUNDLE_HOME_SYSTEM_PROPERTY_KEY =
      SCHEMA_KEYS.sqliteBundleHomeSystemProperty();
  private static final RuntimeSurfaceContract CURRENT = loadCurrent();

  private RuntimeSurfaceContracts() {}

  static RuntimeSurfaceContract current() {
    return CURRENT;
  }

  static RuntimeSurfaceContract loadFromResource(
      @Nullable InputStream resourceStream, String resourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath");
    JsonNode document =
        JsonContractResourceSupport.loadObject(
            resourceStream, resourcePath, "runtime surface contract");
    return new RuntimeSurfaceContract(
        requireWireValue(
            document, DIRECT_JAVA_RUNTIME_DISTRIBUTION_KEY, RuntimeDistribution::fromWireValue),
        requireWireValue(
            document, SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION_KEY, RuntimeDistribution::fromWireValue),
        requireWireValue(
            document, CONTAINER_RUNTIME_DISTRIBUTION_KEY, RuntimeDistribution::fromWireValue),
        requireWireValue(
            document, BUNDLE_RUNTIME_DISTRIBUTION_KEY, RuntimeDistribution::fromWireValue),
        requireWireValue(
            document, PUBLIC_CLI_DISTRIBUTION_KEY, PublicCliDistribution::fromWireValue),
        requireWireValue(document, STORAGE_DRIVER_KEY, StorageDriver::fromWireValue),
        requireWireValue(document, STORAGE_ENGINE_KEY, StorageEngine::fromWireValue),
        requireWireValue(document, BOOK_PROTECTION_MODE_KEY, BookProtectionMode::fromWireValue),
        requireWireValue(document, DEFAULT_BOOK_CIPHER_KEY, BookCipher::fromWireValue),
        requireWireValue(document, SQLITE_LIBRARY_MODE_KEY, SqliteLibraryMode::fromWireValue),
        requireText(document, SQLITE_BUNDLE_HOME_SYSTEM_PROPERTY_KEY));
  }

  private static RuntimeSurfaceContract loadCurrent() {
    return loadFromResource(
        RuntimeSurfaceContracts.class.getResourceAsStream(RESOURCE_PATH), RESOURCE_PATH);
  }

  private static String requireText(JsonNode document, String key) {
    return JsonContractResourceSupport.requireText(document, key);
  }

  private static <T> T requireWireValue(JsonNode document, String key, Function<String, T> parser) {
    return parser.apply(requireText(document, key));
  }
}
