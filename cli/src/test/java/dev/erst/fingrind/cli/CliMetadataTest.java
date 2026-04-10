package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliMetadata}. */
class CliMetadataTest {
  @Test
  void constructor_loadsPackagedMetadata() {
    CliMetadata metadata = new CliMetadata();
    Properties packagedProperties = loadPackagedProperties();

    assertEquals("FinGrind", metadata.applicationName());
    assertEquals(packagedProperties.getProperty("version"), metadata.version());
    assertTrue(
        metadata.version().matches("\\d+\\.\\d+\\.\\d+"),
        "packaged metadata version should be a concrete semantic version");
    assertEquals(
        "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence",
        metadata.description());
  }

  @Test
  void versionFrom_returnsImplementationVersion_whenPresent() {
    assertEquals("1.2.3", CliMetadata.versionFrom("1.2.3", "0.3.0"));
  }

  @Test
  void versionFrom_returnsFallbackVersion_whenImplementationVersionIsAbsent() {
    assertEquals("0.3.0", CliMetadata.versionFrom(null, "0.3.0"));
  }

  @Test
  void versionFrom_returnsFallbackVersion_whenImplementationVersionIsBlank() {
    assertEquals("0.3.0", CliMetadata.versionFrom("   ", "0.3.0"));
  }

  @Test
  void versionFrom_returnsUnknown_whenBothSourcesAreAbsent() {
    assertEquals("unknown", CliMetadata.versionFrom(null, "   "));
  }

  @Test
  void versionFrom_returnsUnknown_whenFallbackVersionIsNull() {
    assertEquals("unknown", CliMetadata.versionFrom("   ", null));
  }

  @Test
  void constructor_mapsIoFailuresToIllegalStateException() {
    try (InputStream brokenStream =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("boom");
          }

          @Override
          public int read(byte[] bytes, int offset, int length) throws IOException {
            throw new IOException("boom");
          }
        }) {

      assertThrows(IllegalStateException.class, () -> new CliMetadata(brokenStream));
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static Properties loadPackagedProperties() {
    try (InputStream metadataStream =
        CliMetadata.class.getResourceAsStream("/fingrind.properties")) {
      Properties properties = new Properties();
      properties.load(metadataStream);
      return properties;
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
