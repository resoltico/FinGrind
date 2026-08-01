package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for fiscal-year-close planning, validation, and value objects. */
class FiscalYearClosePlannerTest {
  private static final Instant CLOSED_AT = Instant.parse("2026-12-31T23:59:59Z");
  private static final ReportingPeriod FISCAL_YEAR =
      new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));

  private final FiscalYearClosePlanner planner =
      FiscalYearClosePlanner.forBookIdentity(bookIdentity());

  @Test
  void closeTargetSelections_andHorizonValidation_areDeterministic() {
    RegisteredAccount capital =
        equityAccount("3000", "Capital", FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
    RegisteredAccount resultHolding =
        equityAccount("3200", "Result Holding", FinancialPositionLineClassification.RESULT_HOLDING);
    RegisteredAccount retainedAccumulated =
        equityAccount(
            "3300",
            "Retained Accumulated",
            FinancialPositionLineClassification.RETAINED_ACCUMULATED);

    assertEquals(
        capital,
        assertInstanceOf(
                AcceptedCloseTargetSelection.class,
                planner.capitalAccount(List.of(capital, resultHolding, retainedAccumulated)))
            .account());
    assertEquals(
        resultHolding,
        assertInstanceOf(
                AcceptedCloseTargetSelection.class,
                planner.resultHoldingAccount(
                    bookIdentity(), List.of(capital, resultHolding, retainedAccumulated)))
            .account());
    assertEquals(
        retainedAccumulated,
        assertInstanceOf(
                AcceptedCloseTargetSelection.class,
                planner.retainedAccumulatedAccount(
                    List.of(capital, resultHolding, retainedAccumulated)))
            .account());

    assertEquals(
        new BookkeepingAdministrationRejection.FiscalYearCloseFutureDate(
            LocalDate.parse("2027-01-01")),
        planner
            .closeHorizonRejection(
                new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-01")),
                bookIdentity(),
                LocalDate.parse("2026-12-31"))
            .orElseThrow());
    assertEquals(
        new BookkeepingAdministrationRejection.FiscalYearCloseMustStartAt(
            LocalDate.parse("2026-01-01")),
        planner
            .closeHorizonRejection(
                new ReportingPeriod(LocalDate.parse("2026-01-02"), LocalDate.parse("2026-12-31")),
                bookIdentity(),
                LocalDate.parse("2026-12-31"))
            .orElseThrow());
    assertEquals(
        new BookkeepingAdministrationRejection.FiscalYearCloseMustEndAt(
            LocalDate.parse("2026-12-31")),
        planner
            .closeHorizonRejection(
                new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-30")),
                bookIdentity(),
                LocalDate.parse("2026-12-31"))
            .orElseThrow());
    assertEquals(
        Optional.empty(),
        planner.closeHorizonRejection(FISCAL_YEAR, bookIdentity(), LocalDate.parse("2026-12-31")));
  }

  @Test
  void reportingPeriod_preservesAWhollyPreBookFiscalYearForDeterministicRejection() {
    dev.erst.fingrind.core.BookIdentity baseline = bookIdentity();
    dev.erst.fingrind.core.BookIdentity midYearBook =
        new dev.erst.fingrind.core.BookIdentity(
            baseline.entityProfile(),
            baseline.bookDoctrine(),
            baseline.functionalCurrency(),
            baseline.fiscalYearStart(),
            LocalDate.parse("2026-07-01"));

    assertEquals(
        new ReportingPeriod(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31")),
        planner.reportingPeriod(midYearBook, 2025));
  }

  @Test
  void closeDraft_buildsUnsweptSweepAndDurableClosePostings() {
    RegisteredAccount capital =
        equityAccount("3000", "Capital", FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
    RegisteredAccount withdrawals =
        equityAccount("3100", "Owner Draw", FinancialPositionLineClassification.EQUITY_WITHDRAWAL);
    RegisteredAccount resultHolding =
        equityAccount("3200", "Result Holding", FinancialPositionLineClassification.RESULT_HOLDING);
    RegisteredAccount retainedAccumulated =
        equityAccount(
            "3300",
            "Retained Accumulated",
            FinancialPositionLineClassification.RETAINED_ACCUMULATED);
    RegisteredAccount cash = balanceSheetAccount("1000", "Cash", AccountType.ASSET);
    RegisteredAccount revenue = profitAndLossAccount("4000", "Revenue", AccountType.REVENUE);
    RegisteredAccount expense = profitAndLossAccount("5000", "Expense", AccountType.EXPENSE);

    FiscalYearCloseDraft closeDraft =
        planner.closeDraft(
            FISCAL_YEAR,
            bookIdentity(),
            capital,
            resultHolding,
            retainedAccumulated,
            List.of(
                capital, withdrawals, resultHolding, retainedAccumulated, cash, revenue, expense),
            List.of(
                posting(
                    "posting-before",
                    PostingKind.STANDARD,
                    LocalDate.parse("2025-12-31"),
                    line("1000", JournalLine.EntrySide.DEBIT, "7.00"),
                    line("4000", JournalLine.EntrySide.CREDIT, "7.00")),
                posting(
                    "posting-sale",
                    PostingKind.STANDARD,
                    LocalDate.parse("2026-12-31"),
                    line("1000", JournalLine.EntrySide.DEBIT, "120.00"),
                    line("4000", JournalLine.EntrySide.CREDIT, "120.00")),
                posting(
                    "posting-expense",
                    PostingKind.STANDARD,
                    LocalDate.parse("2026-12-31"),
                    line("5000", JournalLine.EntrySide.DEBIT, "45.00"),
                    line("1000", JournalLine.EntrySide.CREDIT, "45.00")),
                posting(
                    "posting-draw",
                    PostingKind.STANDARD,
                    LocalDate.parse("2026-12-31"),
                    line("3100", JournalLine.EntrySide.DEBIT, "10.00"),
                    line("1000", JournalLine.EntrySide.CREDIT, "10.00")),
                posting(
                    "posting-outside",
                    PostingKind.STANDARD,
                    LocalDate.parse("2027-01-01"),
                    line("1000", JournalLine.EntrySide.DEBIT, "999.00"),
                    line("4000", JournalLine.EntrySide.CREDIT, "999.00"))),
            Optional.empty(),
            CLOSED_AT);

    assertNotNull(closeDraft.unsweptInterimResultSweepDraft());
    assertEquals(FISCAL_YEAR, closeDraft.unsweptInterimResultSweepDraft().reportingPeriod());
    assertEquals(2, closeDraft.closePostingDrafts().size());
    assertEquals(
        List.of(
            new JournalLine(
                new AccountCode("3100"), JournalLine.EntrySide.CREDIT, Money.parse("EUR", "10.00")),
            new JournalLine(
                new AccountCode("3000"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "10.00"))),
        closeDraft.closePostingDrafts().getFirst().journalEntry().lines());
    assertEquals(
        List.of(
            new JournalLine(
                new AccountCode("3200"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "75.00")),
            new JournalLine(
                new AccountCode("3300"),
                JournalLine.EntrySide.CREDIT,
                Money.parse("EUR", "75.00"))),
        closeDraft.closePostingDrafts().get(1).journalEntry().lines());
  }

  @Test
  void closeDraft_omitsUnsweptSweepWhenPeriodWasAlreadySwept() {
    RegisteredAccount capital =
        equityAccount("3000", "Capital", FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
    RegisteredAccount resultHolding =
        equityAccount("3200", "Result Holding", FinancialPositionLineClassification.RESULT_HOLDING);
    RegisteredAccount retainedAccumulated =
        equityAccount(
            "3300",
            "Retained Accumulated",
            FinancialPositionLineClassification.RETAINED_ACCUMULATED);

    FiscalYearCloseDraft closeDraft =
        planner.closeDraft(
            FISCAL_YEAR,
            bookIdentity(),
            capital,
            resultHolding,
            retainedAccumulated,
            List.of(capital, resultHolding, retainedAccumulated),
            List.of(
                posting(
                    "posting-result",
                    PostingKind.INTERIM_RESULT_SWEEP,
                    LocalDate.parse("2026-12-31"),
                    line("4000", JournalLine.EntrySide.DEBIT, "5.00"),
                    line("3200", JournalLine.EntrySide.CREDIT, "5.00"))),
            Optional.of(FISCAL_YEAR.effectiveDateTo()),
            CLOSED_AT);

    assertNull(closeDraft.unsweptInterimResultSweepDraft());
    assertEquals(1, closeDraft.closePostingDrafts().size());
  }

  @Test
  @SuppressWarnings("NullAway")
  void fiscalYearCloseValueObjects_validateRequiredState() {
    IllegalArgumentException closeOrderFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ClosedFiscalYearRecord(
                    0,
                    FISCAL_YEAR,
                    new AccountCode("3000"),
                    new AccountCode("3200"),
                    new AccountCode("3300"),
                    CLOSED_AT,
                    List.of(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))));
    assertThrows(
        NullPointerException.class,
        () ->
            new FiscalYearCloseDraft(
                FISCAL_YEAR,
                new AccountCode("3000"),
                new AccountCode("3200"),
                new AccountCode("3300"),
                CLOSED_AT,
                null,
                null));
    NullPointerException closedOutcomeFailure =
        assertThrows(
            NullPointerException.class, () -> new FiscalYearCloseOutcome.Closed(null, false));
    NullPointerException rejectedOutcomeFailure =
        assertThrows(NullPointerException.class, () -> new FiscalYearCloseOutcome.Rejected(null));

    assertEquals("closeOrder must be at least one.", closeOrderFailure.getMessage());
    assertEquals("closedFiscalYear", closedOutcomeFailure.getMessage());
    assertEquals("rejection", rejectedOutcomeFailure.getMessage());
  }

  private static RegisteredAccount equityAccount(
      String accountCode, String accountName, FinancialPositionLineClassification classification) {
    return registeredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        AccountType.EQUITY,
        financialPositionTaxonomy(classification),
        true,
        CLOSED_AT);
  }

  private static RegisteredAccount balanceSheetAccount(
      String accountCode, String accountName, AccountType accountType) {
    return registeredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        accountType,
        accountTaxonomy(accountType),
        true,
        CLOSED_AT);
  }

  private static RegisteredAccount profitAndLossAccount(
      String accountCode, String accountName, AccountType accountType) {
    return registeredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        accountType,
        accountTaxonomy(accountType),
        true,
        CLOSED_AT);
  }

  private static CommittedPosting posting(
      String postingId, PostingKind postingKind, LocalDate effectiveDate, JournalLine... lines) {
    return new CommittedPosting(
        new PostingId(
            java.util
                .UUID
                .nameUUIDFromBytes(
                    ("fingrind-test-postingid:" + postingId)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString()),
        new JournalEntry(effectiveDate, List.of(lines)),
        PostingLineageModel.direct(),
        postingKind,
        postingKind == PostingKind.INTERIM_RESULT_SWEEP
            ? dev.erst.fingrind.core.PostingOriginKind.INTERIM_RESULT_SWEEP
            : dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        accountingEvidence(postingId),
        new CommittedProvenance(
            new RequestProvenance(
                dev.erst.fingrind.executor.ScenarioCommandIdentifiers.fromLabel(
                    "command-" + postingId),
                new IdempotencyKey("idem-" + postingId),
                new CausationId("cause-" + postingId),
                Optional.of(new CorrelationId("corr-" + postingId))),
            CLOSED_AT,
            SourceChannel.CLI));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }
}
