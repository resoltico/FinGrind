package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Tests the no-overwrite precondition before a journal reserves a generated-secret target. */
class SqliteGeneratedSecretTargetTest extends SqliteNativeBridgeTestSupport {
  @Test
  void requireAbsent_refusesAnOccupiedTargetWithoutChangingIt() throws Exception {
    Path targetPath = Files.writeString(tempDirectory.resolve("occupied.key"), "occupied-secret");

    SqliteGeneratedSecretTargetOccupiedException exception =
        assertThrows(
            SqliteGeneratedSecretTargetOccupiedException.class,
            () -> SqliteGeneratedSecretTarget.requireAbsent(targetPath));

    assertEquals(targetPath, exception.targetPath());
    assertEquals("occupied-secret", Files.readString(targetPath));
  }

  @Test
  void requireAbsent_acceptsAnAbsentTargetWithoutCreatingIt() {
    Path targetPath = tempDirectory.resolve("absent.key");

    assertDoesNotThrow(() -> SqliteGeneratedSecretTarget.requireAbsent(targetPath));
  }
}
