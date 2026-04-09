package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliMetadata}. */
class CliMetadataTest {
  @Test
  void constructor_loadsPackagedMetadata() {
    CliMetadata metadata = new CliMetadata();

    assertEquals("FinGrind", metadata.applicationName());
    assertEquals("0.1.0", metadata.version());
    assertEquals(
        "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence",
        metadata.description());
  }

  @Test
  void versionFrom_returnsImplementationVersion_whenPresent() {
    assertEquals("1.2.3", CliMetadata.versionFrom("1.2.3", "0.1.0"));
  }

  @Test
  void versionFrom_returnsFallbackVersion_whenImplementationVersionIsAbsent() {
    assertEquals("0.1.0", CliMetadata.versionFrom(null, "0.1.0"));
  }

  @Test
  void versionFrom_returnsFallbackVersion_whenImplementationVersionIsBlank() {
    assertEquals("0.1.0", CliMetadata.versionFrom("   ", "0.1.0"));
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
}
