package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.openedBook;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.ClosePeriodCommand;
import dev.erst.fingrind.contract.bookkeeping.ClosePeriodResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
        new BookkeepingAdministrationRejection.AccountRoleConflict(
            account.accountCode(), AccountRole.ORDINARY, AccountRole.CONTRA);

    DeclareAccountResult declared =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new AccountDeclarationOutcome.Declared(account));
    DeclareAccountResult rejected =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new AccountDeclarationOutcome.Rejected(rejection));

    assertEquals(
        BookkeepingPublishedLanguageTranslator.toPublished(account),
        assertInstanceOf(DeclareAccountResult.Declared.class, declared).account());
    assertEquals(
        new BookAdministrationRejection.AccountRoleConflict(
            account.accountCode(), AccountRole.ORDINARY, AccountRole.CONTRA),
        assertInstanceOf(DeclareAccountResult.Rejected.class, rejected).rejection());
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
  void bookkeepingPublishedLanguageTranslator_translatesClosePeriodCommandsAndOutcomes() {
    ReportingPeriod reportingPeriod =
        new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"));
    Instant closedAt = Instant.parse("2026-05-05T09:15:30Z");
    var closedPeriod =
        new dev.erst.fingrind.executor.bookkeeping.ClosedPeriod(
            1,
            reportingPeriod,
            new AccountCode("3200"),
            List.of(
                CurrencyBalance.ofTotals(Money.parse("EUR", "0.00"), Money.parse("EUR", "5.00"))),
            closedAt,
            List.of(new dev.erst.fingrind.core.PostingId("posting-1")));

    assertEquals(
        reportingPeriod,
        BookkeepingPublishedLanguageTranslator.fromPublished(
            new ClosePeriodCommand(reportingPeriod)));
    assertEquals(
        new ClosePeriodResult.Closed(
            new dev.erst.fingrind.contract.bookkeeping.ClosedPeriod(
                1,
                reportingPeriod,
                new AccountCode("3200"),
                List.of(
                    CurrencyBalance.ofTotals(
                        Money.parse("EUR", "0.00"), Money.parse("EUR", "5.00"))),
                closedAt,
                List.of(new dev.erst.fingrind.core.PostingId("posting-1")))),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new PeriodCloseOutcome.Closed(closedPeriod)));
    assertEquals(
        new ClosePeriodResult.Rejected(
            new BookAdministrationRejection.ClosingEquityAccountCandidateMissing(
                FinancialPositionLineClassification.RETAINED_EARNINGS, List.of())),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new PeriodCloseOutcome.Rejected(
                new BookkeepingAdministrationRejection.ClosingEquityAccountCandidateMissing(
                    FinancialPositionLineClassification.RETAINED_EARNINGS, List.of()))));
  }

  @Test
  void
      bookkeepingPublishedLanguageTranslator_translatesExtendedAdministrationAndPostingRejections() {
    assertEquals(
        new BookAdministrationRejection.AccountTypeConflict(
            new AccountCode("1000"), AccountType.ASSET, AccountType.LIABILITY),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.AccountTypeConflict(
                new AccountCode("1000"), AccountType.ASSET, AccountType.LIABILITY)));
    assertEquals(
        new BookAdministrationRejection.AccountTaxonomyConflict(
            new AccountCode("1000"),
            new dev.erst.fingrind.core.AccountTaxonomy(
                List.of(new AccountCode("1000")).stream().findFirst(),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty()),
            new dev.erst.fingrind.core.AccountTaxonomy(
                List.of(new AccountCode("1099")).stream().findFirst(),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty())),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.AccountTaxonomyConflict(
                new AccountCode("1000"),
                new dev.erst.fingrind.core.AccountTaxonomy(
                    List.of(new AccountCode("1000")).stream().findFirst(),
                    java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    java.util.Optional.empty()),
                new dev.erst.fingrind.core.AccountTaxonomy(
                    List.of(new AccountCode("1099")).stream().findFirst(),
                    java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    java.util.Optional.empty()))));
    assertEquals(
        new BookAdministrationRejection.ClosingEquityAccountCandidateMissing(
            FinancialPositionLineClassification.RETAINED_EARNINGS,
            List.of(new AccountCode("3200"))),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.ClosingEquityAccountCandidateMissing(
                FinancialPositionLineClassification.RETAINED_EARNINGS,
                List.of(new AccountCode("3200")))));
    assertEquals(
        new BookAdministrationRejection.PeriodCloseMustStartAt(LocalDate.parse("2026-04-08")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.PeriodCloseMustStartAt(
                LocalDate.parse("2026-04-08"))));
    assertEquals(
        new BookAdministrationRejection.ClosingEquityAccountCandidateAmbiguous(
            FinancialPositionLineClassification.RETAINED_EARNINGS,
            List.of(new AccountCode("3200"), new AccountCode("3210"))),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.ClosingEquityAccountCandidateAmbiguous(
                FinancialPositionLineClassification.RETAINED_EARNINGS,
                List.of(new AccountCode("3200"), new AccountCode("3210")))));
    assertEquals(
        new BookAdministrationRejection.PeriodCloseFutureDate(LocalDate.parse("2026-04-30")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.PeriodCloseFutureDate(
                LocalDate.parse("2026-04-30"))));
    assertEquals(
        new BookAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary(
            LocalDate.parse("2026-12-15"),
            LocalDate.parse("2027-01-15"),
            bookIdentity().fiscalYearStart()),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary(
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
        new BookAdministrationRejection.ParentAccountTaxonomyConflict(
            new AccountCode("1010"),
            new dev.erst.fingrind.core.AccountTaxonomy(
                java.util.Optional.of(new AccountCode("1000")),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty()),
            new AccountCode("1000"),
            new dev.erst.fingrind.core.AccountTaxonomy(
                java.util.Optional.of(new AccountCode("0900")),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty())),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.ParentAccountTaxonomyConflict(
                new AccountCode("1010"),
                new dev.erst.fingrind.core.AccountTaxonomy(
                    java.util.Optional.of(new AccountCode("1000")),
                    java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    java.util.Optional.empty()),
                new AccountCode("1000"),
                new dev.erst.fingrind.core.AccountTaxonomy(
                    java.util.Optional.of(new AccountCode("0900")),
                    java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    java.util.Optional.empty()))));
    assertEquals(
        new BookAdministrationRejection.AccountHierarchyCycle(
            new AccountCode("1010"), new AccountCode("1000")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.AccountHierarchyCycle(
                new AccountCode("1010"), new AccountCode("1000"))));

    assertEquals(
        new PostingRejection.ClosedPeriodViolation(
            LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.ClosedPeriodViolation(
                LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07"))));
    assertEquals(
        new PostingRejection.OpeningBalanceWindowClosed(
            dev.erst.fingrind.core.PostingKind.STANDARD, LocalDate.parse("2026-04-07")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.OpeningBalanceWindowClosed(
                dev.erst.fingrind.core.PostingKind.STANDARD, LocalDate.parse("2026-04-07"))));
    assertEquals(
        new PostingRejection.PostingKindReserved(dev.erst.fingrind.core.PostingKind.PERIOD_CLOSE),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.PostingKindReserved(
                dev.erst.fingrind.core.PostingKind.PERIOD_CLOSE)));
    assertEquals(
        new PostingRejection.BookFunctionalCurrencyMismatch(
            dev.erst.fingrind.core.CurrencyUnit.of("USD"),
            dev.erst.fingrind.core.CurrencyUnit.of("EUR")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.BookFunctionalCurrencyMismatch(
                dev.erst.fingrind.core.CurrencyUnit.of("USD"),
                dev.erst.fingrind.core.CurrencyUnit.of("EUR"))));
    assertEquals(
        new PostingRejection.OpeningBalanceTouchesNominalAccount(
            new AccountCode("4000"), AccountType.REVENUE),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.OpeningBalanceTouchesNominalAccount(
                new AccountCode("4000"), AccountType.REVENUE)));
    assertEquals(
        new PostingRejection.ClosingEquityAccountReserved(new AccountCode("3200")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.ClosingEquityAccountReserved(new AccountCode("3200"))));
    assertEquals(
        new PostingRejection.ReversalTargetNotFound(
            new dev.erst.fingrind.core.PostingId("posting-1")),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingPostingRejection.ReversalTargetNotFound(
                new dev.erst.fingrind.core.PostingId("posting-1"))));
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
}
