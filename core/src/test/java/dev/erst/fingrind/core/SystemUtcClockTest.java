package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Tests the process-boundary UTC clock seam. */
class SystemUtcClockTest {
  @Test
  void instance_isStableAndUsesUtc() {
    assertSame(SystemUtcClock.instance(), SystemUtcClock.instance());
    assertEquals(ZoneOffset.UTC, SystemUtcClock.instance().getZone());
  }
}
