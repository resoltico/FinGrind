package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostingId}. */
class PostingIdTest {
  @Test
  void constructor_trimsValue() {
    PostingId postingId = new PostingId(" posting-1 ");

    assertEquals("posting-1", postingId.value());
  }

  @Test
  void constructor_rejectsBlankValue() {
    assertThrows(IllegalArgumentException.class, () -> new PostingId(" "));
  }
}
