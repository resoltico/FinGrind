package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccrualCutoffKind;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Proves the durable accrual cut-off aggregate's common lifecycle invariants. */
class AccrualCutoffRecordTest {
  private static final LocalDate ORIGINATED_ON = LocalDate.parse("2026-04-07");
  private static final AccrualCutoffId CUTOFF_ID = new AccrualCutoffId("cutoff-2026-04");

  @Test
  void recordsExposeKindRemainingAmountAndLifecycleHorizon() {
    AccrualCutoffRecord.Prepayment prepayment =
        new AccrualCutoffRecord.Prepayment(
            CUTOFF_ID,
            ORIGINATED_ON,
            new AccountCode("1410"),
            new AccountCode("5000"),
            money("100.00"),
            interval(ORIGINATED_ON),
            money("25.00"),
            Optional.of(LocalDate.parse("2026-04-30")));
    AccrualCutoffRecord.DeferredRevenue deferredRevenue =
        new AccrualCutoffRecord.DeferredRevenue(
            CUTOFF_ID,
            ORIGINATED_ON,
            new AccountCode("2200"),
            new AccountCode("4000"),
            money("100.00"),
            interval(ORIGINATED_ON),
            money("0.00"),
            Optional.empty());
    AccrualCutoffRecord.AccruedExpense accruedExpense =
        new AccrualCutoffRecord.AccruedExpense(
            CUTOFF_ID,
            ORIGINATED_ON,
            new AccountCode("2100"),
            new AccountCode("5000"),
            money("100.00"),
            money("100.00"),
            Optional.of(ORIGINATED_ON));

    assertEquals(AccrualCutoffKind.PREPAYMENT, prepayment.kind());
    assertEquals(money("75.00"), prepayment.remainingAmount());
    assertEquals(LocalDate.parse("2026-04-30"), prepayment.lifecycleHorizonEffectiveDate());
    assertEquals(AccrualCutoffKind.DEFERRED_REVENUE, deferredRevenue.kind());
    assertEquals(money("100.00"), deferredRevenue.remainingAmount());
    assertEquals(ORIGINATED_ON, deferredRevenue.lifecycleHorizonEffectiveDate());
    assertEquals(AccrualCutoffKind.ACCRUED_EXPENSE, accruedExpense.kind());
    assertEquals(money("0.00"), accruedExpense.remainingAmount());
  }

  @Test
  void recordsRejectInvalidCommonAmountsAndApplicationHorizon() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccrualCutoffRecord.AccruedExpense(
                CUTOFF_ID,
                ORIGINATED_ON,
                new AccountCode("2100"),
                new AccountCode("5000"),
                money("0.00"),
                money("0.00"),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccrualCutoffRecord.AccruedExpense(
                CUTOFF_ID,
                ORIGINATED_ON,
                new AccountCode("2100"),
                new AccountCode("5000"),
                money("100.00"),
                money("100.01"),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccrualCutoffRecord.AccruedExpense(
                CUTOFF_ID,
                ORIGINATED_ON,
                new AccountCode("2100"),
                new AccountCode("5000"),
                money("100.00"),
                money("0.00"),
                Optional.of(ORIGINATED_ON.minusDays(1))));
  }

  @Test
  void recordsRejectRecognitionIntervalsBeforeTheirOrigin() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccrualCutoffRecord.Prepayment(
                CUTOFF_ID,
                ORIGINATED_ON,
                new AccountCode("1410"),
                new AccountCode("5000"),
                money("100.00"),
                interval(ORIGINATED_ON.minusDays(1)),
                money("0.00"),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccrualCutoffRecord.DeferredRevenue(
                CUTOFF_ID,
                ORIGINATED_ON,
                new AccountCode("2200"),
                new AccountCode("4000"),
                money("100.00"),
                interval(ORIGINATED_ON.minusDays(1)),
                money("0.00"),
                Optional.empty()));
  }

  private static AccrualCutoffRecognitionInterval interval(LocalDate startDate) {
    return new AccrualCutoffRecognitionInterval(startDate, LocalDate.parse("2026-05-31"));
  }

  private static Money money(String decimalAmount) {
    return Money.parse("EUR", decimalAmount);
  }
}
