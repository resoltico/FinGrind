package dev.erst.fingrind.contract.protocol;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
  private static final String REQUIRED_SOURCE_PACKAGE_ID_KEY =
      SCHEMA_KEYS.requiredSourcePackageId();
  private static final String VENDORED_RELEASE_FILES_KEY = SCHEMA_KEYS.vendoredReleaseFiles();
  private static final String NATIVE_HARDENING_KEY = SCHEMA_KEYS.nativeHardening();
  private static final String NATIVE_HARDENING_UNIX_COMPILER_FLAGS_KEY =
      SCHEMA_KEYS.nativeHardeningUnixCompilerFlags();
  private static final String NATIVE_HARDENING_LINUX_LINKER_FLAGS_KEY =
      SCHEMA_KEYS.nativeHardeningLinuxLinkerFlags();
  private static final String NATIVE_HARDENING_MACOS_LINKER_FLAGS_KEY =
      SCHEMA_KEYS.nativeHardeningMacosLinkerFlags();
  private static final String NATIVE_HARDENING_WINDOWS_COMPILER_FLAGS_KEY =
      SCHEMA_KEYS.nativeHardeningWindowsCompilerFlags();
  private static final String NATIVE_HARDENING_WINDOWS_LINKER_FLAGS_KEY =
      SCHEMA_KEYS.nativeHardeningWindowsLinkerFlags();
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
    JsonNode nativeHardening =
        JsonContractResourceSupport.requireObject(
            document,
            NATIVE_HARDENING_KEY,
            NATIVE_HARDENING_KEY + " must be one JSON object of hardening flags.");
    return new ManagedSqliteContract(
        JsonContractResourceSupport.requireText(document, REQUIRED_MINIMUM_SQLITE_VERSION_KEY),
        JsonContractResourceSupport.requireText(document, REQUIRED_SQLITE3MC_VERSION_KEY),
        JsonContractResourceSupport.requireText(document, REQUIRED_SQLITE_SOURCE_ID_KEY),
        JsonContractResourceSupport.requireText(document, REQUIRED_SOURCE_PACKAGE_ID_KEY),
        requireVendoredReleaseFiles(document, VENDORED_RELEASE_FILES_KEY),
        new ManagedSqliteContract.NativeHardeningContract(
            JsonContractResourceSupport.requireStringArray(
                nativeHardening, NATIVE_HARDENING_UNIX_COMPILER_FLAGS_KEY),
            JsonContractResourceSupport.requireStringArray(
                nativeHardening, NATIVE_HARDENING_LINUX_LINKER_FLAGS_KEY),
            JsonContractResourceSupport.requireStringArray(
                nativeHardening, NATIVE_HARDENING_MACOS_LINKER_FLAGS_KEY),
            JsonContractResourceSupport.requireStringArray(
                nativeHardening, NATIVE_HARDENING_WINDOWS_COMPILER_FLAGS_KEY),
            JsonContractResourceSupport.requireStringArray(
                nativeHardening, NATIVE_HARDENING_WINDOWS_LINKER_FLAGS_KEY)),
        JsonContractResourceSupport.requireStringArray(document, REQUIRED_COMPILE_OPTIONS_KEY),
        JsonContractResourceSupport.requireStringArray(document, FORBIDDEN_COMPILE_OPTIONS_KEY),
        JsonContractResourceSupport.requireBoolean(document, REQUIRES_SECURE_MEMORY_SUPPORT_KEY));
  }

  private static Map<String, String> requireVendoredReleaseFiles(JsonNode document, String key) {
    JsonNode files =
        JsonContractResourceSupport.requireObject(
            document, key, key + " must be one JSON object of vendored release files.");
    if (files.isEmpty()) {
      throw new IllegalArgumentException(key + " must not be empty.");
    }
    Map<String, String> normalized = new ConcurrentHashMap<>();
    files
        .properties()
        .forEach(
            entry ->
                normalized.put(
                    entry.getKey(),
                    JsonContractResourceSupport.requireText(files, entry.getKey())));
    return Map.copyOf(normalized);
  }

  private static ManagedSqliteContract loadCurrent() {
    return loadFromResource(
        ManagedSqliteContracts.class.getResourceAsStream(RESOURCE_PATH), RESOURCE_PATH);
  }
}
