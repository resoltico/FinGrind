package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.LedgerExecutionJournal;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerJournalEntry;
import dev.erst.fingrind.contract.LedgerJournalStep;
import dev.erst.fingrind.contract.LedgerPlanId;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerStepFailure;
import dev.erst.fingrind.contract.LedgerStepId;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodCurrencySummary;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared CLI fixture helpers and sample payloads for split command tests. */
class CliFixtureSupport extends CliIoFixtureSupport {
  protected static DeclaredAccount declaredAccount(
      String accountCode, String accountName, NormalBalance normalBalance) {
    return new DeclaredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        normalBalance,
        true,
        Instant.parse("2026-04-07T10:15:30Z"));
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
        account,
        Optional.of(LocalDate.parse("2026-04-01")),
        Optional.of(LocalDate.parse("2026-04-30")),
        List.of(balance));
  }

  protected static TrialBalanceReport trialBalanceReport(
      DeclaredAccount account, CurrencyBalance balance) {
    return new TrialBalanceReport(
        Optional.of(LocalDate.parse("2026-04-30")), List.of(new TrialBalanceRow(account, balance)));
  }

  protected static AccountLedgerReport accountLedgerReport(
      DeclaredAccount account, PostingFact postingFact, CurrencyBalance balance) {
    return new AccountLedgerReport(
        account,
        EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        List.of(balance),
        List.of(
            new AccountLedgerEntry(postingFact, balance, money("EUR", "6.00"), BalanceSide.DEBIT)),
        List.of(balance));
  }

  protected static AccountLedgerReport selfLedgerReport(
      DeclaredAccount account, PostingFact postingFact) {
    return new AccountLedgerReport(
        account,
        EffectiveDateRange.unbounded(),
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
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        1,
        2,
        2,
        List.of(new PeriodCurrencySummary(balance)),
        List.of(new PeriodAccountActivityRow(revenueAccount, balance)));
  }

  protected static TrialBalanceReport sampleTrialBalanceReport() {
    return new TrialBalanceReport(
        Optional.of(LocalDate.parse("2026-04-30")),
        List.of(
            new TrialBalanceRow(
                new DeclaredAccount(
                    new AccountCode("1000"),
                    new AccountName("Cash"),
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z")),
                CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00")))));
  }

  protected static dev.erst.fingrind.contract.AccountBalanceSnapshot
      sampleAccountBalanceSnapshot() {
    DeclaredAccount cashAccount =
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T12:00:00Z"));
    return new dev.erst.fingrind.contract.AccountBalanceSnapshot(
        cashAccount,
        Optional.of(LocalDate.parse("2026-04-01")),
        Optional.of(LocalDate.parse("2026-04-30")),
        List.of(CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00"))));
  }

  protected static dev.erst.fingrind.contract.AccountLedgerReport sampleAccountLedgerReport() {
    DeclaredAccount cashAccount =
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T12:00:00Z"));
    return new dev.erst.fingrind.contract.AccountLedgerReport(
        cashAccount,
        new dev.erst.fingrind.core.EffectiveDateRange.Bounded(
            LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        List.of(CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "0.00"))),
        List.of(),
        List.of(CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00"))));
  }

  protected static dev.erst.fingrind.contract.PeriodSummaryReport samplePeriodSummaryReport() {
    return new dev.erst.fingrind.contract.PeriodSummaryReport(
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        1,
        2,
        1,
        List.of(
            new dev.erst.fingrind.contract.PeriodCurrencySummary(
                CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "10.00")))),
        List.of());
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
