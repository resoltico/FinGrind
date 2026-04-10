package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Covers boundary validation for core text-backed semantic value objects. */
class CoreTextValueObjectsTest {
  @Test
  void actorId_stripsWhitespaceAndRejectsBlank() {
    assertEquals("actor-1", new ActorId("  actor-1  ").value());
    assertThrows(IllegalArgumentException.class, () -> new ActorId("   "));
  }

  @Test
  void causationId_stripsWhitespaceAndRejectsBlank() {
    assertEquals("cause-1", new CausationId("  cause-1  ").value());
    assertThrows(IllegalArgumentException.class, () -> new CausationId("   "));
  }

  @Test
  void commandId_stripsWhitespaceAndRejectsBlank() {
    assertEquals("command-1", new CommandId("  command-1  ").value());
    assertThrows(IllegalArgumentException.class, () -> new CommandId("   "));
  }

  @Test
  void correlationId_stripsWhitespaceAndRejectsBlank() {
    assertEquals("corr-1", new CorrelationId("  corr-1  ").value());
    assertThrows(IllegalArgumentException.class, () -> new CorrelationId("   "));
  }

  @Test
  void correctionReason_stripsWhitespaceAndRejectsBlank() {
    assertEquals("operator correction", new CorrectionReason("  operator correction  ").value());
    assertThrows(IllegalArgumentException.class, () -> new CorrectionReason("   "));
  }
}
