package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.ClosedFiscalYearRecord;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.SweptInterimResult;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused coverage for replay and rejection branches inside generated close persistence. */
class SqliteClosePostingPersistenceCoverageTest extends SqlitePostingFactStoreTestSupport {
  private static final ReportingPeriod APRIL_2026 =
      new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"));
  private static final ReportingPeriod FISCAL_YEAR_2026 =
      new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
  private static final Instant FIXED_INSTANT = Instant.parse("2026-12-31T23:59:59Z");

  @Test
  void persistInterimResultSweep_reusesReplayPostingWithoutWritingAnotherPostingFact() {
    Path bookPath = tempDirectory.resolve("close-persistence-sweep-replay.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareCloseAccount(
          postingFactStore,
          "3200",
          "Result Holding",
          FinancialPositionLineClassification.RESULT_HOLDING);
      PostingDraft replayDraft =
          generatedPostingDraft(
              "interim-result-sweep",
              "replay-eur",
              PostingKind.INTERIM_RESULT_SWEEP,
              PostingOriginKind.INTERIM_RESULT_SWEEP,
              LocalDate.parse("2026-04-30"),
              List.of(
                  line("2000", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("3200", JournalLine.EntrySide.CREDIT, "10.00")));
      CommittedPosting replayPosting = replayDraft.materialize(new PostingId("sweep-posting-1"));
      assertEquals(
          new PostingCommitResult.Committed(replayPosting, false),
          postingFactStore.commit(replayDraft, replayPosting::postingId));

      SweptInterimResult transferred =
          closePostingPersistence(postingFactStore)
              .persistInterimResultSweep(
                  requireStoreDatabase(postingFactStore),
                  new dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft(
                      APRIL_2026,
                      new AccountCode("3200"),
                      List.of(),
                      FIXED_INSTANT,
                      List.of(replayDraft)),
                  () -> new PostingId("unused-replay"));

      assertEquals(List.of(replayPosting.postingId()), transferred.sweepPostingIds());
      assertEquals(
          1, queryInt(requireStoreDatabase(postingFactStore), "select count(*) from posting_fact"));
      assertEquals(
          1,
          queryInt(
              requireStoreDatabase(postingFactStore), "select count(*) from interim_result_sweep"));
      assertEquals(
          1,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from interim_result_sweep_posting"));
    }
  }

  @Test
  void persistFiscalYearClose_reusesReplayPostingWithoutWritingAnotherPostingFact() {
    Path bookPath = tempDirectory.resolve("close-persistence-fiscal-replay.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareAllCloseTargets(postingFactStore);
      PostingDraft replayDraft =
          generatedPostingDraft(
              "fiscal-year-close",
              "replay-eur",
              PostingKind.FISCAL_YEAR_CLOSE,
              PostingOriginKind.FISCAL_YEAR_CLOSE,
              LocalDate.parse("2026-12-31"),
              List.of(
                  line("3200", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("3300", JournalLine.EntrySide.CREDIT, "10.00")));
      CommittedPosting replayPosting = replayDraft.materialize(new PostingId("fiscal-posting-1"));
      assertEquals(
          new PostingCommitResult.Committed(replayPosting, false),
          postingFactStore.commit(replayDraft, replayPosting::postingId));

      ClosedFiscalYearRecord closedFiscalYear =
          closePostingPersistence(postingFactStore)
              .persistFiscalYearClose(
                  requireStoreDatabase(postingFactStore),
                  new FiscalYearCloseDraft(
                      FISCAL_YEAR_2026,
                      new AccountCode("3000"),
                      new AccountCode("3200"),
                      new AccountCode("3300"),
                      FIXED_INSTANT,
                      null,
                      List.of(replayDraft)),
                  () -> new PostingId("unused-replay"));

      assertEquals(List.of(replayPosting.postingId()), closedFiscalYear.closePostingIds());
      assertEquals(
          1, queryInt(requireStoreDatabase(postingFactStore), "select count(*) from posting_fact"));
      assertEquals(
          1,
          queryInt(
              requireStoreDatabase(postingFactStore), "select count(*) from fiscal_year_close"));
      assertEquals(
          1,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from fiscal_year_close_posting"));
    }
  }

  @Test
  void persistFiscalYearClose_rejectedGeneratedPostingThrowsAcceptanceFailure() {
    Path bookPath = tempDirectory.resolve("close-persistence-fiscal-rejected.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareAllCloseTargets(postingFactStore);
      PostingDraft existingDraft =
          generatedPostingDraft(
              "fiscal-year-close",
              "conflict-eur",
              PostingKind.FISCAL_YEAR_CLOSE,
              PostingOriginKind.FISCAL_YEAR_CLOSE,
              LocalDate.parse("2026-12-31"),
              List.of(
                  line("3200", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("3300", JournalLine.EntrySide.CREDIT, "10.00")));
      CommittedPosting existingPosting =
          existingDraft.materialize(new PostingId("fiscal-posting-existing"));
      assertEquals(
          new PostingCommitResult.Committed(existingPosting, false),
          postingFactStore.commit(existingDraft, existingPosting::postingId));
      PostingDraft conflictingDraft =
          generatedPostingDraft(
              "fiscal-year-close",
              "conflict-eur",
              PostingKind.FISCAL_YEAR_CLOSE,
              PostingOriginKind.FISCAL_YEAR_CLOSE,
              LocalDate.parse("2026-12-31"),
              List.of(
                  line("3200", JournalLine.EntrySide.DEBIT, "11.00"),
                  line("3300", JournalLine.EntrySide.CREDIT, "11.00")));

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  closePostingPersistence(postingFactStore)
                      .persistFiscalYearClose(
                          requireStoreDatabase(postingFactStore),
                          new FiscalYearCloseDraft(
                              FISCAL_YEAR_2026,
                              new AccountCode("3000"),
                              new AccountCode("3200"),
                              new AccountCode("3300"),
                              FIXED_INSTANT,
                              null,
                              List.of(conflictingDraft)),
                          () -> new PostingId("unused-conflict")));

      assertTrue(
          NullTestSupport.messageOf(failure)
              .contains("Generated fiscal year close posting failed bookkeeping acceptance"));
      assertEquals(
          1, queryInt(requireStoreDatabase(postingFactStore), "select count(*) from posting_fact"));
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore), "select count(*) from fiscal_year_close"));
    }
  }

