package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import org.junit.jupiter.api.Test;

/** Direct coverage for canonical-to-local entry-semantics violation adapters. */
class BookkeepingEntrySemanticsViolationSupportTest {
  @Test
  void helpers_copyCanonicalViolationsAndGuardSelectorFacts() {
    PostingRejection.EntrySemanticsViolation canonicalViolation =
        new PostingRejection.EntrySemanticsViolation(
            "source-document-type-not-accepted",
            "entryKind",
            "entryKind 'SALE_ON_CREDIT' does not accept sourceDocumentType 'cash-receipt'.");

    BookkeepingPostingRejection.EntrySemanticsViolation localViolation =
        BookkeepingEntrySemanticsViolationSupport.toLocal(canonicalViolation);

    assertEquals(canonicalViolation.code(), localViolation.code());
    assertEquals(canonicalViolation.field(), localViolation.field());
    assertEquals(canonicalViolation.message(), localViolation.message());
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField("entryKind");
    assertEquals(
        "SALE_ON_CREDIT",
        BookkeepingEntrySemanticsViolationSupport.requireSelectorValue("SALE_ON_CREDIT"));
    assertEquals(
        "selectorField",
        assertThrows(
                NullPointerException.class,
                () ->
                    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(
                        nullOf()))
            .getMessage());
    assertEquals(
        "selectorField must be 'entryKind'.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(
                        "eventKind"))
            .getMessage());
    assertEquals(
        "selectorValue",
        assertThrows(
                NullPointerException.class,
                () -> BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(nullOf()))
            .getMessage());
  }
}
