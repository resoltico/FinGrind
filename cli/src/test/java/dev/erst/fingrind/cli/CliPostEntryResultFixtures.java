package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
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

/** Shared CLI-test fixtures for post-entry results that now require resolved-journal payloads. */
final class CliPostEntryResultFixtures {
  private static final ResolvedJournal RESOLVED_JOURNAL =
      new ResolvedJournal(
          new JournalEntry(
              LocalDate.parse("2026-04-07"),
              List.of(
                  new JournalLine(
                      new AccountCode("1000"),
                      JournalLine.EntrySide.DEBIT,
                      Money.parse("EUR", "12.10")),
                  new JournalLine(
                      new AccountCode("4000"),
                      JournalLine.EntrySide.CREDIT,
                      Money.parse("EUR", "10.00")),
                  new JournalLine(
                      new AccountCode("2100"),
                      JournalLine.EntrySide.CREDIT,
                      Money.parse("EUR", "2.10")))),
          null,
          null,
          new ClassificationResult(
              EconomicEventClass.SETTLED_SALE,
              Set.of(),
              Set.of(EconomicEventClass.SETTLED_SALE),
              true,
              EvidenceClass.CASH_SETTLEMENT,
              StructuralContext.ordinary()));
  private static final AttestationCommit ATTESTATION_COMMIT =
      new AttestationCommit(java.math.BigInteger.ONE, "a".repeat(64));

  private CliPostEntryResultFixtures() {}

  static PostEntryResult.PreflightAccepted preflightAccepted(
      IdempotencyKey idempotencyKey, LocalDate effectiveDate) {
    return new PostEntryResult.PreflightAccepted(idempotencyKey, effectiveDate, RESOLVED_JOURNAL);
  }

  static PostEntryResult.Committed committed(
      PostingId postingId,
      IdempotencyKey idempotencyKey,
      LocalDate effectiveDate,
      Instant recordedAt,
      boolean duplicate) {
    return new PostEntryResult.Committed(
        postingId,
        idempotencyKey,
        effectiveDate,
        recordedAt,
        duplicate,
        RESOLVED_JOURNAL,
        duplicate ? null : ATTESTATION_COMMIT);
  }

  static ResolvedJournal resolvedJournal() {
    return RESOLVED_JOURNAL;
  }
}