  private static SqliteClosePostingPersistence closePostingPersistence(
      SqlitePostingFactStore postingFactStore) {
    return new SqliteClosePostingPersistence(
        postingFactStore.storeContext(),
        SqliteCommitFaultHook.NONE,
        PostingAcceptancePolicy.currentKernel());
  }

  private static PostingDraft generatedPostingDraft(
      String operationName,
      String token,
      PostingKind postingKind,
      PostingOriginKind postingOriginKind,
      LocalDate effectiveDate,
      List<JournalLine> lines) {
    return new PostingDraft(
        new JournalEntry(effectiveDate, lines),
        dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.direct(),
        postingKind,
        postingOriginKind,
        generatedEvidence(
            token,
            postingKind == PostingKind.FISCAL_YEAR_CLOSE
                ? "year-end-close-plan"
                : "interim-result-sweep-plan"),
        new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
        generatedProvenance(operationName, token));
  }

  private static CommittedProvenance generatedProvenance(String operationName, String token) {
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId("system:" + operationName),
            ActorType.SYSTEM,
            new CommandId(operationName + ":" + token),
            new IdempotencyKey(operationName + ":" + token),
            new CausationId(operationName + ":" + token),
            Optional.of(new CorrelationId(operationName + ":" + token)));
    return new CommittedProvenance(requestProvenance, FIXED_INSTANT, SourceChannel.SYSTEM);
  }

  private static void declareAllCloseTargets(SqlitePostingFactStore postingFactStore) {
    declareCloseAccount(
        postingFactStore,
        "3000",
        "Capital",
        FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
    declareCloseAccount(
        postingFactStore,
        "3200",
        "Result Holding",
        FinancialPositionLineClassification.RESULT_HOLDING);
    declareCloseAccount(
        postingFactStore,
        "3300",
        "Retained Accumulated",
        FinancialPositionLineClassification.RETAINED_ACCUMULATED);
  }

  private static void declareCloseAccount(
      SqlitePostingFactStore postingFactStore,
      String accountCode,
      String accountName,
      FinancialPositionLineClassification classification) {
    assertEquals(
        new AccountDeclarationOutcome.Declared(
            new RegisteredAccount(
                new AccountCode(accountCode),
                new AccountName(accountName),
                AccountType.EQUITY,
                financialPositionTaxonomy(classification),
                true,
                FIXED_INSTANT)),
        postingFactStore.declareAccount(
            new AccountCode(accountCode),
            new AccountName(accountName),
            AccountType.EQUITY,
            financialPositionTaxonomy(classification),
            FIXED_INSTANT));
  }
}
