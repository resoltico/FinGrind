package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.TEST_AUTHORIZER;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.applicationService;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.command;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.conflictingPosting;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.declareDefaultAccounts;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.declareLatviaVatRegistration;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.declareNonCashDirectJournalAccounts;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.declareTaxAccounts;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.existingPosting;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.mismatchedReversalJournalEntry;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.preflightRejected;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.requestProvenance;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.reversalJournalEntry;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.reversalReference;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.taxedSaleCommand;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingEffectiveDateBeforeBookStart;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests covering preflight behavior in {@link PostingApplicationService}. */
class PostingApplicationServicePreflightTest {
  @Test
  void preflight_rejectsBookNotInitialized() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result = applicationService.preflight(command("idem-1"));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"), new PostingRejection.BookNotInitialized()),
          result);
    }
  }

  @Test
  void preflight_rejectsUnknownAndInactiveAccountsBeforeOtherChecks() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult unknownAccountResult = applicationService.preflight(command("idem-1"));
      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"),
              new PostingRejection.AccountStateViolations(
                  List.of(
                      new PostingRejection.UnknownAccount(new AccountCode("1000")),
                      new PostingRejection.UnknownAccount(new AccountCode("2000"))))),
          unknownAccountResult);

      declareDefaultAccounts(bookSession);
      bookSession.deactivateAccount(new AccountCode("1000"));

      PostEntryResult inactiveAccountResult = applicationService.preflight(command("idem-2"));
      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-2"),
              new PostingRejection.AccountStateViolations(
                  List.of(new PostingRejection.InactiveAccount(new AccountCode("1000"))))),
          inactiveAccountResult);
    }
  }

  @Test
  void preflight_returnsAcceptedWhenRequestIsAdmissible() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result = applicationService.preflight(command("idem-1"));

      assertAccepted(result, "idem-1");
    }
  }

  @Test
  void preflight_rejectsFutureEffectiveDatesBeforeSemanticValidation() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);
      PostEntryCommand command =
          new PostEntryCommand(
              new BookkeepingEntry.SaleSettled(
                  LocalDate.parse("2026-04-08"),
                  new AccountCode("1000"),
                  new AccountCode("2000"),
                  MonetaryAmount.of(Money.parse("EUR", "10.00")),
                  null,
                  null,
                  null,
                  null,
                  null),
              generatedEvidence("idem-future", "cash-receipt"),
              requestProvenance("idem-future"),
              SourceChannel.CLI);

      PostEntryResult result = applicationService.preflight(command);

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-future"),
              new PostingRejection.PostingEffectiveDateInFuture(
                  LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-07"))),
          result);
    }
  }

  @Test
  void preflight_rejectsEffectiveDatesBeforeImmutableBookStartBeforeSemanticValidation() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);
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

      PostEntryResult result = applicationService.preflight(command);

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-before-book-start"),
              new PostingEffectiveDateBeforeBookStart(
                  LocalDate.parse("2025-12-31"), LocalDate.parse("2026-01-01"))),
          result);
    }
  }

  @Test
  void preflight_returnsResolvedJournalForTaxedSettledSale() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      declareTaxAccounts(bookSession);
      declareLatviaVatRegistration(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result = applicationService.preflight(taxedSaleCommand("idem-taxed-sale"));

      PostEntryResult.PreflightAccepted accepted =
          assertInstanceOf(PostEntryResult.PreflightAccepted.class, result);
      var appliedTax = accepted.resolvedJournal().appliedTax();
      assertNotNull(appliedTax);
      assertEquals(new IdempotencyKey("idem-taxed-sale"), accepted.idempotencyKey());
      assertEquals(
          EconomicEventClass.SETTLED_SALE,
          accepted.resolvedJournal().classification().eventClass());
      assertEquals("2100", appliedTax.taxAmount().minorUnits());
      assertEquals(
          new AccountCode("2100"),
          accepted.resolvedJournal().expandedLines().lines().get(2).accountCode());
    }
  }

  @Test
  void preflight_returnsResolvedJournalForTradingSettledSaleWithDerivedInventoryCosting() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(
          PostingApplicationServiceTestSupport.FIXED_CLOCK.instant(),
          new BookIdentity(
              new EntityProfile(new BookEntityName("Acme Studio")),
              BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING,
              dev.erst.fingrind.core.CurrencyUnit.of("EUR"),
              FiscalYearStart.parse("01-01"),
              java.time.LocalDate.parse("2026-01-01")),
          List.of());
      bookSession.declareAccount(
          new AccountCode("1000"),
          new dev.erst.fingrind.core.AccountName("Cash"),
          dev.erst.fingrind.core.AccountType.ASSET,
          ExecutorAccountingTestSupport.accountTaxonomy(
              dev.erst.fingrind.core.AccountType.ASSET, dev.erst.fingrind.core.NormalBalance.DEBIT),
          PostingApplicationServiceTestSupport.FIXED_CLOCK.instant());
      bookSession.declareAccount(
          new AccountCode("4000"),
          new dev.erst.fingrind.core.AccountName("Sales Revenue"),
          dev.erst.fingrind.core.AccountType.REVENUE,
          ExecutorAccountingTestSupport.accountTaxonomy(
              dev.erst.fingrind.core.AccountType.REVENUE,
              dev.erst.fingrind.core.NormalBalance.CREDIT),
          PostingApplicationServiceTestSupport.FIXED_CLOCK.instant());
      bookSession.declareAccount(
          new AccountDeclaration(
              new AccountCode("1400"),
              new dev.erst.fingrind.core.AccountName("Inventory"),
              dev.erst.fingrind.core.AccountType.ASSET,
              ExecutorAccountingTestSupport.financialPositionTaxonomy(
                  dev.erst.fingrind.core.FinancialPositionLineClassification.INVENTORY),
              new dev.erst.fingrind.core.UnitOfMeasure("unit", 0)),
          PostingApplicationServiceTestSupport.FIXED_CLOCK.instant());
      bookSession.declareAccount(
          new AccountCode("5000"),
          new dev.erst.fingrind.core.AccountName("Cost of Sales"),
          dev.erst.fingrind.core.AccountType.EXPENSE,
          ExecutorAccountingTestSupport.accountTaxonomy(dev.erst.fingrind.core.AccountType.EXPENSE),
          PostingApplicationServiceTestSupport.FIXED_CLOCK.instant());
      bookSession.inventoryStateByAccount.put(
          new AccountCode("1400"),
          new dev.erst.fingrind.executor.bookkeeping.InventoryAccountState(
              new WeightedAverageCostingMath.InventoryPool(
                  Quantity.ofScaledUnits(0, 2), Money.parse("EUR", "20.00")),
              Optional.of(LocalDate.parse("2026-04-06"))));
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result =
          applicationService.preflight(
              new PostEntryCommand(
                  new BookkeepingEntry.SaleSettled(
                      LocalDate.parse("2026-04-07"),
                      new AccountCode("1000"),
                      new AccountCode("4000"),
                      MonetaryAmount.of(Money.parse("EUR", "70.00")),
                      new dev.erst.fingrind.contract.bookkeeping.InventoryRelief(
                          new AccountCode("1400"),
                          new AccountCode("5000"),
                          new dev.erst.fingrind.contract.bookkeeping.QuantityText("1")),
                      null,
                      null,
                      null,
                      null),
                  generatedEvidence("idem-trading-sale", "cash-receipt"),
                  requestProvenance("idem-trading-sale"),
                  SourceChannel.CLI));

      PostEntryResult.PreflightAccepted accepted =
          assertInstanceOf(PostEntryResult.PreflightAccepted.class, result);
      assertEquals(
          EconomicEventClass.SETTLED_SALE,
          accepted.resolvedJournal().classification().eventClass());
      assertEquals(4, accepted.resolvedJournal().expandedLines().lines().size());
      assertEquals(
          new AccountCode("5000"),
          accepted.resolvedJournal().expandedLines().lines().get(2).accountCode());
      assertEquals(
          Money.parse("EUR", "10.00"),
          accepted.resolvedJournal().expandedLines().lines().get(2).amount().money());
      assertEquals(
          new AccountCode("1400"),
          accepted.resolvedJournal().expandedLines().lines().get(3).accountCode());
      assertEquals(
          Money.parse("EUR", "10.00"),
          accepted.resolvedJournal().expandedLines().lines().get(3).amount().money());
    }
  }

  @Test
  void preflight_rejectsTypedEntryWhenAccountsAndEvidenceContradictEntryKind() {
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

      PostEntryResult result = applicationService.preflight(command);

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-semantics"),
              new PostingRejection.EntrySemanticsViolations(
                  List.of(
                      PostingRejectionSemantics.accountTypeMismatch(
                          BookkeepingEntryKind.SALE_SETTLED.wireValue(),
                          "cashAccountCode",
                          new AccountCode("2000"),
                          dev.erst.fingrind.core.AccountType.ASSET,
                          dev.erst.fingrind.core.AccountType.REVENUE),
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
                          dev.erst.fingrind.core.AccountType.REVENUE,
                          dev.erst.fingrind.core.AccountType.ASSET),
                      PostingRejectionSemantics.sourceDocumentTypeNotAccepted(
                          BookkeepingEntryKind.SALE_SETTLED.wireValue(),
                          new dev.erst.fingrind.core.SourceDocumentType("invoice"),
                          List.of("cash-receipt", "bank-deposit", "card-settlement"))))),
          result);
    }
  }

  @Test
  void preflight_rejectsEconomicallyNullDirectJournalsBeforeBookkeepingCommitChecks() {
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

      PostEntryResult result = applicationService.preflight(command);

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-economic-null"),
              new PostingRejection.EntrySemanticsViolations(
                  List.of(
                      PostingRejectionSemantics.economicNullJournal(
                          BookkeepingEntryKind.DIRECT_JOURNAL.wireValue())))),
          result);
    }
  }

  @Test
  void preflight_rejectsDirectJournalsThatNeverTouchDeclaredCashAccounts() {
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

      PostEntryResult result = applicationService.preflight(command);

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-non-cash-direct-journal"),
              new PostingRejection.EntrySemanticsViolations(
                  List.of(
                      PostingRejectionSemantics.rawJournalRequiresCashLine(
                          BookkeepingEntryKind.DIRECT_JOURNAL.wireValue())))),
          result);
    }
  }

  @Test
  void preflight_rejectsIdempotencyKeyConflict() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(conflictingPosting("posting-existing", "idem-1"));
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result = applicationService.preflight(command("idem-1"));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"), new PostingRejection.IdempotencyKeyConflict()),
          result);
    }
  }

  @Test
  void preflight_rejectsMissingReversalTarget() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result =
          applicationService.preflight(
              command(
                  "idem-1",
                  Optional.of(new ReversalReference(new PostingId("posting-missing"))),
                  Optional.of(new ReversalReason("operator reversal"))));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"),
              new PostingRejection.ReversalTargetNotFound(new PostingId("posting-missing"))),
          result);
    }
  }

  @Test
  void preflight_acceptsReversalWhenTargetExistsAndReasonIsPresent() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(existingPosting("posting-1", "idem-existing"));
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result =
          applicationService.preflight(
              command(
                  "idem-1",
                  reversalReference("posting-1"),
                  Optional.of(new ReversalReason("full reversal")),
                  reversalJournalEntry()));

      assertAccepted(result, "idem-1");
    }
  }

  @Test
  void preflight_derivesReversalJournalFromTargetPosting() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(existingPosting("posting-1", "idem-existing"));
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult.PreflightAccepted accepted =
          assertInstanceOf(
              PostEntryResult.PreflightAccepted.class,
              applicationService.preflight(
                  command(
                      "idem-1",
                      reversalReference("posting-1"),
                      Optional.of(new ReversalReason("full reversal")),
                      mismatchedReversalJournalEntry())));

      assertEquals(new IdempotencyKey("idem-1"), accepted.idempotencyKey());
      assertEquals(reversalJournalEntry(), accepted.resolvedJournal().expandedLines());
    }
  }

  @Test
  void preflight_rejectsReversalWhenTargetAlreadyHasReversal() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(existingPosting("posting-1", "idem-original"));
      PostingApplicationService applicationService = applicationService(bookSession);
      applicationService.commit(
          command(
              "idem-existing-reversal",
              reversalReference("posting-1"),
              Optional.of(new ReversalReason("full reversal")),
              reversalJournalEntry()),
          TEST_AUTHORIZER);

      PostEntryResult result =
          applicationService.preflight(
              command(
                  "idem-1",
                  reversalReference("posting-1"),
                  Optional.of(new ReversalReason("full reversal")),
                  reversalJournalEntry()));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"),
              new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))),
          result);
    }
  }

  @Test
  void preflight_rejectsReversalWhenTargetIsAlreadyAReversal() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);
      PostEntryResult.Committed originalCommitted =
          assertInstanceOf(
              PostEntryResult.Committed.class,
              applicationService.commit(command("idem-original"), TEST_AUTHORIZER));
      PostEntryResult.Committed reversalCommitted =
          assertInstanceOf(
              PostEntryResult.Committed.class,
              applicationService.commit(
                  command(
                      "idem-reversal",
                      Optional.of(new ReversalReference(originalCommitted.postingId())),
                      Optional.of(new ReversalReason("full reversal")),
                      reversalJournalEntry()),
                  TEST_AUTHORIZER));

      PostEntryResult result =
          applicationService.preflight(
              command(
                  "idem-reroll",
                  Optional.of(new ReversalReference(reversalCommitted.postingId())),
                  Optional.of(new ReversalReason("redo by reversal")),
                  reversalJournalEntry()));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-reroll"),
              new ReversalTargetIsReversal(reversalCommitted.postingId())),
          result);
    }
  }

  @Test
  void applicationRejectionFor_translatesResolutionRejectionsAfterEarlierChecksPass() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);
      PostEntryCommand command =
          command(
              "idem-reversal-missing",
              reversalReference("posting-missing"),
              Optional.of(new ReversalReason("operator reversal")),
              reversalJournalEntry());

      assertEquals(
          Optional.of(
              new PostingRejection.ReversalTargetNotFound(new PostingId("posting-missing"))),
          invokePrivateOptionalRejection(applicationService, "applicationRejectionFor", command));
    }
  }

  @Test
  void declaredAccountRejectionFor_reusesOneDeclaredAccountWhenOnlyOneAccountIsReferenced() {
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
                              new AccountCode("1000"),
                              dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                              Money.parse("EUR", "10.00")))),
                  null),
              generatedEvidence("idem-single-account", "operator-note"),
              requestProvenance("idem-single-account"),
              SourceChannel.CLI);

      assertEquals(
          Optional.empty(),
          invokePrivateOptionalRejection(
              applicationService, "declaredAccountRejectionFor", command));
    }
  }

  @Test
  void applicationRejectionFor_translatesInventoryAdmissionFailuresAfterEarlierChecksPass() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(
          PostingApplicationServiceTestSupport.FIXED_CLOCK.instant(),
          PostEntrySemanticsPolicyTestSupport.tradingCashBookIdentity(),
          List.of());
      declareDefaultAccounts(bookSession);
      bookSession.declareAccount(
          new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
              new AccountCode("1400"),
              new dev.erst.fingrind.core.AccountName("Inventory"),
              dev.erst.fingrind.core.AccountType.ASSET,
              ExecutorAccountingTestSupport.financialPositionTaxonomy(
                  dev.erst.fingrind.core.FinancialPositionLineClassification.INVENTORY),
              new dev.erst.fingrind.core.UnitOfMeasure("unit", 0)),
          PostingApplicationServiceTestSupport.FIXED_CLOCK.instant());
      bookSession.declareAccount(
          new AccountCode("5000"),
          new dev.erst.fingrind.core.AccountName("Cost of Sales"),
          dev.erst.fingrind.core.AccountType.EXPENSE,
          ExecutorAccountingTestSupport.accountTaxonomy(dev.erst.fingrind.core.AccountType.EXPENSE),
          PostingApplicationServiceTestSupport.FIXED_CLOCK.instant());
      PostingApplicationService applicationService = applicationService(bookSession);
      PostEntryCommand command =
          new PostEntryCommand(
              new BookkeepingEntry.SaleSettled(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1000"),
                  new AccountCode("2000"),
                  MonetaryAmount.of(Money.parse("EUR", "10.00")),
                  new dev.erst.fingrind.contract.bookkeeping.InventoryRelief(
                      new AccountCode("1400"),
                      new AccountCode("5000"),
                      new dev.erst.fingrind.contract.bookkeeping.QuantityText("1")),
                  null,
                  null,
                  null,
                  null),
              generatedEvidence("idem-inventory-overdraw", "cash-receipt"),
              requestProvenance("idem-inventory-overdraw"),
              SourceChannel.CLI);

      assertEquals(
          Optional.of(
              new PostingRejection.AccountStateViolations(
                  List.of(
                      new dev.erst.fingrind.contract.bookkeeping.InventoryQuantityBelowZero(
                          new AccountCode("1400"),
                          "inventoryRelief.quantity",
                          LocalDate.parse("2026-04-07"),
                          dev.erst.fingrind.core.Quantity.zero(0),
                          dev.erst.fingrind.core.Quantity.ofScaledUnits(0, 1),
                          dev.erst.fingrind.core.Quantity.ofScaledUnits(0, 1))))),
          invokePrivateOptionalRejection(applicationService, "applicationRejectionFor", command));
    }
  }

  private static void assertAccepted(PostEntryResult result, String idempotencyKey) {
    PostEntryResult.PreflightAccepted accepted =
        assertInstanceOf(PostEntryResult.PreflightAccepted.class, result);
    assertEquals(new IdempotencyKey(idempotencyKey), accepted.idempotencyKey());
    assertEquals(LocalDate.parse("2026-04-07"), accepted.effectiveDate());
    assertEquals(
        LocalDate.parse("2026-04-07"), accepted.resolvedJournal().expandedLines().effectiveDate());
  }

  @SuppressWarnings("unchecked")
  private static Optional<PostingRejection> invokePrivateOptionalRejection(
      PostingApplicationService applicationService, String methodName, PostEntryCommand command) {
    try {
      MethodHandle methodHandle =
          MethodHandles.privateLookupIn(PostingApplicationService.class, MethodHandles.lookup())
              .findVirtual(
                  PostingApplicationService.class,
                  methodName,
                  MethodType.methodType(Optional.class, PostEntryCommand.class));
      return (Optional<PostingRejection>) methodHandle.invoke(applicationService, command);
    } catch (RuntimeException | Error throwable) {
      throw throwable;
    } catch (Throwable throwable) {
      throw new AssertionError(throwable);
    }
  }
}
