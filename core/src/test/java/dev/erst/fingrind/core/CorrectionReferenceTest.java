package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link CorrectionReference}. */
class CorrectionReferenceTest {
  @Test
  void constructor_acceptsValidReference() {
    CorrectionReference correctionReference =
        new CorrectionReference(
            CorrectionReference.CorrectionKind.REVERSAL, new PostingId("posting-1"));

    assertEquals(CorrectionReference.CorrectionKind.REVERSAL, correctionReference.kind());
  }

  @Test
  void constructor_rejectsNullPostingId() {
    assertThrows(
        NullPointerException.class,
        () -> new CorrectionReference(CorrectionReference.CorrectionKind.AMENDMENT, null));
  }
}
