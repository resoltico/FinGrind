package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Coverage and defensive-path tests for the runtime-surface contract loader. */
class RuntimeSurfaceContractsTest {
  @Test
  void loadFromResource_rejectsMissingStream() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> RuntimeSurfaceContracts.loadFromResource(null, "/missing.json"));

    assertEquals(
        "Missing runtime surface contract resource: /missing.json", exception.getMessage());
  }

  @Test
  void loadFromResource_wrapsIoFailures() {
    UncheckedIOException exception =
        assertThrows(
            UncheckedIOException.class,
            () -> RuntimeSurfaceContracts.loadFromResource(failingInputStream(), "/broken.json"));

    assertEquals(
        "Failed to load runtime surface contract resource: /broken.json", exception.getMessage());
    assertEquals("boom", Objects.requireNonNull(exception.getCause()).getMessage());
  }

  @Test
  void loadFromResource_rejectsBlankRequiredTextValues() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                RuntimeSurfaceContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "directJavaRuntimeDistribution": "direct-java-invocation",
                          "sourceCheckoutRuntimeDistribution": "source-checkout-gradle",
                          "containerRuntimeDistribution": "container-image",
                          "bundleRuntimeDistribution": "self-contained-bundle",
                          "publicCliDistribution": "self-contained-bundle",
                          "storageDriver": "sqlite-ffm-sqlite3mc",
                          "storageEngine": "sqlite",
                          "bookProtectionMode": "required",
                          "defaultBookCipher": "chacha20",
                          "sqliteLibraryMode": "managed-only",
                          "sqliteBundleHomeSystemProperty": ""
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/blank.json"));

    assertEquals(
        "sqliteBundleHomeSystemProperty must be a non-blank JSON string.", exception.getMessage());
  }

  @Test
  void loadFromResource_rejectsNonTextualRequiredValues() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                RuntimeSurfaceContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "directJavaRuntimeDistribution": 1,
                          "sourceCheckoutRuntimeDistribution": "source-checkout-gradle",
                          "containerRuntimeDistribution": "container-image",
                          "bundleRuntimeDistribution": "self-contained-bundle",
                          "publicCliDistribution": "self-contained-bundle",
                          "storageDriver": "sqlite-ffm-sqlite3mc",
                          "storageEngine": "sqlite",
                          "bookProtectionMode": "required",
                          "defaultBookCipher": "chacha20",
                          "sqliteLibraryMode": "managed-only",
                          "sqliteBundleHomeSystemProperty": "fingrind.bundle.home"
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/invalid-runtime-surface-contract.json"));

    assertEquals(
        "directJavaRuntimeDistribution must be a non-blank JSON string.", exception.getMessage());
  }

  @Test
  void loadFromResource_returnsTypedContract() {
    RuntimeSurfaceContract contract =
        RuntimeSurfaceContracts.loadFromResource(
            new ByteArrayInputStream(
                """
                {
                  "directJavaRuntimeDistribution": "direct-java-invocation",
                  "sourceCheckoutRuntimeDistribution": "source-checkout-gradle",
                  "containerRuntimeDistribution": "container-image",
                  "bundleRuntimeDistribution": "self-contained-bundle",
                  "publicCliDistribution": "self-contained-bundle",
                  "storageDriver": "sqlite-ffm-sqlite3mc",
                  "storageEngine": "sqlite",
                  "bookProtectionMode": "required",
                  "defaultBookCipher": "chacha20",
                  "sqliteLibraryMode": "managed-only",
                  "sqliteBundleHomeSystemProperty": "fingrind.bundle.home"
                }
                """
                    .getBytes(StandardCharsets.UTF_8)),
            "/runtime-surface-contract.json");

    assertEquals(
        RuntimeDistribution.DIRECT_JAVA_INVOCATION, contract.directJavaRuntimeDistribution());
    assertEquals(
        RuntimeDistribution.SOURCE_CHECKOUT_GRADLE, contract.sourceCheckoutRuntimeDistribution());
    assertEquals(RuntimeDistribution.CONTAINER_IMAGE, contract.containerRuntimeDistribution());
    assertEquals(RuntimeDistribution.SELF_CONTAINED_BUNDLE, contract.bundleRuntimeDistribution());
    assertEquals(PublicCliDistribution.SELF_CONTAINED_BUNDLE, contract.publicCliDistribution());
    assertEquals(StorageDriver.SQLITE_FFM_SQLITE3MC, contract.storageDriver());
    assertEquals(StorageEngine.SQLITE, contract.storageEngine());
    assertEquals(BookProtectionMode.REQUIRED, contract.bookProtectionMode());
    assertEquals(BookCipher.CHACHA20, contract.defaultBookCipher());
    assertEquals(SqliteLibraryMode.MANAGED_ONLY, contract.sqliteLibraryMode());
    assertEquals("fingrind.bundle.home", contract.sqliteBundleHomeSystemProperty());
  }

  private static InputStream failingInputStream() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("boom");
      }

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
        throw new IOException("boom");
      }
    };
  }
}
