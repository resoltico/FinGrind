package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression coverage for the capability-baseline synchronization entrypoint. */
class ProtocolCapabilityBaselineSyncMainTest {
  @Test
  void main_requiresExactlyOneDestinationArgument() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ProtocolCapabilityBaselineSyncMain.main(new String[0]));

    assertTrue(
        Objects.requireNonNull(exception.getMessage()).contains("Expected exactly one argument"));
  }

  @Test
  void mainSynchronizesTheRequestedSnapshot(@TempDir Path tempDir) throws IOException {
    Path snapshot = tempDir.resolve("nested/capability-baseline.json");

    ProtocolCapabilityBaselineSyncMain.main(new String[] {snapshot.toString()});

    assertEquals(ProtocolCapabilityBaseline.render(), Files.readString(snapshot));
  }
}
