package dev.erst.fingrind.contract.protocol;

import java.io.InputStream;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Loads and publishes the current managed-SQLite contract snapshot. */
final class ManagedSqliteContracts {
  private static final ProtocolContractSchemaKeys.ManagedSqlite SCHEMA_KEYS =
      ProtocolContractSchemaKeys.current().managedSqlite();
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/managed-sqlite-contract.json";
  private static final String REQUIRED_MINIMUM_SQLITE_VERSION_KEY =
      SCHEMA_KEYS.requiredMinimumSqliteVersion();
  private static final String REQUIRED_SQLITE3MC_VERSION_KEY =
      SCHEMA_KEYS.requiredSqlite3mcVersion();
  private static final String REQUIRED_SQLITE_SOURCE_ID_KEY = SCHEMA_KEYS.requiredSqliteSourceId();
  private static final String REQUIRED_COMPILE_OPTIONS_KEY = SCHEMA_KEYS.requiredCompileOptions();
  private static final String FORBIDDEN_COMPILE_OPTIONS_KEY = SCHEMA_KEYS.forbiddenCompileOptions();
  private static final String REQUIRES_SECURE_MEMORY_SUPPORT_KEY =
      SCHEMA_KEYS.requiresSecureMemorySupport();
  private static final ManagedSqliteContract CURRENT = loadCurrent();

  private ManagedSqliteContracts() {}

  static ManagedSqliteContract current() {
    return CURRENT;
  }

  static ManagedSqliteContract loadFromResource(
      @Nullable InputStream resourceStream, String resourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath");
    JsonNode document =
        JsonContractResourceSupport.loadObject(
            resourceStream, resourcePath, "managed SQLite contract");
    return new ManagedSqliteContract(
        JsonContractResourceSupport.requireText(document, REQUIRED_MINIMUM_SQLITE_VERSION_KEY),
        JsonContractResourceSupport.requireText(document, REQUIRED_SQLITE3MC_VERSION_KEY),
        JsonContractResourceSupport.requireText(document, REQUIRED_SQLITE_SOURCE_ID_KEY),
        JsonContractResourceSupport.requireStringArray(document, REQUIRED_COMPILE_OPTIONS_KEY),
        JsonContractResourceSupport.requireStringArray(document, FORBIDDEN_COMPILE_OPTIONS_KEY),
        JsonContractResourceSupport.requireBoolean(document, REQUIRES_SECURE_MEMORY_SUPPORT_KEY));
  }

  private static ManagedSqliteContract loadCurrent() {
    return loadFromResource(
        ManagedSqliteContracts.class.getResourceAsStream(RESOURCE_PATH), RESOURCE_PATH);
  }
}
