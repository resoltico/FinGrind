package dev.erst.fingrind.contract.bookkeeping;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccrualCutoffApplicationKind;
import dev.erst.fingrind.core.AccrualCutoffKind;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Contract coverage for the typed accrual cut-off event family and its resolved journals. */
class AccrualCutoffBookkeepingEntryVariantsTest {
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-25");
  private static final AccountCode CASH = new AccountCode("cash");
  private static final AccountCode EXPENSE = new AccountCode("operating-expense");
  private static final AccountCode REVENUE = new AccountCode("service-revenue");
  private static final AccountCode PREPAID_EXPENSE = new AccountCode("prepaid-expense");
  private static final AccountCode DEFERRED_REVENUE = new AccountCode("deferred-revenue");
  private static final AccountCode ACCRUED_EXPENSE = new AccountCode("accrued-expense");

  @Test
  void originVariants_publishStableKindsOriginsAndPairedJournals() {
    AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment =
        new AccrualCutoffBookkeepingEntryVariants.Prepayment(
            EFFECTIVE_DATE,
            cutoffId("prepayment-2026-q2"),
            PREPAID_EXPENSE,
            EXPENSE,
            CASH,
            money("120000"),
            interval());
    AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue =
        new AccrualCutoffBookkeepingEntryVariants.DeferredRevenue(
            EFFECTIVE_DATE,
            cutoffId("deferred-revenue-2026-q2"),
            CASH,
            DEFERRED_REVENUE,
            REVENUE,
            money("120000"),
            interval());
    AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense =
        new AccrualCutoffBookkeepingEntryVariants.AccruedExpense(
            EFFECTIVE_DATE,
            cutoffId("accrued-expense-2026-04"),
            EXPENSE,
            ACCRUED_EXPENSE,
            money("120000"));

    assertOriginEntry(
        prepayment,
        BookkeepingEntryKind.PREPAYMENT,
        PostingOriginKind.PREPAYMENT,
        PREPAID_EXPENSE,
        CASH);
    assertOriginEntry(
        deferredRevenue,
        BookkeepingEntryKind.DEFERRED_REVENUE,
        PostingOriginKind.DEFERRED_REVENUE,
        CASH,
        DEFERRED_REVENUE);
    assertOriginEntry(
        accruedExpense,
        BookkeepingEntryKind.ACCRUED_EXPENSE,
        PostingOriginKind.ACCRUED_EXPENSE,
        EXPENSE,
        ACCRUED_EXPENSE);
  }

