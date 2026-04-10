package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ReversalReference}. */
class ReversalReferenceTest {
  @Test
  void constructor_acceptsValidReference() {
    ReversalReference reversalReference = new ReversalReference(new PostingId("posting-1"));

    assertEquals(new PostingId("posting-1"), reversalReference.priorPostingId());
  }

  @Test
  void constructor_rejectsNullPostingId() {
    assertThrows(NullPointerException.class, () -> new ReversalReference(null));
  }
}
