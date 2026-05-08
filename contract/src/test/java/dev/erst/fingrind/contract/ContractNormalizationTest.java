package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
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
import org.junit.jupiter.api.Test;

/** Covers shared collection validation paths across public contract records. */
class ContractNormalizationTest {
  private static final DeclaredAccount CASH_ACCOUNT =
      new DeclaredAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          true,
          Instant.parse("2026-04-07T10:15:30Z"));

  @Test
  void collectionBearingRecords_rejectNullListsWithFieldContext() {
    assertEquals(
        "openingBalances must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new AccountLedgerReport(
                        CASH_ACCOUNT,
                        EffectiveDateRange.unbounded(),
                        nullOf(),
                        List.of(),
                        List.of()))
            .getMessage());
    assertEquals(
        "entries must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new AccountLedgerReport(
                        CASH_ACCOUNT,
                        EffectiveDateRange.unbounded(),
                        List.of(),
                        nullOf(),
                        List.of()))
            .getMessage());
    assertEquals(
        "closingBalances must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new AccountLedgerReport(
                        CASH_ACCOUNT,
                        EffectiveDateRange.unbounded(),
                        List.of(),
                        List.of(),
                        nullOf()))
            .getMessage());
    assertEquals(
        "currencyTotals must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new PeriodSummaryReport(
                        LocalDate.parse("2026-04-01"),
                        LocalDate.parse("2026-04-30"),
                        1,
                        2,
                        1,
                        nullOf(),
                        List.of()))
            .getMessage());
    assertEquals(
        "accountActivity must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new PeriodSummaryReport(
                        LocalDate.parse("2026-04-01"),
                        LocalDate.parse("2026-04-30"),
                        1,
                        2,
                        1,
                        List.of(),
                        nullOf()))
            .getMessage());
    assertEquals(
        "balances must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new AccountBalanceSnapshot(
                        CASH_ACCOUNT, Optional.empty(), Optional.empty(), nullOf()))
            .getMessage());
    assertEquals(
        "accounts must not be null.",
        assertThrows(
                NullPointerException.class, () -> new AccountPage(nullOf(), 50, Optional.empty()))
            .getMessage());
    assertEquals(
        "postings must not be null.",
        assertThrows(
                NullPointerException.class, () -> new PostingPage(nullOf(), 10, Optional.empty()))
            .getMessage());
    assertEquals(
        "facts must not be null.",
        assertThrows(
                NullPointerException.class,
                () -> new LedgerStepFailure("rejected", "Rejected.", nullOf()))
            .getMessage());
  }

  @Test
  void requiredNonEmptyCollections_rejectNullDirectly() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    assertEquals(
        "steps",
        assertThrows(
                NullPointerException.class,
                () -> new LedgerPlan(new LedgerPlanId("plan-1"), nullOf()))
            .getMessage());
    assertEquals(
        "steps",
        assertThrows(
                NullPointerException.class,
                () -> new LedgerExecutionJournal(startedAt, finishedAt, nullOf()))
            .getMessage());
    assertEquals(
        "facts must not be null.",
        assertThrows(NullPointerException.class, () -> LedgerFact.group("group", nullOf()))
            .getMessage());
    assertEquals(
        "violations must not be null.",
        assertThrows(
                NullPointerException.class,
                () -> new PostingRejection.AccountStateViolations(nullOf()))
            .getMessage());
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
