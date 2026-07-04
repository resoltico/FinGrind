package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage tests for reversal journal derivation support. */
class ReversalResolutionSupportTest {
  @Test
  void resolve_returnsResolvedReversalUnchanged() {
    BookkeepingEntry.Reversal resolvedReversal =
        PostingApplicationServiceTestSupport.resolvedReversalEntry(
            "posting-1",
            "full reversal",
            PostingApplicationServiceTestSupport.reversalJournalEntry());

    BookkeepingEntry resolved =
        ReversalResolutionSupport.resolve(
            resolvedReversal, new PostingValidationStoreDouble(Map.of()));

    assertSame(resolvedReversal, resolved);
  }

  @Test
  void unresolvedReversal_reportsMissingTargetBeforeAndDuringResolution() {
    BookkeepingEntry.Reversal unresolvedReversal =
        new BookkeepingEntry.Reversal(
            LocalDate.parse("2026-04-07"),
            new PostingLineage.Reversal(
                new ReversalReference(new PostingId("posting-missing")),
                new ReversalReason("operator reversal")),
            null,
            null);
    PostingValidationStoreDouble emptyBook = new PostingValidationStoreDouble(Map.of());

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.ReversalTargetNotFound(
                new PostingId("posting-missing"))),
        ReversalResolutionSupport.rejectionFor(unresolvedReversal, emptyBook));

    IllegalStateException missingTarget =
        assertThrows(
            IllegalStateException.class,
            () -> ReversalResolutionSupport.resolve(unresolvedReversal, emptyBook));
    assertEquals(
        "Reversal target posting-missing must exist before translation.",
        missingTarget.getMessage());
  }
}
