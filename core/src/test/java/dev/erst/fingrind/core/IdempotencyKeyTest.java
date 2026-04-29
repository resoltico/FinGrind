package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link IdempotencyKey}. */
class IdempotencyKeyTest {
  @Test
  void constructor_trimsValue() {
    IdempotencyKey idempotencyKey = new IdempotencyKey(" post-001 ");

    assertEquals("post-001", idempotencyKey.value());
  }

  @Test
  void constructor_rejectsBlankValue() {
    assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey(""));
  }

  @Test
  void constructor_rejectsOverlongAndInvalidValues() {
    String tooLong = "I" + "1".repeat(IdempotencyKey.maxLength());

    assertEquals("^[A-Za-z0-9](?:[A-Za-z0-9._:/-]{0,127})?$", IdempotencyKey.pattern());
    assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey("idem key"));
    assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey(tooLong));
  }
}
