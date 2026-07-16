package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedAccrualCutoffApplication;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccrualCutoffApplicationKind;
import dev.erst.fingrind.core.AccrualCutoffKind;
import java.time.LocalDate;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Focused coverage for accrual cut-off entry payload mapping and text rendering. */
class CliAccrualCutoffPostingEntryPayloadTest {
  @Test
  void entryPayload_andRenderedFacts_publishEveryAccrualCutoffLifecycleVariant() {
    CliPostingEntryPayload prepayment = entryPayload(prepaymentEntry());
    CliPostingEntryPayload deferredRevenue = entryPayload(deferredRevenueEntry());
    CliPostingEntryPayload accruedExpense = entryPayload(accruedExpenseEntry());
    CliPostingEntryPayload recognition = entryPayload(recognitionEntry());
    CliPostingEntryPayload settlement = entryPayload(settlementEntry());

    assertAccrualCutoffPayload(prepayment, "PREPAYMENT", "prepayment-2026-q1");
    assertEquals(
        "prepaid-expense",
        Objects.requireNonNull(prepayment.accrualCutoff()).prepaymentAssetAccountCode());
    assertEquals(
        "2026-01-15",
        Objects.requireNonNull(prepayment.accrualCutoff().recognitionInterval()).startDate());
    assertAccrualCutoffPayload(deferredRevenue, "DEFERRED_REVENUE", "deferred-revenue-2026-q1");
    assertEquals(
        "deferred-revenue",
        Objects.requireNonNull(deferredRevenue.accrualCutoff()).deferredRevenueAccountCode());
    assertAccrualCutoffPayload(accruedExpense, "ACCRUED_EXPENSE", "accrued-expense-2026-01");
    assertEquals(
        "accrued-expense",
        Objects.requireNonNull(accruedExpense.accrualCutoff())
            .accruedExpenseLiabilityAccountCode());
    assertNull(Objects.requireNonNull(accruedExpense.accrualCutoff()).recognitionInterval());
    CliPostingEntryPayload.AccrualCutoffPayload recognitionCutoff =
        Objects.requireNonNull(recognition.accrualCutoff());
    CliPostingEntryPayload.AccrualCutoffPayload settlementCutoff =
        Objects.requireNonNull(settlement.accrualCutoff());
    assertNull(recognitionCutoff.aggregateKind());
    assertEquals(
        "RECOGNITION",
        Objects.requireNonNull(recognitionCutoff.resolvedApplication()).applicationKind());
    assertEquals(
        "SETTLEMENT",
        Objects.requireNonNull(settlementCutoff.resolvedApplication()).applicationKind());

    String prepaymentFacts = CliPostingEntryPayloadSupport.renderEntryFacts(prepayment);
    String recognitionFacts = CliPostingEntryPayloadSupport.renderEntryFacts(recognition);
    assertTrue(prepaymentFacts.contains("Accrual cut-off id"));
    assertTrue(prepaymentFacts.contains("Accrual cut-off kind"));
    assertTrue(prepaymentFacts.contains("Prepaid expense account"));
    assertTrue(prepaymentFacts.contains("Recognition interval"));
    assertTrue(recognitionFacts.contains("Resolved accrual application"));
    assertTrue(recognitionFacts.contains("Resolved debit account"));
    assertTrue(recognitionFacts.contains("Resolved credit account"));
  }

  @Test
  void accrualCutoffPayload_rejectsMissingOrConflictingLifecycleFacts() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPostingEntryPayload.AccrualCutoffPayload(
                "cutoff-1", null, null, null, null, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPostingEntryPayload.AccrualCutoffPayload(
                "cutoff-1",
                "PREPAYMENT",
                "prepaid-expense",
                null,
                null,
                new CliPostingEntryPayload.RecognitionIntervalPayload("2026-01-01", "2026-01-31"),
                new CliPostingEntryPayload.ResolvedApplicationPayload(
                    "RECOGNITION", "prepaid-expense", "operating-expense")));
  }

  private static AccrualCutoffBookkeepingEntryVariants.Prepayment prepaymentEntry() {
    return new AccrualCutoffBookkeepingEntryVariants.Prepayment(
        LocalDate.parse("2026-01-15"),
        new AccrualCutoffId("prepayment-2026-q1"),
        new AccountCode("prepaid-expense"),
        new AccountCode("operating-expense"),
        new AccountCode("cash"),
        new MonetaryAmount("EUR", "12000"),
        recognitionInterval());
  }

  private static AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenueEntry() {
    return new AccrualCutoffBookkeepingEntryVariants.DeferredRevenue(
        LocalDate.parse("2026-01-15"),
        new AccrualCutoffId("deferred-revenue-2026-q1"),
        new AccountCode("cash"),
        new AccountCode("deferred-revenue"),
        new AccountCode("sales-revenue"),
        new MonetaryAmount("EUR", "12000"),
        recognitionInterval());
  }

  private static AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpenseEntry() {
    return new AccrualCutoffBookkeepingEntryVariants.AccruedExpense(
        LocalDate.parse("2026-01-31"),
        new AccrualCutoffId("accrued-expense-2026-01"),
        new AccountCode("operating-expense"),
        new AccountCode("accrued-expense"),
        new MonetaryAmount("EUR", "12000"));
  }

  private static AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognitionEntry() {
    return new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
        LocalDate.parse("2026-02-01"),
        new AccrualCutoffId("prepayment-2026-q1"),
        new MonetaryAmount("EUR", "4000"),
        new ResolvedAccrualCutoffApplication(
            AccrualCutoffKind.PREPAYMENT,
            AccrualCutoffApplicationKind.RECOGNITION,
            new AccountCode("operating-expense"),
            new AccountCode("prepaid-expense")));
  }

  private static AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlementEntry() {
    return new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
        LocalDate.parse("2026-02-15"),
        new AccrualCutoffId("accrued-expense-2026-01"),
        new AccountCode("cash"),
        new MonetaryAmount("EUR", "12000"),
        new ResolvedAccrualCutoffApplication(
            AccrualCutoffKind.ACCRUED_EXPENSE,
            AccrualCutoffApplicationKind.SETTLEMENT,
            new AccountCode("accrued-expense"),
            new AccountCode("cash")));
  }

  private static AccrualCutoffRecognitionInterval recognitionInterval() {
    return new AccrualCutoffRecognitionInterval(
        LocalDate.parse("2026-01-15"), LocalDate.parse("2026-03-31"));
  }

  private static void assertAccrualCutoffPayload(
      CliPostingEntryPayload payload, String aggregateKind, String accrualCutoffId) {
    CliPostingEntryPayload.AccrualCutoffPayload accrualCutoff =
        Objects.requireNonNull(payload.accrualCutoff());
    assertEquals(aggregateKind, accrualCutoff.aggregateKind());
    assertEquals(accrualCutoffId, accrualCutoff.accrualCutoffId());
  }

  private static CliPostingEntryPayload entryPayload(
      dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry entry) {
    return Objects.requireNonNull(CliPostingEntryPayloadSupport.entryPayload(entry));
  }
}
