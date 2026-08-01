package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Exact coverage for immutable coordination-control geometry and identity rules. */
class SqliteCoordinationControlProtocolTest {
  @Test
  void validatesSlotsLockRangesAndBoundedImmutableMagic() {
    assertEquals(
        SqliteCoordinationControlProtocol.CONTROL_LOCK_BASE,
        SqliteCoordinationControlProtocol.activitySlotPosition(0));
    assertEquals(
        SqliteCoordinationControlProtocol.CONTROL_LOCK_BASE + 7L,
        SqliteCoordinationControlProtocol.activitySlotPosition(7));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteCoordinationControlProtocol.activitySlotPosition(-1));

    assertEquals(
        SqliteCoordinationControlProtocol.CONTROL_LOCK_BASE,
        SqliteCoordinationControlProtocol.maintenanceLockPosition());
    assertEquals(
        Long.MAX_VALUE - SqliteCoordinationControlProtocol.CONTROL_LOCK_BASE,
        SqliteCoordinationControlProtocol.maintenanceLockLength());
    SqliteCoordinationControlProtocol.requireLockRange(
        SqliteCoordinationControlProtocol.maintenanceLockPosition(), 1L);
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteCoordinationControlProtocol.requireLockRange(0L, 1L));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteCoordinationControlProtocol.requireLockRange(
                SqliteCoordinationControlProtocol.maintenanceLockPosition(), 0L));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteCoordinationControlProtocol.requireLockRange(Long.MAX_VALUE, 1L));

    byte[] magic = SqliteCoordinationControlProtocol.magic("control", "binding");
    assertArrayEquals("control:binding\n".getBytes(StandardCharsets.US_ASCII), magic);
    byte[] checkedMagic = SqliteCoordinationControlProtocol.checkedMagic(magic);
    magic[0] = 'x';
    assertArrayEquals("control:binding\n".getBytes(StandardCharsets.US_ASCII), checkedMagic);
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteCoordinationControlProtocol.checkedMagic(new byte[0]));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteCoordinationControlProtocol.checkedMagic(
                new byte[(int) SqliteCoordinationControlProtocol.CONTROL_LOCK_BASE]));
  }

  @Test
  void bindsCanonicalDirectoriesAndSelectsTheWindowsTransportOnlyForWindows() {
    String canonicalBinding =
        SqliteCoordinationControlProtocol.canonicalDirectoryBinding(
            Path.of("books").toAbsolutePath());

    assertEquals(64, canonicalBinding.length());
    assertTrue(canonicalBinding.matches("[0-9a-f]{64}"));
    assertEquals(
        canonicalBinding,
        SqliteCoordinationControlProtocol.canonicalDirectoryBinding(
            Path.of("books").toAbsolutePath().normalize()));
    assertTrue(SqliteCoordinationControlProtocol.isWindows("Windows 11"));
    assertFalse(SqliteCoordinationControlProtocol.isWindows("Mac OS X"));
    assertFalse(SqliteCoordinationControlProtocol.isWindows("Linux"));
    assertEquals(
        SqliteCoordinationControlProtocol.isWindows(System.getProperty("os.name", "")),
        SqliteCoordinationControlProtocol.isWindows());
  }
}
