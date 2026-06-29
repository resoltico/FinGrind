package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.openedBook;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.ClosedFiscalYear;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseCommand;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceDocumentType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the bookkeeping published-language translator. */
class BookkeepingPublishedLanguageTranslatorTest {
  private static final List<String> ENTRY_SEMANTICS_CANONICAL_CODES =
      List.of(
          "economic-null-journal",
          "cash-basis-account-required",
          "distinct-role-accounts-required",
          "account-type-mismatch",
          "cash-flow-asset-classification-mismatch",
          "financial-position-classification-mismatch",
          "source-document-type-not-accepted",
          "unknown-tax-registration",
          "unknown-tax-code",
          "tax-application-kind-mismatch");

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
                java.util.Optional.of(FinancialPositionLineClassification.NONCURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.NON_CASH))),
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
  void bookkeepingEntrySemanticsCodes_matchThePublishedCanonicalDescriptorOwner() {
    assertEquals(
        ENTRY_SEMANTICS_CANONICAL_CODES,
        PostingRejection.descriptors().stream()
            .filter(descriptor -> "entry-semantics-violations".equals(descriptor.code()))
            .findFirst()
            .orElseThrow()
            .detailRejections()
            .stream()
            .map(dev.erst.fingrind.contract.runtime.ContractResponse.RejectionDescriptor::code)
            .toList());
    assertEquals(
        ENTRY_SEMANTICS_CANONICAL_CODES,
        Arrays.stream(BookkeepingEntrySemanticsViolationFactory.class.getDeclaredMethods())
            .filter(
                method ->
                    Modifier.isStatic(method.getModifiers())
                        && method.getReturnType()
                            == BookkeepingPostingRejection.EntrySemanticsViolation.class
                        && method.getParameterCount() > 0
                        && method.getParameterTypes()[0] == String.class)
            .map(BookkeepingPublishedLanguageTranslatorTest::invokeBookkeepingEntrySemanticsFactory)
            .map(BookkeepingPostingRejection.EntrySemanticsViolation::code)
            .distinct()
            .sorted(java.util.Comparator.comparingInt(ENTRY_SEMANTICS_CANONICAL_CODES::indexOf))
            .toList());
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
        reportingPeriod,
        BookkeepingRequestPublishedLanguageTranslator.fromPublished(
            new InterimResultSweepCommand(reportingPeriod)));
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
            new BookAdministrationRejection.CloseTargetAccountCandidateMissing(
                FinancialPositionLineClassification.RESULT_HOLDING, List.of())),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new InterimResultSweepOutcome.Rejected(
                new BookkeepingAdministrationRejection.CloseTargetAccountCandidateMissing(
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
        reportingPeriod,
        BookkeepingRequestPublishedLanguageTranslator.fromPublished(
            new FiscalYearCloseCommand(reportingPeriod)));
    assertEquals(
        new FiscalYearCloseResult.Closed(
            new ClosedFiscalYear(
                1,
                reportingPeriod,
                new AccountCode("3000"),
                new AccountCode("3200"),
                new AccountCode("3300"),
                closedAt,
                List.of(new dev.erst.fingrind.core.PostingId("posting-1")))),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new FiscalYearCloseOutcome.Closed(closedFiscalYear)));
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
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                List.of(new AccountCode("1099")).stream().findFirst(),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)));
    assertEquals(
        new BookAdministrationRejection.AccountTaxonomyConflict(
            new AccountCode("1000"),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                List.of(new AccountCode("1000")).stream().findFirst(),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                List.of(new AccountCode("1099")).stream().findFirst(),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT))),
        BookkeepingPublishedLanguageTranslator.toPublished(localAccountTaxonomyConflict));
    assertEquals(
        new BookAdministrationRejection.CloseTargetAccountCandidateMissing(
            FinancialPositionLineClassification.RESULT_HOLDING, List.of(new AccountCode("3200"))),
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookkeepingAdministrationRejection.CloseTargetAccountCandidateMissing(
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
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
            new AccountCode("1000"),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                java.util.Optional.of(new AccountCode("0900")),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)));
    assertEquals(
        new BookAdministrationRejection.ParentAccountTaxonomyConflict(
            new AccountCode("1010"),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                java.util.Optional.of(new AccountCode("1000")),
                java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                java.util.Optional.empty(),
                java.util.Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
            new AccountCode("1000"),
            new dev.erst.fingrind.core.AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                java.util.Optional.of(new AccountCode("0900")),
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
                    BookkeepingEntrySemanticsViolationFactory.accountTypeMismatch(
                        "entryKind",
                        "SALE",
                        "cashAccountCode",
                        new AccountCode("2000"),
                        AccountType.ASSET,
                        AccountType.REVENUE),
                    BookkeepingEntrySemanticsViolationFactory.sourceDocumentTypeNotAccepted(
                        "entryKind",
                        "SALE",
                        new SourceDocumentType("invoice"),
                        List.of("cash-receipt", "bank-deposit", "card-settlement"))))));
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

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeBookkeepingEntrySemanticsFactory(Method method) {
    try {
      return switch (method.getName()) {
        case "accountTypeMismatch" -> invokeAccountTypeMismatch(method);
        case "cashBasisAccountRequired" -> invokeCashBasisAccountRequired(method);
        case "cashFlowAssetClassificationMismatch" ->
            invokeCashFlowAssetClassificationMismatch(method);
        case "financialPositionClassificationMismatch" ->
            invokeFinancialPositionClassificationMismatch(method);
        case "sourceDocumentTypeNotAccepted" -> invokeSourceDocumentTypeNotAccepted(method);
        case "unknownTaxRegistration" -> invokeUnknownTaxRegistration(method);
        case "unknownTaxCode" -> invokeUnknownTaxCode(method);
        case "taxApplicationKindMismatch" -> invokeTaxApplicationKindMismatch(method);
        case "distinctRoleAccountsRequired" -> invokeDistinctRoleAccountsRequired(method);
        case "economicNullJournal" -> invokeEconomicNullJournal(method);
        default ->
            throw new AssertionError("Unexpected entry-semantics factory: " + method.getName());
      };
    } catch (ReflectiveOperationException exception) {
      throw new LinkageError(exception.getMessage(), exception);
    }
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation invokeAccountTypeMismatch(
      Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "SALE",
            "cashAccountCode",
            new AccountCode("1000"),
            AccountType.ASSET,
            AccountType.REVENUE);
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeCashFlowAssetClassificationMismatch(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "SALE",
            "cashAccountCode",
            new AccountCode("1000"),
            CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT,
            CashFlowAssetClassification.NON_CASH);
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeFinancialPositionClassificationMismatch(Method method)
          throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "OWNER_CONTRIBUTION",
            "equityAccountCode",
            new AccountCode("3000"),
            FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
            null);
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeSourceDocumentTypeNotAccepted(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "SALE",
            new SourceDocumentType("invoice"),
            List.of("cash-receipt", "cash-sale"));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeDistinctRoleAccountsRequired(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "SALE",
            "cashAccountCode",
            "revenueAccountCode",
            new AccountCode("1000"));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation invokeCashBasisAccountRequired(
      Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "DIRECT_JOURNAL",
            List.of(new AccountCode("3000"), new AccountCode("3200")));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation invokeUnknownTaxRegistration(
      Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(null, "entryKind", "SALE", new TaxRegistrationId("tax-reg-1"));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation invokeUnknownTaxCode(
      Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "SALE",
            new TaxRegistrationId("tax-reg-1"),
            new TaxCode("output-std"));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeTaxApplicationKindMismatch(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "SALE",
            new TaxCode("output-std"),
            TaxApplicationKind.OUTPUT_SALE,
            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE);
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation invokeEconomicNullJournal(
      Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(null, "entryKind", "DIRECT_JOURNAL");
  }
}
