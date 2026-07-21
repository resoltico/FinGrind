package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.openedBook;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AmendAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.AmendAccountResult;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.ClosedFiscalYear;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseCommand;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.InventoryMovementPrecedesAccountHorizon;
import dev.erst.fingrind.contract.bookkeeping.InventoryQuantityBelowZero;
import dev.erst.fingrind.contract.bookkeeping.InventoryWriteDownExceedsCarryingCost;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostingEffectiveDateBeforeBookStart;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceDocumentType;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for the bookkeeping published-language translator. */
class BookkeepingPublishedLanguageTranslatorTest {
  @Test
  void bookkeepingPublishedLanguageTranslator_translatesBookOpeningOutcomes() {
    Instant initializedAt = Instant.parse("2026-05-05T09:15:30Z");
    BookkeepingAdministrationRejection rejection =
        new BookkeepingAdministrationRejection.BookAlreadyInitialized();

    OpenBookResult opened =
        BookkeepingPublishedLanguageTranslator.toPublished(openedBook(initializedAt));
    OpenBookResult rejected =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookOpeningOutcome.Rejected(rejection));

    assertEquals(
        initializedAt, assertInstanceOf(OpenBookResult.Opened.class, opened).initializedAt());
    assertEquals(
        new BookAdministrationRejection.BookAlreadyInitialized(),
        assertInstanceOf(OpenBookResult.Rejected.class, rejected).rejection());
  }

  @Test
  void bookkeepingPublishedLanguageTranslator_translatesAccountDeclarationOutcomes() {
    RegisteredAccount account =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-05-05T09:15:30Z"));
    BookkeepingAdministrationRejection rejection =
        new BookkeepingAdministrationRejection.AccountTaxonomyConflict(
            account.accountCode(),
            account.accountTaxonomy(),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.of(FinancialPositionLineClassification.NONCURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.NON_CASH)));

    DeclareAccountResult declared =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new AccountDeclarationOutcome.Declared(account));
    DeclareAccountResult reactivated =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new AccountDeclarationOutcome.Reactivated(account));
    DeclareAccountResult renamed =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new AccountDeclarationOutcome.Renamed(account));
    DeclareAccountResult unchanged =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new AccountDeclarationOutcome.Unchanged(account));
    DeclareAccountResult rejected =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new AccountDeclarationOutcome.Rejected(rejection));

    assertEquals(
        BookkeepingPublishedLanguageTranslator.toPublished(account),
        assertInstanceOf(DeclareAccountResult.Declared.class, declared).account());
    assertEquals(
        BookkeepingPublishedLanguageTranslator.toPublished(account),
        assertInstanceOf(DeclareAccountResult.Reactivated.class, reactivated).account());
    assertEquals(
        BookkeepingPublishedLanguageTranslator.toPublished(account),
        assertInstanceOf(DeclareAccountResult.Renamed.class, renamed).account());
    assertEquals(
        BookkeepingPublishedLanguageTranslator.toPublished(account),
        assertInstanceOf(DeclareAccountResult.Unchanged.class, unchanged).account());
    assertEquals(
        new BookAdministrationRejection.AccountTaxonomyConflict(
            account.accountCode(),
            account.accountTaxonomy(),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.of(FinancialPositionLineClassification.NONCURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.NON_CASH))),
        assertInstanceOf(DeclareAccountResult.Rejected.class, rejected).rejection());
  }

  @Test
  void accountRegistryPublishedLanguageTranslator_translatesLifecycleCommandsAndOutcomes() {
    RegisteredAccount account =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-05-05T09:15:30Z"));
    AmendAccountCommand command =
        new AmendAccountCommand(
            account.accountCode(),
            new AccountName("Operating Cash"),
            account.accountType(),
            account.accountTaxonomy());

    AccountDeclaration amendment =
        AccountRegistryPublishedLanguageTranslator.fromPublished(command);
    AmendAccountResult amended =
        AccountRegistryPublishedLanguageTranslator.toPublished(
            new AccountAmendmentOutcome.Amended(account));
    AmendAccountResult unchanged =
        AccountRegistryPublishedLanguageTranslator.toPublished(
            new AccountAmendmentOutcome.Unchanged(account));
    AmendAccountResult rejected =
        AccountRegistryPublishedLanguageTranslator.toPublished(
            new AccountAmendmentOutcome.Rejected(
                new AccountRegistryLifecycleRejection.AccountNotFound(account.accountCode())));
    RetireAccountResult retired =
        AccountRegistryPublishedLanguageTranslator.toPublished(
            new AccountRetirementOutcome.Retired(account));
    RetireAccountResult retirementUnchanged =
        AccountRegistryPublishedLanguageTranslator.toPublished(
            new AccountRetirementOutcome.Unchanged(account));
    RetireAccountResult retirementRejected =
        AccountRegistryPublishedLanguageTranslator.toPublished(
            new AccountRetirementOutcome.Rejected(
                new AccountRegistryLifecycleRejection.AccountBalanceNotZero(
                    account.accountCode())));

    assertEquals(command.accountCode(), amendment.accountCode());
    assertEquals(command.accountName(), amendment.accountName());
    assertEquals(
        BookkeepingPublishedLanguageTranslator.toPublished(account),
        assertInstanceOf(AmendAccountResult.Amended.class, amended).account());
    assertEquals(
        BookkeepingPublishedLanguageTranslator.toPublished(account),
        assertInstanceOf(AmendAccountResult.Unchanged.class, unchanged).account());
    assertEquals(
        new dev.erst.fingrind.contract.bookkeeping.AccountRegistryLifecycleRejection
            .AccountNotFound(account.accountCode()),
        assertInstanceOf(AmendAccountResult.Rejected.class, rejected).rejection());
    assertEquals(
        BookkeepingPublishedLanguageTranslator.toPublished(account),
        assertInstanceOf(RetireAccountResult.Retired.class, retired).account());
    assertEquals(
        BookkeepingPublishedLanguageTranslator.toPublished(account),
        assertInstanceOf(RetireAccountResult.Unchanged.class, retirementUnchanged).account());
    assertEquals(
        new dev.erst.fingrind.contract.bookkeeping.AccountRegistryLifecycleRejection
            .AccountBalanceNotZero(account.accountCode()),
        assertInstanceOf(RetireAccountResult.Rejected.class, retirementRejected).rejection());
  }

  @Test
  void
      bookkeepingPublishedLanguageTranslator_translatesBookContainsSchemaAndGuardsEmptyViolations() {
    OpenBookResult notInitialized =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookOpeningOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized()));
    OpenBookResult rejected =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookOpeningOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookContainsSchema()));

    assertEquals(
        new BookAdministrationRejection.BookNotInitialized(),
        assertInstanceOf(OpenBookResult.Rejected.class, notInitialized).rejection());
    assertEquals(
        new BookAdministrationRejection.BookContainsSchema(),
        assertInstanceOf(OpenBookResult.Rejected.class, rejected).rejection());
    assertThrows(
        IllegalArgumentException.class,
        () -> new BookkeepingPostingRejection.AccountStateViolations(java.util.List.of()));
    InvocationTargetException invocationTargetException =
        assertThrows(
            InvocationTargetException.class,
            () ->
                BookkeepingPostingRejection.AccountStateViolations.class
                    .getDeclaredConstructor(java.util.List.class)
                    .newInstance((Object) null));
    NullPointerException cause =
        assertInstanceOf(NullPointerException.class, invocationTargetException.getCause());
    assertEquals("violations", cause.getMessage());
  }

  @Test
  void bookkeepingPublishedLanguageTranslator_translatesDirectAndReversalLineage() {
    ReversalReference reversalReference =
        new ReversalReference(new dev.erst.fingrind.core.PostingId("posting-1"));
    ReversalReason reversalReason = new ReversalReason("duplicate entry");

    assertEquals(
        PostingLineage.direct(),
        BookkeepingPublishedLanguageTranslator.toPublished(PostingLineageModel.direct()));
    assertEquals(
        PostingLineageModel.direct(),
        BookkeepingPublishedLanguageTranslator.fromPublished(PostingLineage.direct()));
    assertEquals(
        PostingLineage.reversal(reversalReference, reversalReason),
        BookkeepingPublishedLanguageTranslator.toPublished(
            PostingLineageModel.reversal(reversalReference, reversalReason)));
    assertEquals(
        PostingLineageModel.reversal(reversalReference, reversalReason),
        BookkeepingPublishedLanguageTranslator.fromPublished(
            PostingLineage.reversal(reversalReference, reversalReason)));
  }

  @Test
  void bookkeepingPublishedLanguageTranslator_fromPublishedDropsResolvedEntryWhenJournalDrifts() {
    dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry originatingEntry =
        new dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("4000"),
            new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null,
            null);
    dev.erst.fingrind.contract.bookkeeping.PostingFact postingFact =
        new dev.erst.fingrind.contract.bookkeeping.PostingFact(
            new dev.erst.fingrind.core.PostingId("posting-1"),
            new dev.erst.fingrind.core.JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new dev.erst.fingrind.core.JournalLine(
                        new AccountCode("1000"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "12.00")),
                    new dev.erst.fingrind.core.JournalLine(
                        new AccountCode("4000"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "12.00")))),
            PostingLineage.direct(),
            dev.erst.fingrind.core.PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.SALE_SETTLED,
            dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence(
                "published-posting"),
            new dev.erst.fingrind.core.CommittedProvenance(
                new dev.erst.fingrind.core.RequestProvenance(
                    "actor-1",
                    "person",
                    new dev.erst.fingrind.core.CommandId("command-1"),
                    new dev.erst.fingrind.core.IdempotencyKey("idem-1"),
                    new dev.erst.fingrind.core.CausationId("cause-1"),
                    java.util.Optional.of(new dev.erst.fingrind.core.CorrelationId("corr-1"))),
                Instant.parse("2026-05-05T09:15:30Z"),
                dev.erst.fingrind.core.SourceChannel.CLI),
            originatingEntry);

    CommittedPosting committedPosting =
        BookkeepingPublishedLanguageTranslator.fromPublished(postingFact);

    assertEquals(java.util.Optional.of(originatingEntry), committedPosting.callerAuthoredEntry());
    assertEquals(java.util.Optional.empty(), committedPosting.resolvedOriginatingEntry());
  }

  @Test
  void bookkeepingPublishedLanguageTranslator_translatesInterimResultSweepCommandsAndOutcomes() {
    ReportingPeriod reportingPeriod =
        new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"));
    Instant sweptAt = Instant.parse("2026-05-05T09:15:30Z");
    var sweptInterimResult =
        new dev.erst.fingrind.executor.bookkeeping.SweptInterimResult(
            1,
            reportingPeriod,
            new AccountCode("3200"),
            List.of(
                CurrencyBalance.ofTotals(Money.parse("EUR", "0.00"), Money.parse("EUR", "5.00"))),
            sweptAt,
            List.of(new dev.erst.fingrind.core.PostingId("posting-1")));

    assertEquals(
        reportingPeriod.effectiveDateTo(),
        BookkeepingRequestPublishedLanguageTranslator.fromPublished(
            new InterimResultSweepCommand(reportingPeriod.effectiveDateTo())));
    assertEquals(
        new InterimResultSweepResult.Swept(
            new dev.erst.fingrind.contract.bookkeeping.SweptInterimResult(
                1,
                reportingPeriod,
                new AccountCode("3200"),
                List.of(
                    CurrencyBalance.ofTotals(
                        Money.parse("EUR", "0.00"), Money.parse("EUR", "5.00"))),
                sweptAt,
                List.of(new dev.erst.fingrind.core.PostingId("posting-1")))),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new InterimResultSweepOutcome.Transferred(sweptInterimResult)));
    assertEquals(
        new InterimResultSweepResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateMissing(
                FinancialPositionLineClassification.RESULT_HOLDING, List.of())),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new InterimResultSweepOutcome.Rejected(
                new CloseTargetAccountCandidateMissing(
                    FinancialPositionLineClassification.RESULT_HOLDING, List.of()))));
  }

  @Test
  void bookkeepingPublishedLanguageTranslator_translatesFiscalYearCloseCommandsAndOutcomes() {
    ReportingPeriod reportingPeriod =
        new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
    Instant closedAt = Instant.parse("2027-01-05T09:15:30Z");
    ClosedFiscalYearRecord closedFiscalYear =
        new ClosedFiscalYearRecord(
            1,
            reportingPeriod,
            new AccountCode("3000"),
            new AccountCode("3200"),
            new AccountCode("3300"),
            closedAt,
            List.of(new dev.erst.fingrind.core.PostingId("posting-1")));

    assertEquals(
        2026,
        BookkeepingRequestPublishedLanguageTranslator.fromPublished(
            new FiscalYearCloseCommand(2026)));
    assertEquals(
        new FiscalYearCloseResult.Closed(
            new ClosedFiscalYear(
                1,
                reportingPeriod,
                new AccountCode("3000"),
                new AccountCode("3200"),
                new AccountCode("3300"),
                closedAt,
                List.of(new dev.erst.fingrind.core.PostingId("posting-1"))),
            false),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new FiscalYearCloseOutcome.Closed(closedFiscalYear, false)));
    assertEquals(
        new FiscalYearCloseResult.Rejected(
            new BookAdministrationRejection.FiscalYearCloseMustEndAt(
                LocalDate.parse("2026-12-31"))),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new FiscalYearCloseOutcome.Rejected(
                new BookkeepingAdministrationRejection.FiscalYearCloseMustEndAt(
                    LocalDate.parse("2026-12-31")))));
  }

  @Test
  void
      bookkeepingPublishedLanguageTranslator_translatesExtendedAdministrationAndPostingRejections() {
    assertExtendedAdministrationRejectionTranslations();
    assertInventoryAccountStateViolationTranslations();
    assertEntrySemanticsViolationTranslations();
    assertReversalTargetRejectionTranslations();
  }

  private static void assertExtendedAdministrationRejectionTranslations() {
    assertEquals(
        new BookAdministrationRejection.AccountTypeConflict(
            new AccountCode("1000"), AccountType.ASSET, AccountType.LIABILITY),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.AccountTypeConflict(
                new AccountCode("1000"), AccountType.ASSET, AccountType.LIABILITY)));
    var localAccountTaxonomyConflict =
        new BookkeepingAdministrationRejection.AccountTaxonomyConflict(
            new AccountCode("1000"),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                List.of(new AccountCode("1000")).stream().findFirst(),
                Optional.empty(),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                List.of(new AccountCode("1099")).stream().findFirst(),
                Optional.empty(),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)));
    assertEquals(
        new BookAdministrationRejection.AccountTaxonomyConflict(
            new AccountCode("1000"),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                List.of(new AccountCode("1000")).stream().findFirst(),
                Optional.empty(),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                List.of(new AccountCode("1099")).stream().findFirst(),
                Optional.empty(),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT))),
        BookkeepingPublishedLanguageTranslator.toPublished(localAccountTaxonomyConflict));
    assertEquals(
        new dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateMissing(
            FinancialPositionLineClassification.RESULT_HOLDING, List.of(new AccountCode("3200"))),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new CloseTargetAccountCandidateMissing(
                FinancialPositionLineClassification.RESULT_HOLDING,
                List.of(new AccountCode("3200")))));
    assertEquals(
        new BookAdministrationRejection.InterimResultSweepMustStartAt(
            LocalDate.parse("2026-04-08")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.InterimResultSweepMustStartAt(
                LocalDate.parse("2026-04-08"))));
    assertEquals(
        new dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateAmbiguous(
            FinancialPositionLineClassification.RESULT_HOLDING,
            List.of(new AccountCode("3200"), new AccountCode("3210"))),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new CloseTargetAccountCandidateAmbiguous(
                FinancialPositionLineClassification.RESULT_HOLDING,
                List.of(new AccountCode("3200"), new AccountCode("3210")))));
    assertEquals(
        new BookAdministrationRejection.InterimResultSweepFutureDate(LocalDate.parse("2026-04-30")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.InterimResultSweepFutureDate(
                LocalDate.parse("2026-04-30"))));
    assertEquals(
        new BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
            LocalDate.parse("2026-12-15"),
            LocalDate.parse("2027-01-15"),
            bookIdentity().fiscalYearStart()),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
                LocalDate.parse("2026-12-15"),
                LocalDate.parse("2027-01-15"),
                bookIdentity().fiscalYearStart())));
    assertEquals(
        new BookAdministrationRejection.ParentAccountMissing(
            new AccountCode("1010"), new AccountCode("1000")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.ParentAccountMissing(
                new AccountCode("1010"), new AccountCode("1000"))));
    assertEquals(
        new BookAdministrationRejection.ParentAccountInactive(
            new AccountCode("1010"), new AccountCode("1000")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.ParentAccountInactive(
                new AccountCode("1010"), new AccountCode("1000"))));
    assertEquals(
        new BookAdministrationRejection.ParentAccountTypeConflict(
            new AccountCode("1010"),
            AccountType.ASSET,
            new AccountCode("2000"),
            AccountType.LIABILITY),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.ParentAccountTypeConflict(
                new AccountCode("1010"),
                AccountType.ASSET,
                new AccountCode("2000"),
                AccountType.LIABILITY)));
    assertEquals(
        new BookAdministrationRejection.ParentAccountNotHeader(
            new AccountCode("1010"), new AccountCode("1000"), AccountNodeKind.POSTABLE),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.ParentAccountNotHeader(
                new AccountCode("1010"), new AccountCode("1000"), AccountNodeKind.POSTABLE)));
    var localParentAccountTaxonomyConflict =
        new BookkeepingAdministrationRejection.ParentAccountTaxonomyConflict(
            new AccountCode("1010"),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                java.util.Optional.of(new AccountCode("1000")),
                java.util.Optional.empty(),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
            new AccountCode("1000"),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                java.util.Optional.of(new AccountCode("0900")),
                java.util.Optional.empty(),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)));
    assertEquals(
        new BookAdministrationRejection.ParentAccountTaxonomyConflict(
            new AccountCode("1010"),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                java.util.Optional.of(new AccountCode("1000")),
                java.util.Optional.empty(),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
            new AccountCode("1000"),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                java.util.Optional.of(new AccountCode("0900")),
                java.util.Optional.empty(),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT))),
        BookkeepingPublishedLanguageTranslator.toPublished(localParentAccountTaxonomyConflict));
    assertEquals(
        new BookAdministrationRejection.AccountHierarchyCycle(
            new AccountCode("1010"), new AccountCode("1000")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.AccountHierarchyCycle(
                new AccountCode("1010"), new AccountCode("1000"))));
  }

  private static void assertInventoryAccountStateViolationTranslations() {
    assertEquals(
        new PostingRejection.AccountStateViolations(
            List.of(
                new PostingRejection.NonPostableAccount(
                    new AccountCode("1000"), AccountNodeKind.HEADER))),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.AccountStateViolations(
                List.of(
                    new BookkeepingPostingRejection.NonPostableAccount(
                        new AccountCode("1000"), AccountNodeKind.HEADER)))));
    assertEquals(
        new PostingRejection.AccountStateViolations(
            List.of(
                new InventoryMovementPrecedesAccountHorizon(
                    new AccountCode("1400"),
                    "effectiveDate",
                    LocalDate.parse("2026-04-07"),
                    LocalDate.parse("2026-04-08")),
                new InventoryQuantityBelowZero(
                    new AccountCode("1400"),
                    "inventoryRelief.quantity",
                    LocalDate.parse("2026-04-07"),
                    Quantity.ofScaledUnits(0, 1),
                    Quantity.ofScaledUnits(0, 5),
                    Quantity.ofScaledUnits(0, 4)),
                new InventoryWriteDownExceedsCarryingCost(
                    new AccountCode("1400"),
                    "reversal.priorPostingId",
                    LocalDate.parse("2026-04-07"),
                    Money.parse("EUR", "10.00"),
                    Money.parse("EUR", "50.00"),
                    Money.parse("EUR", "40.00")))),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.AccountStateViolations(
                List.of(
                    new InventoryMovementPrecedesAccountHorizonViolation(
                        new AccountCode("1400"),
                        "effectiveDate",
                        LocalDate.parse("2026-04-07"),
                        LocalDate.parse("2026-04-08")),
                    new InventoryQuantityBelowZeroViolation(
                        new AccountCode("1400"),
                        "inventoryRelief.quantity",
                        LocalDate.parse("2026-04-07"),
                        Quantity.ofScaledUnits(0, 1),
                        Quantity.ofScaledUnits(0, 5),
                        Quantity.ofScaledUnits(0, 4)),
                    new InventoryWriteDownExceedsCarryingCostViolation(
                        new AccountCode("1400"),
                        "reversal.priorPostingId",
                        LocalDate.parse("2026-04-07"),
                        Money.parse("EUR", "10.00"),
                        Money.parse("EUR", "50.00"),
                        Money.parse("EUR", "40.00"))))));
  }

  private static void assertEntrySemanticsViolationTranslations() {
    assertEquals(
        new PostingRejection.EntrySemanticsViolations(
            List.of(
                PostingRejectionSemantics.accountTypeMismatch(
                    "SALE",
                    "cashAccountCode",
                    new AccountCode("2000"),
                    AccountType.ASSET,
                    AccountType.REVENUE),
                PostingRejectionSemantics.sourceDocumentTypeNotAccepted(
                    "SALE",
                    new SourceDocumentType("invoice"),
                    List.of("cash-receipt", "bank-deposit", "card-settlement")))),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.EntrySemanticsViolations(
                List.of(
                    BookkeepingAccountSemanticsViolations.accountTypeMismatch(
                        "entryKind",
                        "SALE",
                        "cashAccountCode",
                        new AccountCode("2000"),
                        AccountType.ASSET,
                        AccountType.REVENUE),
                    BookkeepingEvidenceSemanticsViolations.sourceDocumentTypeNotAccepted(
                        "entryKind",
                        "SALE",
                        new SourceDocumentType("invoice"),
                        List.of("cash-receipt", "bank-deposit", "card-settlement"))))));
  }

  private static void assertReversalTargetRejectionTranslations() {
    assertEquals(
        new PostingRejection.ReversalTargetNotFound(
            new dev.erst.fingrind.core.PostingId("posting-1")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.ReversalTargetNotFound(
                new dev.erst.fingrind.core.PostingId("posting-1"))));
    assertEquals(
        new dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal(
            new dev.erst.fingrind.core.PostingId("posting-1")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new ReversalTargetIsReversal(new dev.erst.fingrind.core.PostingId("posting-1"))));
    assertEquals(
        new PostingRejection.ReversalAlreadyExists(
            new dev.erst.fingrind.core.PostingId("posting-1")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.ReversalAlreadyExists(
                new dev.erst.fingrind.core.PostingId("posting-1"))));
    assertEquals(
        new PostingRejection.ReversalDoesNotNegateTarget(
            new dev.erst.fingrind.core.PostingId("posting-1")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.ReversalDoesNotNegateTarget(
                new dev.erst.fingrind.core.PostingId("posting-1"))));
  }

  @Test
  void toPublished_projectsDirectPostingOutcomeVariants() {
    assertEquals(
        new PostingRejection.BookNotInitialized(),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.BookNotInitialized()));
    assertEquals(
        new PostingRejection.IdempotencyKeyConflict(),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.IdempotencyKeyConflict()));
    assertEquals(
        new PostingEffectiveDateBeforeBookStart(
            LocalDate.parse("2026-04-06"), LocalDate.parse("2026-04-07")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingEffectiveDateBeforeBookStart(
                LocalDate.parse("2026-04-06"), LocalDate.parse("2026-04-07"))));
    assertEquals(
        new PostingRejection.PostingEffectiveDateInFuture(
            LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-07")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.PostingEffectiveDateInFuture(
                LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-07"))));
    assertEquals(
        new PostingRejection.SweptInterimResultViolation(
            LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.SweptInterimResultViolation(
                LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07"))));
    assertEquals(
        new PostingRejection.OpeningPositionWindowClosed(
            dev.erst.fingrind.core.PostingKind.STANDARD, LocalDate.parse("2026-04-07")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.OpeningPositionWindowClosed(
                dev.erst.fingrind.core.PostingKind.STANDARD, LocalDate.parse("2026-04-07"))));
    assertEquals(
        new PostingRejection.BookFunctionalCurrencyMismatch(
            dev.erst.fingrind.core.CurrencyUnit.of("USD"),
            dev.erst.fingrind.core.CurrencyUnit.of("EUR")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.BookFunctionalCurrencyMismatch(
                dev.erst.fingrind.core.CurrencyUnit.of("USD"),
                dev.erst.fingrind.core.CurrencyUnit.of("EUR"))));
    assertEquals(
        new PostingRejection.OpeningPositionTouchesNominalAccount(
            new AccountCode("4000"), AccountType.REVENUE),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.OpeningPositionTouchesNominalAccount(
                new AccountCode("4000"), AccountType.REVENUE)));
    assertEquals(
        new PostingRejection.ReservedResultClassification(
            new AccountCode("3200"), FinancialPositionLineClassification.RESULT_HOLDING),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.ReservedResultClassification(
                new AccountCode("3200"), FinancialPositionLineClassification.RESULT_HOLDING)));
  }
}
