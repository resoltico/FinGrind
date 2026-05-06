package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Covers shared collection normalization paths across public contract records. */
@NullUnmarked
class ContractNormalizationTest {
  private static final DeclaredAccount CASH_ACCOUNT =
      new DeclaredAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          true,
          Instant.parse("2026-04-07T10:15:30Z"));

  @Test
  void collectionBearingRecords_coalesceNullListsWhereAllowed() {
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(CASH_ACCOUNT, EffectiveDateRange.unbounded(), null, null, null);
    PeriodSummaryReport periodSummaryReport =
        new PeriodSummaryReport(
            LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"), 1, 2, 1, null, null);
    AccountBalanceSnapshot accountBalanceSnapshot =
        new AccountBalanceSnapshot(CASH_ACCOUNT, Optional.empty(), Optional.empty(), null);
    AccountPage accountPage = new AccountPage(null, 50, Optional.empty());
    PostingPage postingPage = new PostingPage(null, 10, Optional.empty());
    LedgerStepFailure failure = new LedgerStepFailure("rejected", "Rejected.", null);

    assertEquals(List.of(), accountLedgerReport.openingBalances());
    assertEquals(List.of(), accountLedgerReport.entries());
    assertEquals(List.of(), accountLedgerReport.closingBalances());
    assertEquals(List.of(), periodSummaryReport.currencyTotals());
    assertEquals(List.of(), periodSummaryReport.accountActivity());
    assertEquals(List.of(), accountBalanceSnapshot.balances());
    assertEquals(List.of(), accountPage.accounts());
    assertEquals(List.of(), postingPage.postings());
    assertEquals(List.of(), failure.facts());
  }

  @Test
  void requiredNonEmptyCollections_rejectNullByNormalizingToEmptyFirst() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");

    assertThrows(
        IllegalArgumentException.class, () -> new LedgerPlan(new LedgerPlanId("plan-1"), null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LedgerExecutionJournal(startedAt, finishedAt, null));
    assertThrows(IllegalArgumentException.class, () -> LedgerFact.group("group", null));
    assertThrows(
        IllegalArgumentException.class, () -> new PostingRejection.AccountStateViolations(null));
  }

  @Test
  void exitCodeDescriptors_rejectNegativeCodes() {
    ExitCodeDescriptor exitCode = new ExitCodeDescriptor(0, "success");

    assertEquals(0, exitCode.code());
    assertEquals("success", exitCode.meaning());
    assertThrows(IllegalArgumentException.class, () -> new ExitCodeDescriptor(-1, "invalid"));
  }

  @Test
  void reportingFixtures_stillProvideConcreteBalances() {
    CurrencyBalance balance =
        new CurrencyBalance(
            new Money(new CurrencyCode("EUR"), new BigDecimal("15.00")),
            new Money(new CurrencyCode("EUR"), BigDecimal.ZERO),
            new Money(new CurrencyCode("EUR"), new BigDecimal("15.00")),
            BalanceSide.DEBIT);

    assertEquals("EUR", balance.netAmount().currencyCode().value());
  }

  @Test
  void currencyBalance_rejectsMixedCurrencies() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CurrencyBalance(
                new Money(new CurrencyCode("EUR"), new BigDecimal("15.00")),
                new Money(new CurrencyCode("USD"), BigDecimal.ZERO),
                new Money(new CurrencyCode("EUR"), new BigDecimal("15.00")),
                BalanceSide.DEBIT));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CurrencyBalance(
                new Money(new CurrencyCode("EUR"), new BigDecimal("15.00")),
                new Money(new CurrencyCode("EUR"), BigDecimal.ZERO),
                new Money(new CurrencyCode("USD"), new BigDecimal("15.00")),
                BalanceSide.DEBIT));
  }
}
