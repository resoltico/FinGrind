package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.BalanceSide;
import org.junit.jupiter.api.Test;

/** Unit tests for exact SQLite-backed balance arithmetic helpers. */
class SqliteBalanceMathTest {
  @Test
  void absoluteMinorUnits_rejectsOverflow() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteBalanceMath.absoluteMinorUnits(Long.MIN_VALUE));
    assertEquals(
        "SQLite running balance exceeded the supported exact money range.", exception.getMessage());
    assertEquals(BalanceSide.ZERO, SqliteBalanceMath.balanceSide(0L));
  }
}
