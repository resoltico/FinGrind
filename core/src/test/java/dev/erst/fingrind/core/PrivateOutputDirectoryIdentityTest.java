package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.attribute.UserPrincipal;
import org.junit.jupiter.api.Test;

/** Verifies invariants of the value that identifies a POSIX-protected directory. */
class PrivateOutputDirectoryIdentityTest {
  private static final UserPrincipal OWNER = () -> "owner";

  @Test
  void posixDirectoryIdentity_rejectsANegativeUnixUserId() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new PrivateOutputDirectory.PosixDirectoryIdentity(OWNER, -1L, false));

    assertEquals("unixUserId must be non-negative.", exception.getMessage());
  }
}
