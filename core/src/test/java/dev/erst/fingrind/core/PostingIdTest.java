package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostingId}. */
class PostingIdTest {
  @Test
  void constructor_trimsValue() {
    PostingId postingId = new PostingId("  41c39b6b-2521-30ba-8e89-2debaaba6fdc  ");

    assertEquals("41c39b6b-2521-30ba-8e89-2debaaba6fdc", postingId.value());
  }

  @Test
  void constructor_rejectsBlankValue() {
    assertThrows(IllegalArgumentException.class, () -> new PostingId(" "));
  }
}
