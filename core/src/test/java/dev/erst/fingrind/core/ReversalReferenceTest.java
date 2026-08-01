package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ReversalReference}. */
class ReversalReferenceTest {
  @Test
  void constructor_acceptsValidReference() {
    ReversalReference reversalReference =
        new ReversalReference(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"));
    assertEquals(
        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"), reversalReference.priorPostingId());
  }

  @Test
  @org.jspecify.annotations.NullUnmarked
  void constructor_rejectsNullPostingId() {
    assertThrows(NullPointerException.class, () -> new ReversalReference(null));
  }
}