  @Test
  void applicationVariants_requireExecutorResolutionAndPublishResolvedJournals() {
    ResolvedAccrualCutoffApplication recognitionResolution =
        new ResolvedAccrualCutoffApplication(
            AccrualCutoffKind.PREPAYMENT,
            AccrualCutoffApplicationKind.RECOGNITION,
            EXPENSE,
            PREPAID_EXPENSE);
    ResolvedAccrualCutoffApplication settlementResolution =
        new ResolvedAccrualCutoffApplication(
            AccrualCutoffKind.ACCRUED_EXPENSE,
            AccrualCutoffApplicationKind.SETTLEMENT,
            ACCRUED_EXPENSE,
            CASH);
    AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition =
        new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
            EFFECTIVE_DATE, cutoffId("prepayment-2026-q2"), money("40000"), recognitionResolution);
    AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement =
        new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
            EFFECTIVE_DATE,
            cutoffId("accrued-expense-2026-04"),
            CASH,
            money("40000"),
            settlementResolution);
    AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition unresolvedRecognition =
        new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
            EFFECTIVE_DATE, cutoffId("prepayment-2026-q3"), money("40000"), null);
    AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement unresolvedSettlement =
        new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
            EFFECTIVE_DATE, cutoffId("accrued-expense-2026-05"), CASH, money("40000"), null);

    assertOriginEntry(
        recognition,
        BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION,
        PostingOriginKind.ACCRUAL_CUTOFF_RECOGNITION,
        EXPENSE,
        PREPAID_EXPENSE);
    assertOriginEntry(
        settlement,
        BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT,
        PostingOriginKind.ACCRUED_EXPENSE_SETTLEMENT,
        ACCRUED_EXPENSE,
        CASH);
    assertEquals(
        "accrualCutoffRecognition requires executor-resolved accrual cut-off facts.",
        assertThrows(IllegalStateException.class, unresolvedRecognition::journalEntry)
            .getMessage());
    assertEquals(
        "accruedExpenseSettlement requires executor-resolved accrual cut-off facts.",
        assertThrows(IllegalStateException.class, unresolvedSettlement::journalEntry).getMessage());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
                EFFECTIVE_DATE,
                cutoffId("prepayment-2026-q4"),
                money("40000"),
                settlementResolution));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
                EFFECTIVE_DATE,
                cutoffId("accrued-expense-2026-06"),
                CASH,
                money("40000"),
                recognitionResolution));
  }

  @Test
  void constructionRejectsRecognitionIntervalsBeforeTheirOriginDate() {
    AccrualCutoffRecognitionInterval invalidInterval =
        new AccrualCutoffRecognitionInterval(EFFECTIVE_DATE.minusDays(1), EFFECTIVE_DATE);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccrualCutoffBookkeepingEntryVariants.Prepayment(
                EFFECTIVE_DATE,
                cutoffId("prepayment-2026-q1"),
                PREPAID_EXPENSE,
                EXPENSE,
                CASH,
                money("120000"),
                invalidInterval));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccrualCutoffBookkeepingEntryVariants.DeferredRevenue(
                EFFECTIVE_DATE,
                cutoffId("deferred-revenue-2026-q1"),
                CASH,
                DEFERRED_REVENUE,
                REVENUE,
                money("120000"),
                invalidInterval));
  }

  @Test
  void identifiersIntervalsAndResolvedApplications_enforceTheirPublishedInvariants() {
    assertEquals("prepayment-2026-q2", cutoffId(" prepayment-2026-q2 ").value());
    assertEquals("[a-z0-9]+(?:-[a-z0-9]+)*", AccrualCutoffId.pattern());
    assertEquals(120, AccrualCutoffId.maxLength());
    assertThrows(IllegalArgumentException.class, () -> cutoffId("PREPAYMENT"));
    assertThrows(IllegalArgumentException.class, () -> cutoffId(" "));
    assertThrows(IllegalArgumentException.class, () -> cutoffId("a".repeat(121)));

    AccrualCutoffRecognitionInterval interval = interval();
    assertTrue(interval.contains(EFFECTIVE_DATE));
    assertTrue(interval.contains(EFFECTIVE_DATE.plusMonths(2)));
    assertFalse(interval.contains(EFFECTIVE_DATE.minusDays(1)));
    assertFalse(interval.contains(EFFECTIVE_DATE.plusMonths(3)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AccrualCutoffRecognitionInterval(EFFECTIVE_DATE, EFFECTIVE_DATE.minusDays(1)));
    assertThrows(NullPointerException.class, () -> interval.contains(nullOf()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ResolvedAccrualCutoffApplication(
                AccrualCutoffKind.PREPAYMENT,
                AccrualCutoffApplicationKind.RECOGNITION,
                PREPAID_EXPENSE,
                PREPAID_EXPENSE));
    ResolvedAccrualCutoffApplication recognitionResolution =
        new ResolvedAccrualCutoffApplication(
            AccrualCutoffKind.PREPAYMENT,
            AccrualCutoffApplicationKind.RECOGNITION,
            EXPENSE,
            PREPAID_EXPENSE);
    assertEquals(
        "recognition requires executor-resolved applicationKind SETTLEMENT.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    AccrualCutoffBookkeepingEntrySurfaceSupport.requireResolvedApplication(
                        recognitionResolution,
                        AccrualCutoffApplicationKind.SETTLEMENT,
                        "recognition"))
            .getMessage());
  }

  private static void assertOriginEntry(
      AccrualCutoffBookkeepingEntryVariants entry,
      BookkeepingEntryKind expectedEntryKind,
      PostingOriginKind expectedOriginKind,
      AccountCode debitAccountCode,
      AccountCode creditAccountCode) {
    assertEquals(expectedEntryKind, entry.entryKind());
    assertEquals(PostingKind.STANDARD, entry.postingKind());
    assertEquals(expectedOriginKind, entry.postingOriginKind());
    assertEquals(debitAccountCode, entry.journalEntry().lines().getFirst().accountCode());
    assertEquals(creditAccountCode, entry.journalEntry().lines().getLast().accountCode());
  }

  private static AccrualCutoffId cutoffId(String value) {
    return new AccrualCutoffId(value);
  }

  private static AccrualCutoffRecognitionInterval interval() {
    return new AccrualCutoffRecognitionInterval(EFFECTIVE_DATE, EFFECTIVE_DATE.plusMonths(2));
  }

  private static MonetaryAmount money(String minorUnits) {
    return new MonetaryAmount("EUR", minorUnits);
  }
}
