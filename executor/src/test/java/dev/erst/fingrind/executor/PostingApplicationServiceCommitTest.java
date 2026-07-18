package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.FIXED_CLOCK;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.applicationService;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.command;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.commitRejected;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.conflictingStoredPosting;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.declareDefaultAccounts;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.declareLatviaVatRegistration;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.declareNonCashDirectJournalAccounts;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.declareTaxAccounts;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.existingPosting;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.mappedOutcomeBookSession;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.requestProvenance;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.reversalJournalEntry;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.reversalReference;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.taxedSaleCommand;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingEffectiveDateBeforeBookStart;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests covering commit behavior in {@link PostingApplicationService}. */
class PostingApplicationServiceCommitTest {
  @Test
  void commit_returnsCommittedWhenRequestIsAdmissible() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result = applicationService.commit(command("idem-1"));

      assertCommitted(result, "posting-new", "idem-1");
    }
  }

  @Test
  void commit_returnsResolvedJournalForTaxedSettledSale() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      declareTaxAccounts(bookSession);
      declareLatviaVatRegistration(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result = applicationService.commit(taxedSaleCommand("idem-taxed-sale"));

      PostEntryResult.Committed committed =
          assertInstanceOf(PostEntryResult.Committed.class, result);
      var appliedTax = committed.resolvedJournal().appliedTax();
      assertNotNull(appliedTax);
      assertEquals(new PostingId("posting-new"), committed.postingId());
      assertEquals(
          EconomicEventClass.SETTLED_SALE,
          committed.resolvedJournal().classification().eventClass());
      assertEquals("2100", appliedTax.taxAmount().minorUnits());
      assertEquals(
          new AccountCode("2100"),
          committed.resolvedJournal().expandedLines().lines().get(2).accountCode());
    }
  }

  @Test
  void commit_rejectsBookNotInitializedBeforeGeneratingPostingId() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      PostingApplicationService applicationService =
          new PostingApplicationService(
              bookSession,
              bookSession,
              () -> {
                throw new AssertionError("postingIdGenerator should not be called");
              },
              FIXED_CLOCK);

      PostEntryResult result = applicationService.commit(command("idem-1"));

      assertEquals(
          commitRejected(new IdempotencyKey("idem-1"), new PostingRejection.BookNotInitialized()),
          result);
    }
  }

  @Test
  void commit_rejectsEffectiveDatesBeforeImmutableBookStartBeforeGeneratingPostingId() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService =
          new PostingApplicationService(
              bookSession,
              bookSession,
              () -> {
                throw new AssertionError("postingIdGenerator should not be called");
              },
              FIXED_CLOCK);
      PostEntryCommand command =
          new PostEntryCommand(
              new BookkeepingEntry.SaleSettled(
                  LocalDate.parse("2025-12-31"),
                  new AccountCode("1000"),
                  new AccountCode("2000"),
                  MonetaryAmount.of(Money.parse("EUR", "10.00")),
                  null,
                  null,
                  null,
                  null,
                  null),
              generatedEvidence("idem-before-book-start", "cash-receipt"),
              requestProvenance("idem-before-book-start"),
              SourceChannel.CLI);

      PostEntryResult result = applicationService.commit(command);

      assertEquals(
          commitRejected(
              new IdempotencyKey("idem-before-book-start"),
              new PostingEffectiveDateBeforeBookStart(
                  LocalDate.parse("2025-12-31"), LocalDate.parse("2026-01-01"))),
          result);
    }
  }

  @Test
  void commit_returnsCommittedForValidReversal() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(existingPosting("posting-1", "idem-original"));
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result =
          applicationService.commit(
              command(
                  "idem-1",
                  reversalReference("posting-1"),
                  Optional.of(new ReversalReason("full reversal")),
                  reversalJournalEntry()));

      assertCommitted(result, "posting-new", "idem-1");
    }
  }

  @Test
  void commit_rejectsTypedEntryWhenAccountsAndEvidenceContradictEntryKind() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);
      PostEntryCommand command =
          new PostEntryCommand(
              new BookkeepingEntry.SaleSettled(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("2000"),
                  new AccountCode("1000"),
                  MonetaryAmount.of(Money.parse("EUR", "10.00")),
                  null,
                  null,
                  null,
                  null,
                  null),
              generatedEvidence("idem-semantics", "invoice"),
              requestProvenance("idem-semantics"),
              SourceChannel.CLI);

      PostEntryResult result = applicationService.commit(command);

      assertEquals(
          commitRejected(
              new IdempotencyKey("idem-semantics"),
              new PostingRejection.EntrySemanticsViolations(
                  List.of(
                      PostingRejectionSemantics.accountTypeMismatch(
                          BookkeepingEntryKind.SALE_SETTLED.wireValue(),
                          "cashAccountCode",
                          new AccountCode("2000"),
                          AccountType.ASSET,
                          AccountType.REVENUE),
                      PostingRejectionSemantics.cashFlowAssetClassificationMismatch(
                          BookkeepingEntryKind.SALE_SETTLED.wireValue(),
                          "cashAccountCode",
                          new AccountCode("2000"),
                          dev.erst.fingrind.core.CashFlowAssetClassification
                              .CASH_AND_CASH_EQUIVALENT,
                          null),
                      PostingRejectionSemantics.accountTypeMismatch(
                          BookkeepingEntryKind.SALE_SETTLED.wireValue(),
                          "revenueAccountCode",
                          new AccountCode("1000"),
                          AccountType.REVENUE,
                          AccountType.ASSET),
                      PostingRejectionSemantics.sourceDocumentTypeNotAccepted(
                          BookkeepingEntryKind.SALE_SETTLED.wireValue(),
                          new dev.erst.fingrind.core.SourceDocumentType("invoice"),
                          List.of("cash-receipt", "bank-deposit", "card-settlement"))))),
          result);
    }
  }

  @Test
  void commit_rejectsEconomicallyNullDirectJournalsBeforeDurableWriteExecution() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);
      PostEntryCommand command =
          new PostEntryCommand(
              new BookkeepingEntry.DirectJournal(
                  new dev.erst.fingrind.core.JournalEntry(
                      LocalDate.parse("2026-04-07"),
                      List.of(
                          new dev.erst.fingrind.core.JournalLine(
                              new AccountCode("1000"),
                              dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                              Money.parse("EUR", "10.00")),
                          new dev.erst.fingrind.core.JournalLine(
                              new AccountCode("2000"),
                              dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                              Money.parse("EUR", "10.00")),
                          new dev.erst.fingrind.core.JournalLine(
                              new AccountCode("2000"),
                              dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                              Money.parse("EUR", "10.00")),
                          new dev.erst.fingrind.core.JournalLine(
                              new AccountCode("1000"),
                              dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                              Money.parse("EUR", "10.00")))),
                  null),
              generatedEvidence("idem-economic-null", "operator-note"),
              requestProvenance("idem-economic-null"),
              SourceChannel.CLI);

      PostEntryResult result = applicationService.commit(command);

      assertEquals(
          commitRejected(
              new IdempotencyKey("idem-economic-null"),
              new PostingRejection.EntrySemanticsViolations(
                  List.of(
                      PostingRejectionSemantics.economicNullJournal(
                          BookkeepingEntryKind.DIRECT_JOURNAL.wireValue())))),
          result);
    }
  }

  @Test
  void commit_rejectsDirectJournalsThatNeverTouchDeclaredCashAccounts() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareNonCashDirectJournalAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);
      PostEntryCommand command =
          new PostEntryCommand(
              new BookkeepingEntry.DirectJournal(
                  new dev.erst.fingrind.core.JournalEntry(
                      LocalDate.parse("2026-04-07"),
                      List.of(
                          new dev.erst.fingrind.core.JournalLine(
                              new AccountCode("3000"),
                              dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                              Money.parse("EUR", "10.00")),
                          new dev.erst.fingrind.core.JournalLine(
                              new AccountCode("3200"),
                              dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                              Money.parse("EUR", "10.00")))),
                  null),
              generatedEvidence("idem-non-cash-direct-journal", "operator-note"),
              requestProvenance("idem-non-cash-direct-journal"),
              SourceChannel.CLI);

      PostEntryResult result = applicationService.commit(command);

      assertEquals(
          commitRejected(
              new IdempotencyKey("idem-non-cash-direct-journal"),
              new PostingRejection.EntrySemanticsViolations(
                  List.of(
                      PostingRejectionSemantics.rawJournalRequiresCashLine(
                          BookkeepingEntryKind.DIRECT_JOURNAL.wireValue())))),
          result);
    }
  }

  @Test
  void commit_mapsOrdinaryBookSessionOutcomes() {
    PostingApplicationServiceTestSupport.PostingBookSession bookSession =
        mappedOutcomeBookSession();
    PostingApplicationService applicationService = applicationService(bookSession);

    assertEquals(
        commitRejected(
            new IdempotencyKey("idem-book-not-initialized"),
            new PostingRejection.BookNotInitialized()),
        applicationService.commit(command("idem-book-not-initialized")));
    assertEquals(
        commitRejected(
            new IdempotencyKey("idem-unknown-account"),
            new PostingRejection.AccountStateViolations(
                List.of(new PostingRejection.UnknownAccount(new AccountCode("1000"))))),
        applicationService.commit(command("idem-unknown-account")));
    assertEquals(
        commitRejected(
            new IdempotencyKey("idem-inactive-account"),
            new PostingRejection.AccountStateViolations(
                List.of(new PostingRejection.InactiveAccount(new AccountCode("1000"))))),
        applicationService.commit(command("idem-inactive-account")));
    assertEquals(
        commitRejected(
            new IdempotencyKey("idem-duplicate"), new PostingRejection.IdempotencyKeyConflict()),
        applicationService.commit(command("idem-duplicate")));
    assertEquals(
        commitRejected(
            new IdempotencyKey("idem-reversal-duplicate"),
            new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))),
        applicationService.commit(
            command(
                "idem-reversal-duplicate",
                reversalReference("posting-1"),
                Optional.of(new ReversalReason("full reversal")),
                reversalJournalEntry())));
    assertEquals(
        commitRejected(
            new IdempotencyKey("idem-reversal-target-is-reversal"),
            new dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal(
                new PostingId("posting-2"))),
        applicationService.commit(
            command(
                "idem-reversal-target-is-reversal",
                reversalReference("posting-1"),
                Optional.of(new ReversalReason("full reversal")),
                reversalJournalEntry())));
  }

  @Test
  void commit_rejectsReversalOfReversalAgainstLiveBookState() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);
      PostEntryResult.Committed originalCommitted =
          assertInstanceOf(
              PostEntryResult.Committed.class, applicationService.commit(command("idem-original")));
      PostEntryResult.Committed reversalCommitted =
          assertInstanceOf(
              PostEntryResult.Committed.class,
              applicationService.commit(
                  command(
                      "idem-reversal",
                      Optional.of(new ReversalReference(originalCommitted.postingId())),
                      Optional.of(new ReversalReason("full reversal")),
                      reversalJournalEntry())));

      PostEntryResult result =
          applicationService.commit(
              command(
                  "idem-reversal-of-reversal",
                  Optional.of(new ReversalReference(reversalCommitted.postingId())),
                  Optional.of(new ReversalReason("redo by reversal")),
                  originalCommitted.resolvedJournal().expandedLines()));

      assertEquals(
          commitRejected(
              new IdempotencyKey("idem-reversal-of-reversal"),
              new ReversalTargetIsReversal(reversalCommitted.postingId())),
          result);
    }
  }

  @Test
  void commit_rejectsDeterministicDuplicateIdempotencyBeforeCommitStoreRuns() {
    PostingApplicationServiceTestSupport.PostingBookSession bookSession =
        new PostingApplicationServiceTestSupport.DelegatingPostingBookSession() {
          @Override
          public BookLifecycleInspection inspectBook() {
            return initializedLifecycleInspection(1001, 1, 1, FIXED_CLOCK.instant());
          }

          @Override
          public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
            return Optional.of(
                registeredAccount(
                    accountCode,
                    new AccountName("Synthetic"),
                    "1000".equals(accountCode.value()) ? AccountType.ASSET : AccountType.REVENUE,
                    "1000".equals(accountCode.value()) ? NormalBalance.DEBIT : NormalBalance.CREDIT,
                    true,
                    FIXED_CLOCK.instant()));
          }

          @Override
          public Optional<dev.erst.fingrind.executor.spi.StoredRequestPosting> findExistingPosting(
              IdempotencyKey idempotencyKey) {
            return Optional.of(
                conflictingStoredPosting("posting-existing", idempotencyKey.value()));
          }

          @Override
          public PostingCommitResult commit(
              dev.erst.fingrind.executor.spi.PostingDraft postingDraft,
              dev.erst.fingrind.executor.spi.PostingIdGenerator postingIdGenerator) {
            throw new AssertionError("commitStore.commit should not run for duplicate idempotency");
          }
        };
    PostingApplicationService applicationService = applicationService(bookSession);

    PostEntryResult result = applicationService.commit(command("idem-duplicate"));

    assertEquals(
        commitRejected(
            new IdempotencyKey("idem-duplicate"), new PostingRejection.IdempotencyKeyConflict()),
        result);
  }

  @Test
  void commit_propagatesUnexpectedBookSessionFailure() {
    PostingApplicationServiceTestSupport.PostingBookSession bookSession =
        new PostingApplicationServiceTestSupport.DelegatingPostingBookSession() {
          @Override
          public BookLifecycleInspection inspectBook() {
            return initializedLifecycleInspection(1001, 1, 1, FIXED_CLOCK.instant());
          }

          @Override
          public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
            return Optional.of(
                registeredAccount(
                    accountCode,
                    new AccountName("Synthetic"),
                    "1000".equals(accountCode.value()) ? AccountType.ASSET : AccountType.REVENUE,
                    "1000".equals(accountCode.value()) ? NormalBalance.DEBIT : NormalBalance.CREDIT,
                    true,
                    FIXED_CLOCK.instant()));
          }

          @Override
          public PostingCommitResult commit(
              dev.erst.fingrind.executor.spi.PostingDraft postingDraft,
              dev.erst.fingrind.executor.spi.PostingIdGenerator postingIdGenerator) {
            throw new IllegalStateException("boom");
          }
        };
    PostingApplicationService applicationService = applicationService(bookSession);

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> applicationService.commit(command("idem-1")));

    assertEquals("boom", thrown.getMessage());
  }

  private static void assertCommitted(
      PostEntryResult result, String postingId, String idempotencyKey) {
    PostEntryResult.Committed committed = assertInstanceOf(PostEntryResult.Committed.class, result);
    assertEquals(new PostingId(postingId), committed.postingId());
    assertEquals(new IdempotencyKey(idempotencyKey), committed.idempotencyKey());
    assertEquals(LocalDate.parse("2026-04-07"), committed.effectiveDate());
    assertEquals(FIXED_CLOCK.instant(), committed.recordedAt());
    assertFalse(committed.idempotentReplay());
    assertEquals(
        LocalDate.parse("2026-04-07"), committed.resolvedJournal().expandedLines().effectiveDate());
  }
}
