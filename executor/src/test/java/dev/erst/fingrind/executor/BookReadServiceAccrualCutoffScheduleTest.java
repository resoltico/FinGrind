package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.readService;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleQuery;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleReport;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccrualCutoffKind;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Exercises published accrual cut-off schedule projection through the read service. */
class BookReadServiceAccrualCutoffScheduleTest {
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");
  private static final LocalDate AS_OF = LocalDate.parse("2026-04-30");

  @Test
  void accrualCutoffSchedule_rejectsAnUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      assertEquals(
          new AccrualCutoffScheduleResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          readService(bookSession)
              .accrualCutoffSchedule(new AccrualCutoffScheduleQuery(Optional.empty())));
    }
  }

  @Test
  void accrualCutoffSchedule_projectsExactLifecycleAmountsThroughTheSelectedAsOfDate() {
    AccrualCutoffRecord.Prepayment aprilPrepayment =
        new AccrualCutoffRecord.Prepayment(
            new AccrualCutoffId("prepayment-2026"),
            LocalDate.parse("2026-04-01"),
            new AccountCode("prepaid-insurance"),
            new AccountCode("insurance-expense"),
            Money.ofMinorUnits(EUR, 12_000),
            new AccrualCutoffRecognitionInterval(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-12-31")),
            Money.ofMinorUnits(EUR, 3_000),
            Optional.of(AS_OF));
    AccrualCutoffRecord.DeferredRevenue aprilDeferredRevenue =
        new AccrualCutoffRecord.DeferredRevenue(
            new AccrualCutoffId("deferred-revenue-2026"),
            LocalDate.parse("2026-04-02"),
            new AccountCode("deferred-support-revenue"),
            new AccountCode("support-revenue"),
            Money.ofMinorUnits(EUR, 8_000),
            new AccrualCutoffRecognitionInterval(
                LocalDate.parse("2026-04-02"), LocalDate.parse("2026-12-31")),
            Money.ofMinorUnits(EUR, 2_000),
            Optional.of(LocalDate.parse("2026-04-20")));
    AccrualCutoffRecord.AccruedExpense aprilAccruedExpense =
        new AccrualCutoffRecord.AccruedExpense(
            new AccrualCutoffId("accrued-expense-2026"),
            LocalDate.parse("2026-04-03"),
            new AccountCode("accrued-legal-expense"),
            new AccountCode("legal-expense"),
            Money.ofMinorUnits(EUR, 5_000),
            Money.ofMinorUnits(EUR, 1_000),
            Optional.of(LocalDate.parse("2026-04-21")));
    StatementBookStore bookStore =
        new StatementBookStore(
            List.of(),
            List.of(),
            List.of(),
            List.of(aprilPrepayment, aprilDeferredRevenue, aprilAccruedExpense));

    AccrualCutoffScheduleReport report =
        assertInstanceOf(
                AccrualCutoffScheduleResult.Reported.class,
                new BookReadService(bookStore, bookStore)
                    .accrualCutoffSchedule(new AccrualCutoffScheduleQuery(Optional.of(AS_OF))))
            .report();

    assertEquals(bookIdentity(), report.bookIdentity());
    assertEquals(Optional.of(AS_OF), report.effectiveDateAsOf());
    assertEquals(3, report.rows().size());
    var prepayment = report.rows().get(0);
    assertEquals("prepayment-2026", prepayment.accrualCutoffId().value());
    assertEquals(AccrualCutoffKind.PREPAYMENT, prepayment.kind());
    assertEquals(Money.ofMinorUnits(EUR, 3_000), prepayment.appliedAmount().toMoney());
    assertEquals(Money.ofMinorUnits(EUR, 9_000), prepayment.remainingAmount().toMoney());
    assertEquals(Optional.of(LocalDate.parse("2026-04-01")), prepayment.recognitionStartDate());
    assertEquals(Optional.of(AS_OF), prepayment.latestApplicationEffectiveDate());

    var deferredRevenue = report.rows().get(1);
    assertEquals("deferred-revenue-2026", deferredRevenue.accrualCutoffId().value());
    assertEquals(AccrualCutoffKind.DEFERRED_REVENUE, deferredRevenue.kind());
    assertEquals(new AccountCode("deferred-support-revenue"), deferredRevenue.cutoffAccountCode());
    assertEquals(new AccountCode("support-revenue"), deferredRevenue.recognitionAccountCode());
    assertEquals(Money.ofMinorUnits(EUR, 6_000), deferredRevenue.remainingAmount().toMoney());

    var accruedExpense = report.rows().get(2);
    assertEquals("accrued-expense-2026", accruedExpense.accrualCutoffId().value());
    assertEquals(AccrualCutoffKind.ACCRUED_EXPENSE, accruedExpense.kind());
    assertEquals(new AccountCode("accrued-legal-expense"), accruedExpense.cutoffAccountCode());
    assertEquals(new AccountCode("legal-expense"), accruedExpense.recognitionAccountCode());
    assertEquals(Optional.empty(), accruedExpense.recognitionStartDate());
    assertEquals(Optional.empty(), accruedExpense.recognitionEndDate());
  }
}
