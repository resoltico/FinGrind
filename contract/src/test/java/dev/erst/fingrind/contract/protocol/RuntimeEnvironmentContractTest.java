package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Coverage and defensive-path tests for the generated runtime-environment contract helper. */
class RuntimeEnvironmentContractTest {
  @Test
  void loadFromResource_rejectsMissingResourceStream() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> RuntimeEnvironmentContract.loadFromResource(null, "/missing.properties"));

    assertEquals(
        "Missing runtime-environment contract resource: /missing.properties",
        exception.getMessage());
  }

  @Test
  void loadFromResource_wrapsIoFailures() {
    UncheckedIOException exception =
        assertThrows(
            UncheckedIOException.class,
            () ->
                RuntimeEnvironmentContract.loadFromResource(
                    new InputStream() {
                      @Override
                      public int read() throws IOException {
                        throw new IOException("boom");
                      }

                      @Override
                      public int read(byte[] buffer, int offset, int length) throws IOException {
                        throw new IOException("boom");
                      }
                    },
                    "/broken.properties"));

    assertEquals(
        "Failed to load runtime-environment contract resource: /broken.properties",
        exception.getMessage());
    assertEquals("boom", exception.getCause().getMessage());
  }

  @Test
  void constructor_rejectsBlankSourceCheckoutJava() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new RuntimeEnvironmentContract("   "));

    assertEquals("sourceCheckoutJava must not be blank.", exception.getMessage());
  }

  @Test
  void loadFromResource_returnsLoadedContract() {
    RuntimeEnvironmentContract contract =
        RuntimeEnvironmentContract.loadFromResource(
            new ByteArrayInputStream("sourceCheckoutJava=26+\n".getBytes(StandardCharsets.UTF_8)),
            "/runtime-environment-contract.properties");

    assertEquals("26+", contract.sourceCheckoutJava());
  }
}
