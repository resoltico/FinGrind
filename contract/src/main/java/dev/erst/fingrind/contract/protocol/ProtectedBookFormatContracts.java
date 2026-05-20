package dev.erst.fingrind.contract.protocol;

import java.io.InputStream;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Loads and publishes the current persisted protected-book format contract snapshot. */
final class ProtectedBookFormatContracts {
  private static final ProtocolContractSchemaKeys.ProtectedBookFormat SCHEMA_KEYS =
      ProtocolContractSchemaKeys.current().protectedBookFormat();
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/protected-book-format-contract.json";
  private static final String APPLICATION_ID_KEY = SCHEMA_KEYS.applicationId();
  private static final String FORMAT_VERSION_KEY = SCHEMA_KEYS.formatVersion();
  private static final String CIPHER_KEY = SCHEMA_KEYS.cipher();
  private static final String LEGACY_MODE_KEY = SCHEMA_KEYS.legacyMode();
  private static final String PAGE_SIZE_KEY = SCHEMA_KEYS.pageSize();
  private static final String RESERVED_BYTES_KEY = SCHEMA_KEYS.reservedBytes();
  private static final String LEGACY_PAGE_SIZE_KEY = SCHEMA_KEYS.legacyPageSize();
  private static final String KDF_ITER_KEY = SCHEMA_KEYS.kdfIter();
  private static final String PLAINTEXT_HEADER_SIZE_KEY = SCHEMA_KEYS.plaintextHeaderSize();
  private static final ProtectedBookFormatContract CURRENT = loadCurrent();

  private ProtectedBookFormatContracts() {}

  static ProtectedBookFormatContract current() {
    return CURRENT;
  }

  static ProtectedBookFormatContract loadFromResource(
      @Nullable InputStream resourceStream, String resourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath");
    JsonNode document =
        JsonContractResourceSupport.loadObject(
            resourceStream, resourcePath, "protected-book format contract");
    return new ProtectedBookFormatContract(
        JsonContractResourceSupport.requireInt(document, APPLICATION_ID_KEY),
        JsonContractResourceSupport.requireInt(document, FORMAT_VERSION_KEY),
        BookCipher.fromWireValue(JsonContractResourceSupport.requireText(document, CIPHER_KEY)),
        JsonContractResourceSupport.requireBoolean(document, LEGACY_MODE_KEY),
        JsonContractResourceSupport.requireInt(document, PAGE_SIZE_KEY),
        JsonContractResourceSupport.requireInt(document, RESERVED_BYTES_KEY),
        JsonContractResourceSupport.requireInt(document, LEGACY_PAGE_SIZE_KEY),
        JsonContractResourceSupport.requireInt(document, KDF_ITER_KEY),
        JsonContractResourceSupport.requireInt(document, PLAINTEXT_HEADER_SIZE_KEY));
  }

  private static ProtectedBookFormatContract loadCurrent() {
    return loadFromResource(
        ProtectedBookFormatContracts.class.getResourceAsStream(RESOURCE_PATH), RESOURCE_PATH);
  }
}
