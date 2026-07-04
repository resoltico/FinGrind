package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.ResolvedJournal;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ClassificationResult;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.EvidenceClass;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.StructuralContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostEntryResult}. */
class PostEntryResultTest {
  @Test
  void preflightAccepted_holdsItsPayload() {
    ResolvedJournal resolvedJournal = resolvedJournal();
    PostEntryResult.PreflightAccepted result =
        new PostEntryResult.PreflightAccepted(
            new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07"), resolvedJournal);
    assertEquals("idem-1", result.idempotencyKey().value());
    assertEquals(resolvedJournal, result.resolvedJournal());
  }

  @Test
  void committed_holdsItsPayload() {
    ResolvedJournal resolvedJournal = resolvedJournal();
    PostEntryResult.Committed result =
        new PostEntryResult.Committed(
            new PostingId("posting-1"),
            new IdempotencyKey("idem-1"),
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            false,
            resolvedJournal);
    assertEquals("posting-1", result.postingId().value());
    assertFalse(result.idempotentReplay());
    assertEquals(resolvedJournal, result.resolvedJournal());
  }

  @Test
  void preflightRejected_holdsTypedRejection() {
    PostEntryResult.PreflightRejected result =
        new PostEntryResult.PreflightRejected(
            new IdempotencyKey("idem-1"), new PostingRejection.IdempotencyKeyConflict());
    assertEquals(new PostingRejection.IdempotencyKeyConflict(), result.rejection());
  }

  @Test
  void commitRejected_rejectsNullRejection() {
    assertThrows(
        NullPointerException.class,
        () -> new PostEntryResult.CommitRejected(new IdempotencyKey("idem-1"), nullOf()));
  }

  private static ResolvedJournal resolvedJournal() {
    return new ResolvedJournal(
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "10.00")),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "10.00")))),
        null,
        null,
        new ClassificationResult(
            EconomicEventClass.SETTLED_SALE,
            Set.of(),
            Set.of(EconomicEventClass.SETTLED_SALE),
            true,
            EvidenceClass.CASH_SETTLEMENT,
            StructuralContext.ordinary()));
  }
}
