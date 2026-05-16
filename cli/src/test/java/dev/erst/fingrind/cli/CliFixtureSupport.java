package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.ClosedPeriod;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalStep;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared CLI fixture helpers and sample payloads for split command tests. */
class CliFixtureSupport extends CliIoFixtureSupport {
  protected static DeclaredAccount declaredAccount(
      String accountCode, String accountName, NormalBalance normalBalance) {
    return declaredAccount(
        accountCode,
        accountName,
        fixtureAccountType(normalBalance),
        normalBalance,
        true,
        Instant.parse("2026-04-07T10:15:30Z"));
  }

  private static AccountType fixtureAccountType(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case DEBIT -> AccountType.ASSET;
      case CREDIT -> AccountType.REVENUE;
    };
  }

  protected static Money money(String currencyCode, String amount) {
    return Money.parse(currencyCode, amount);
  }

  protected static PostingFact reversalPostingFact() {
    return new PostingFact(
        new PostingId("posting-1"),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"), JournalLine.EntrySide.DEBIT, money("EUR", "10.00")),
                new JournalLine(
                    new AccountCode("2000"), JournalLine.EntrySide.CREDIT, money("EUR", "10.00")))),
        PostingLineage.reversal(
            new ReversalReference(new PostingId("posting-0")), new ReversalReason("Correction")),
        PostingKind.STANDARD,
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey("idem-1"),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  protected static PostingFact selfPostingFact() {
    return new PostingFact(
        new PostingId("posting-self"),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"), JournalLine.EntrySide.DEBIT, money("EUR", "5.00")),
                new JournalLine(
                    new AccountCode("1000"), JournalLine.EntrySide.CREDIT, money("EUR", "5.00")))),
        PostingLineage.direct(),
        PostingKind.STANDARD,
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-2"),
                ActorType.HUMAN,
                new CommandId("command-2"),
                new IdempotencyKey("idem-2"),
                new CausationId("cause-2"),
                Optional.empty()),
            Instant.parse("2026-04-07T10:20:30Z"),
            SourceChannel.CLI));
  }

  protected static CurrencyBalance eurDebitBalance() {
    return CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "4.00"));
  }

  protected static AccountBalanceSnapshot accountBalanceSnapshot(
      DeclaredAccount account, CurrencyBalance balance) {
    return new AccountBalanceSnapshot(
        bookIdentity(),
        account,
        Optional.of(LocalDate.parse("2026-04-01")),
        Optional.of(LocalDate.parse("2026-04-30")),
        allPostingKinds(),
        List.of(balance));
  }

  protected static TrialBalanceReport trialBalanceReport(
      DeclaredAccount account, CurrencyBalance balance) {
    return new TrialBalanceReport(
        bookIdentity(),
        Optional.of(LocalDate.parse("2026-04-30")),
        EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
        allPostingKinds(),
        List.of(new TrialBalanceRow(account, balance)),
        List.of());
  }

  protected static AccountLedgerReport accountLedgerReport(
      DeclaredAccount account, PostingFact postingFact, CurrencyBalance balance) {
    return new AccountLedgerReport(
        bookIdentity(),
        account,
        EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        allPostingKinds(),
        List.of(balance),
        List.of(
            new AccountLedgerEntry(postingFact, balance, money("EUR", "6.00"), BalanceSide.DEBIT)),
        List.of(balance));
  }

  protected static AccountLedgerReport selfLedgerReport(
      DeclaredAccount account, PostingFact postingFact) {
    return new AccountLedgerReport(
        bookIdentity(),
        account,
        EffectiveDateRange.unbounded(),
        allPostingKinds(),
        List.of(),
        List.of(
            new AccountLedgerEntry(
                postingFact,
                CurrencyBalance.ofTotals(money("EUR", "5.00"), money("EUR", "5.00")),
                money("EUR", "0.00"),
                BalanceSide.ZERO)),
        List.of());
  }

  protected static PeriodSummaryReport periodSummaryReport(
      DeclaredAccount revenueAccount, CurrencyBalance balance) {
    return new PeriodSummaryReport(
        bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        allPostingKinds(),
        1,
        2,
        2,
        List.of(new PeriodCurrencySummary(balance)),
        List.of(new PeriodAccountActivityRow(revenueAccount, balance)));
  }

  protected static TrialBalanceReport sampleTrialBalanceReport() {
    return new TrialBalanceReport(
        bookIdentity(),
        Optional.of(LocalDate.parse("2026-04-30")),
        EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
        allPostingKinds(),
        List.of(
            new TrialBalanceRow(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z")),
                CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00")))),
        List.of());
  }

  protected static dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot
      sampleAccountBalanceSnapshot() {
    DeclaredAccount cashAccount =
        declaredAccount(
            "1000",
            "Cash",
            dev.erst.fingrind.core.AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T12:00:00Z"));
    return new dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot(
        bookIdentity(),
        cashAccount,
        Optional.of(LocalDate.parse("2026-04-01")),
        Optional.of(LocalDate.parse("2026-04-30")),
        allPostingKinds(),
        List.of(CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00"))));
  }

  protected static dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport
      sampleAccountLedgerReport() {
    DeclaredAccount cashAccount =
        declaredAccount(
            "1000",
            "Cash",
            dev.erst.fingrind.core.AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T12:00:00Z"));
    return new dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport(
        bookIdentity(),
        cashAccount,
        new dev.erst.fingrind.core.EffectiveDateRange.Bounded(
            LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        allPostingKinds(),
        List.of(CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "0.00"))),
        List.of(),
        List.of(CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00"))));
  }

  protected static dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport
      samplePeriodSummaryReport() {
    return new dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport(
        bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        allPostingKinds(),
        1,
        2,
        1,
        List.of(
            new dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary(
                CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "10.00")))),
        List.of());
  }

  protected static FinancialPositionReport sampleFinancialPositionReport() {
    List<FinancialPositionSection> sections =
        List.of(
            new FinancialPositionSection(
                AccountType.ASSET,
                List.of(
                    financialPositionRow(
                        "1000",
                        "Cash",
                        AccountType.ASSET,
                        AccountRole.ORDINARY,
                        FinancialPositionLineClassification.CURRENT_ASSET,
                        CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00")))),
                List.of(CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00")))),
            new FinancialPositionSection(
                AccountType.EQUITY,
                List.of(
                    financialPositionRow(
                        "3200",
                        "Retained Earnings",
                        AccountType.EQUITY,
                        AccountRole.ORDINARY,
                        FinancialPositionLineClassification.RETAINED_EARNINGS,
                        CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "10.00")))),
                List.of(CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "10.00")))));
    return new FinancialPositionReport(
        bookIdentity(),
        Optional.of(LocalDate.parse("2026-04-30")),
        EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
        allPostingKinds(),
        sections,
        sections);
  }

  protected static IncomeStatementReport sampleIncomeStatementReport() {
    CurrencyBalance revenueMovement =
        CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "10.00"));
    List<IncomeStatementSection> sections =
        List.of(
            new IncomeStatementSection(
                AccountType.REVENUE,
                List.of(
                    incomeStatementRow(
                        "2000",
                        "Revenue",
                        AccountType.REVENUE,
                        AccountRole.ORDINARY,
                        ProfitAndLossLineClassification.OPERATING_REVENUE,
                        revenueMovement)),
                List.of(revenueMovement)));
    return new IncomeStatementReport(
        bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
        standardOnly(),
        sections,
        List.of(revenueMovement),
        sections,
        List.of(revenueMovement));
  }

  protected static ChangesInEquityReport sampleChangesInEquityReport() {
    CurrencyBalance openingBalance =
        CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "0.00"));
    CurrencyBalance movementBalance =
        CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "10.00"));
    CurrencyBalance closingBalance =
        CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "10.00"));
    List<ChangesInEquityRow> rows =
        List.of(
            changesInEquityRow(
                "3200",
                "Retained Earnings",
                AccountRole.ORDINARY,
                FinancialPositionLineClassification.RETAINED_EARNINGS,
                openingBalance,
                movementBalance,
                closingBalance));
    return new ChangesInEquityReport(
        bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
        allPostingKinds(),
        rows,
        List.of(openingBalance),
        List.of(movementBalance),
        List.of(closingBalance),
        rows,
        List.of(openingBalance),
        List.of(movementBalance),
        List.of(closingBalance));
  }

  protected static ClosedPeriod sampleClosedPeriod() {
    return new ClosedPeriod(
        1,
        new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        new AccountCode("3200"),
        List.of(CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "10.00"))),
        Instant.parse("2026-04-30T12:00:00Z"),
        List.of(new PostingId("posting-close-1")));
  }

  private static FinancialPositionRow financialPositionRow(
      String lineCode,
      String lineName,
      AccountType accountType,
      AccountRole accountRole,
      FinancialPositionLineClassification lineClassification,
      CurrencyBalance balance) {
    return new FinancialPositionRow(
        lineCode,
        lineName,
        accountType,
        Optional.of(accountRole),
        lineClassification,
        StatementLineKind.DECLARED_ACCOUNT,
        balance);
  }

  private static IncomeStatementRow incomeStatementRow(
      String lineCode,
      String lineName,
      AccountType accountType,
      AccountRole accountRole,
      ProfitAndLossLineClassification lineClassification,
      CurrencyBalance movement) {
    return new IncomeStatementRow(
        lineCode,
        lineName,
        accountType,
        Optional.of(accountRole),
        lineClassification,
        StatementLineKind.DECLARED_ACCOUNT,
        movement);
  }

  private static ChangesInEquityRow changesInEquityRow(
      String lineCode,
      String lineName,
      AccountRole accountRole,
      FinancialPositionLineClassification lineClassification,
      CurrencyBalance openingBalance,
      CurrencyBalance movement,
      CurrencyBalance closingBalance) {
    return new ChangesInEquityRow(
        lineCode,
        lineName,
        Optional.of(AccountType.EQUITY),
        Optional.of(accountRole),
        lineClassification,
        StatementLineKind.DECLARED_ACCOUNT,
        openingBalance,
        movement,
        closingBalance);
  }

  protected static LedgerPlanResult successfulPlanResult(LedgerPlanId planId) {
    Instant timestamp = fixedClock().instant();
    return new LedgerPlanResult.Succeeded(
        planId,
        new LedgerExecutionJournal(
            timestamp,
            timestamp,
            List.of(
                new LedgerJournalEntry.Succeeded(
                    stepId("inspect"),
                    LedgerJournalStep.standard(LedgerStepKind.INSPECT_BOOK),
                    timestamp,
                    timestamp,
                    List.of(LedgerFact.flag("ok", true), LedgerFact.count("count", 1))))));
  }

  protected static LedgerPlanResult assertionFailedPlanResult(String planId) {
    return assertionFailedPlanResult(planId(planId));
  }

  protected static LedgerPlanResult assertionFailedPlanResult(LedgerPlanId planId) {
    Instant timestamp = fixedClock().instant();
    LedgerStepFailure failure =
        new LedgerStepFailure("assertion-failed", "Assertion failed.", List.of());
    return new LedgerPlanResult.AssertionFailed(
        planId,
        new LedgerExecutionJournal(
            timestamp,
            timestamp,
            List.of(
                new LedgerJournalEntry.AssertionFailed(
                    stepId("assert"),
                    LedgerJournalStep.assertion(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS),
                    timestamp,
                    timestamp,
                    List.of(),
                    failure))));
  }

  protected static LedgerPlanId planId(String value) {
    return new LedgerPlanId(value);
  }

  protected static LedgerStepId stepId(String value) {
    return new LedgerStepId(value);
  }
}
