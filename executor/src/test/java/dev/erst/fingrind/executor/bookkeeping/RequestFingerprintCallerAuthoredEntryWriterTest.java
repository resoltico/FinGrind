package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage for caller-authored fingerprint canonicalization across retained entry kinds. */
class RequestFingerprintCallerAuthoredEntryWriterTest {
  @Test
  void append_coversCreditSettlementAndPatternOnlyEntryVariants() {
    String directJournalCanonical =
        canonical(
            new BookkeepingEntry.DirectJournal(
                new JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new JournalLine(
                            new AccountCode("1000"),
                            JournalLine.EntrySide.DEBIT,
                            dev.erst.fingrind.core.Money.ofMinorUnits(
                                dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1000)),
                        new JournalLine(
                            new AccountCode("4000"),
                            JournalLine.EntrySide.CREDIT,
                            dev.erst.fingrind.core.Money.ofMinorUnits(
                                dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1000)))),
                null));
    String saleOnCreditCanonical =
        canonical(
            new BookkeepingEntry.SaleOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1100"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "9200"),
                null,
                null,
                null,
                null,
                null));
    String expenseOnCreditCanonical =
        canonical(
            new BookkeepingEntry.ExpenseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("2100"),
                new MonetaryAmount("EUR", "12100"),
                null,
                null,
                null));
    String receiptCanonical =
        canonical(
            new BookkeepingEntry.Receipt(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("1100"),
                new MonetaryAmount("EUR", "1000"),
                new SettlementAdjunct(new AccountCode("5600"), new MonetaryAmount("EUR", "100"))));
    String paymentCanonical =
        canonical(
            new BookkeepingEntry.Payment(
                LocalDate.parse("2026-04-07"),
                new AccountCode("2100"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null));
    String purchaseSettledCanonical =
        canonical(
            new BookkeepingEntry.PurchaseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("1000"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null,
                null));
    String purchaseOnCreditCanonical =
        canonical(
            new BookkeepingEntry.PurchaseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("2100"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null,
                null));
    String expenseSettledCanonical =
        canonical(
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null));
    String ownerContributionCanonical =
        canonical(
            new BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("3000"),
                new MonetaryAmount("EUR", "1000"),
                null));
    String ownerWithdrawalCanonical =
        canonical(
            new BookkeepingEntry.OwnerWithdrawal(
                LocalDate.parse("2026-04-07"),
                new AccountCode("3010"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null));
    String openingCanonical =
        canonical(
            new BookkeepingEntry.OpeningPosition(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        new MonetaryAmount("EUR", "1000"),
                        null),
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("3000"),
                        JournalLine.EntrySide.CREDIT,
                        new MonetaryAmount("EUR", "1000"),
                        null))));
    String reversalCanonical =
        canonical(
            new BookkeepingEntry.Reversal(
                LocalDate.parse("2026-04-07"),
                new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                    new ReversalReference(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")),
                    new ReversalReason("operator reversal")),
                null,
                null));

    assertContains(directJournalCanonical, "callerAuthoredEntry.entryKind=DIRECT_JOURNAL");
    assertFalse(directJournalCanonical.contains("callerAuthoredEntry.cashAccountCode="));
    assertContains(saleOnCreditCanonical, "callerAuthoredEntry.receivableAccountCode=1100");
    assertContains(saleOnCreditCanonical, "callerAuthoredEntry.revenueAccountCode=4000");
    assertContains(saleOnCreditCanonical, "callerAuthoredEntry.inventoryRelief.present=false");
    assertContains(purchaseSettledCanonical, "callerAuthoredEntry.inventoryAccountCode=1400");
    assertContains(purchaseSettledCanonical, "callerAuthoredEntry.cashAccountCode=1000");
    assertContains(purchaseOnCreditCanonical, "callerAuthoredEntry.payableAccountCode=2100");
    assertContains(expenseOnCreditCanonical, "callerAuthoredEntry.payableAccountCode=2100");
    assertContains(expenseSettledCanonical, "callerAuthoredEntry.expenseAccountCode=5000");
    assertContains(expenseSettledCanonical, "callerAuthoredEntry.cashAccountCode=1000");
    assertContains(receiptCanonical, "callerAuthoredEntry.settlementAdjunct.present=true");
    assertContains(receiptCanonical, "callerAuthoredEntry.settlementAdjunct.accountCode=5600");
    assertContains(paymentCanonical, "callerAuthoredEntry.settlementAdjunct.present=false");
    assertContains(ownerContributionCanonical, "callerAuthoredEntry.equityAccountCode=3000");
    assertContains(ownerWithdrawalCanonical, "callerAuthoredEntry.equityAccountCode=3010");
    assertFalse(openingCanonical.contains("callerAuthoredEntry.settlementAdjunct."));
    assertContains(reversalCanonical, "callerAuthoredEntry.entryKind=REVERSAL");
    assertFalse(reversalCanonical.contains("callerAuthoredEntry.amountCurrency="));
  }

  @Test
  void append_coversTradingSaleInventoryRelief() {
    String canonical =
        canonical(
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "9200"),
                new InventoryRelief(
                    new AccountCode("1400"),
                    new AccountCode("5000"),
                    new dev.erst.fingrind.contract.bookkeeping.QuantityText("1")),
                null,
                null,
                null,
                null));

    assertContains(canonical, "callerAuthoredEntry.inventoryRelief.present=true");
    assertContains(canonical, "callerAuthoredEntry.inventoryRelief.inventoryAccountCode=1400");
    assertContains(canonical, "callerAuthoredEntry.inventoryRelief.costOfSalesAccountCode=5000");
    assertContains(canonical, "callerAuthoredEntry.inventoryRelief.quantity=1");
  }

  @Test
  void append_keepsDirectJournalsAndReversalsFreeOfTypedFields() {
    StringBuilder directJournal = new StringBuilder();
    RequestFingerprintTypedEntryWriter.append(
        directJournal,
        new BookkeepingEntry.DirectJournal(
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new JournalLine(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        dev.erst.fingrind.core.Money.parse("EUR", "10.00")),
                    new JournalLine(
                        new AccountCode("4000"),
                        JournalLine.EntrySide.CREDIT,
                        dev.erst.fingrind.core.Money.parse("EUR", "10.00")))),
            null));
    StringBuilder reversal = new StringBuilder();
    RequestFingerprintTypedEntryWriter.append(
        reversal,
        new BookkeepingEntry.Reversal(
            LocalDate.parse("2026-04-07"),
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                new ReversalReference(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")),
                new ReversalReason("operator reversal")),
            null,
            null));

    assertTrue(directJournal.isEmpty());
    assertTrue(reversal.isEmpty());
  }

  @Test
  void append_canonicalizesEveryAccrualCutoffLifecycleRequest() {
    AccrualCutoffId cutoffId = new AccrualCutoffId("accrual-cutoff-2026-04");
    AccrualCutoffRecognitionInterval interval =
        new AccrualCutoffRecognitionInterval(
            LocalDate.parse("2026-04-07"), LocalDate.parse("2026-05-31"));

    String prepayment =
        canonical(
            new AccrualCutoffBookkeepingEntryVariants.Prepayment(
                LocalDate.parse("2026-04-07"),
                cutoffId,
                new AccountCode("1410"),
                new AccountCode("5000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                interval));
    String deferredRevenue =
        canonical(
            new AccrualCutoffBookkeepingEntryVariants.DeferredRevenue(
                LocalDate.parse("2026-04-07"),
                cutoffId,
                new AccountCode("1000"),
                new AccountCode("2200"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "1000"),
                interval));
    String accruedExpense =
        canonical(
            new AccrualCutoffBookkeepingEntryVariants.AccruedExpense(
                LocalDate.parse("2026-04-07"),
                cutoffId,
                new AccountCode("5000"),
                new AccountCode("2100"),
                new MonetaryAmount("EUR", "1000")));
    String recognition =
        canonical(
            new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
                LocalDate.parse("2026-04-15"), cutoffId, new MonetaryAmount("EUR", "250"), null));
    String settlement =
        canonical(
            new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
                LocalDate.parse("2026-04-15"),
                cutoffId,
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "250"),
                null));

    assertContains(prepayment, "callerAuthoredEntry.accrualCutoffId=accrual-cutoff-2026-04");
    assertContains(prepayment, "callerAuthoredEntry.prepaymentAssetAccountCode=1410");
    assertContains(prepayment, "callerAuthoredEntry.recognitionInterval.endDate=2026-05-31");
    assertContains(deferredRevenue, "callerAuthoredEntry.deferredRevenueAccountCode=2200");
    assertContains(deferredRevenue, "callerAuthoredEntry.revenueAccountCode=4000");
    assertContains(accruedExpense, "callerAuthoredEntry.accruedExpenseLiabilityAccountCode=2100");
    assertContains(recognition, "callerAuthoredEntry.amountMinorUnits=250");
    assertFalse(recognition.contains("callerAuthoredEntry.cashAccountCode="));
    assertContains(settlement, "callerAuthoredEntry.cashAccountCode=1000");
  }

  @Test
  void append_canonicalizesEveryLatvianMonthlyPayrollRequest() {
    LatvianPayrollRunId payrollRunId = new LatvianPayrollRunId("payroll-2026-07-employee-1");
    String monthlyPayroll =
        canonical(
            new LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll(
                LocalDate.parse("2026-07-31"),
                payrollRunId,
                new LatvianPayrollEmployeeReference("employee-1"),
                new LatvianPayrollMonth(YearMonth.of(2026, 7)),
                dev.erst.fingrind.contract.payroll.LatvianPayrollWithholdingProfile
                    .taxBookWithNoDependantsFor2026(),
                new AccountCode("5000"),
                new AccountCode("5010"),
                new AccountCode("2200"),
                new AccountCode("2210"),
                new AccountCode("2220"),
                new AccountCode("2230"),
                new MonetaryAmount("EUR", "200000"),
                null));
    String netWages =
        canonical(
            new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
                LocalDate.parse("2026-07-31"), payrollRunId, new AccountCode("1000"), null));
    String stateRemittance =
        canonical(
            new LatvianPayrollBookkeepingEntryVariants.StateRemittance(
                LocalDate.parse("2026-07-31"), payrollRunId, new AccountCode("1000"), null));

    assertContains(monthlyPayroll, "callerAuthoredEntry.payrollRunId=payroll-2026-07-employee-1");
    assertContains(monthlyPayroll, "callerAuthoredEntry.employeeReference=employee-1");
    assertContains(monthlyPayroll, "callerAuthoredEntry.payrollMonth=2026-07");
    assertContains(monthlyPayroll, "callerAuthoredEntry.amountCurrency=EUR");
    assertContains(monthlyPayroll, "callerAuthoredEntry.amountMinorUnits=200000");
    assertContains(netWages, "callerAuthoredEntry.settlementKind=NET_WAGES");
    assertContains(netWages, "callerAuthoredEntry.cashAccountCode=1000");
    assertContains(stateRemittance, "callerAuthoredEntry.settlementKind=STATE_REMITTANCE");
    assertContains(stateRemittance, "callerAuthoredEntry.payrollRunId=payroll-2026-07-employee-1");
  }

  private static String canonical(BookkeepingEntry entry) {
    StringBuilder canonical = new StringBuilder();
    RequestFingerprintCallerAuthoredEntryWriter.append(canonical, entry);
    return canonical.toString();
  }

  private static void assertContains(String canonical, String expectedLine) {
    assertTrue(canonical.contains(expectedLine + "\n"), expectedLine);
  }
}
