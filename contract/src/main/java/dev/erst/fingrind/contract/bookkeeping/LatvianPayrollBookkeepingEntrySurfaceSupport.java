package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayrollCalculation;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingOriginKind;
import java.util.ArrayList;
import java.util.List;

/** Maps executor-resolved Latvian payroll facts to their authoritative journal. */
final class LatvianPayrollBookkeepingEntrySurfaceSupport {
  private LatvianPayrollBookkeepingEntrySurfaceSupport() {}

  static BookkeepingEntryKind entryKind(LatvianPayrollBookkeepingEntryVariants entry) {
    return switch (entry) {
      case LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll _ ->
          BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL;
      case LatvianPayrollBookkeepingEntryVariants.NetWageSettlement _ ->
          BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT;
      case LatvianPayrollBookkeepingEntryVariants.StateRemittance _ ->
          BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE;
    };
  }

  static PostingOriginKind postingOriginKind(LatvianPayrollBookkeepingEntryVariants entry) {
    return switch (entry) {
      case LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll _ ->
          PostingOriginKind.LATVIAN_MONTHLY_PAYROLL;
      case LatvianPayrollBookkeepingEntryVariants.NetWageSettlement _ ->
          PostingOriginKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT;
      case LatvianPayrollBookkeepingEntryVariants.StateRemittance _ ->
          PostingOriginKind.LATVIAN_PAYROLL_STATE_REMITTANCE;
    };
  }

  static JournalEntry journalEntry(LatvianPayrollBookkeepingEntryVariants entry) {
    return switch (entry) {
      case LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll ->
          monthlyPayrollJournalEntry(payroll);
      case LatvianPayrollBookkeepingEntryVariants.NetWageSettlement settlement ->
          settlementJournalEntry(
              settlement.effectiveDate(),
              settlement.resolvedSettlement(),
              LatvianPayrollSettlementKind.NET_WAGES,
              "Latvian payroll net-wage settlement");
      case LatvianPayrollBookkeepingEntryVariants.StateRemittance settlement ->
          settlementJournalEntry(
              settlement.effectiveDate(),
              settlement.resolvedSettlement(),
              LatvianPayrollSettlementKind.STATE_REMITTANCE,
              "Latvian payroll state remittance");
    };
  }

  private static JournalEntry monthlyPayrollJournalEntry(
      LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll) {
    LatvianMonthlyPayrollCalculation calculation = payroll.resolvedCalculation();
    if (calculation == null) {
      throw new IllegalStateException(
          "Latvian monthly payroll requires executor-resolved calculation before deriving a journal.");
    }
    List<JournalLine> lines = new ArrayList<>();
    addWhenPositive(
        lines,
        payroll.wageExpenseAccountCode(),
        JournalLine.EntrySide.DEBIT,
        calculation.grossWages());
    addWhenPositive(
        lines,
        payroll.employerSocialContributionExpenseAccountCode(),
        JournalLine.EntrySide.DEBIT,
        calculation.employerSocialContribution());
    addWhenPositive(
        lines,
        payroll.netWagesPayableAccountCode(),
        JournalLine.EntrySide.CREDIT,
        calculation.netWages());
    addWhenPositive(
        lines,
        payroll.employeeSocialContributionPayableAccountCode(),
        JournalLine.EntrySide.CREDIT,
        calculation.employeeSocialContribution());
    addWhenPositive(
        lines,
        payroll.employerSocialContributionPayableAccountCode(),
        JournalLine.EntrySide.CREDIT,
        calculation.employerSocialContribution());
    addWhenPositive(
        lines,
        payroll.personalIncomeTaxPayableAccountCode(),
        JournalLine.EntrySide.CREDIT,
        calculation.personalIncomeTax());
    return new JournalEntry(payroll.effectiveDate(), List.copyOf(lines));
  }

  private static void addWhenPositive(
      List<JournalLine> lines,
      dev.erst.fingrind.core.AccountCode accountCode,
      JournalLine.EntrySide side,
      dev.erst.fingrind.core.Money amount) {
    if (amount.isPositive()) {
      lines.add(new JournalLine(accountCode, side, amount));
    }
  }

  private static JournalEntry settlementJournalEntry(
      java.time.LocalDate effectiveDate,
      @org.jspecify.annotations.Nullable ResolvedLatvianPayrollSettlement resolvedSettlement,
      LatvianPayrollSettlementKind expectedKind,
      String entryName) {
    ResolvedLatvianPayrollSettlement requiredSettlement =
        requireResolvedSettlement(resolvedSettlement, entryName);
    List<JournalLine> lines = new ArrayList<>();
    if (expectedKind == LatvianPayrollSettlementKind.NET_WAGES) {
      addWhenPositive(
          lines,
          requiredSettlement.netWagesPayableAccountCode(),
          JournalLine.EntrySide.DEBIT,
          requiredSettlement.netWages());
      addWhenPositive(
          lines,
          requiredSettlement.cashAccountCode(),
          JournalLine.EntrySide.CREDIT,
          requiredSettlement.netWages());
    } else {
      addWhenPositive(
          lines,
          requiredSettlement.employeeSocialContributionPayableAccountCode(),
          JournalLine.EntrySide.DEBIT,
          requiredSettlement.employeeSocialContribution());
      addWhenPositive(
          lines,
          requiredSettlement.employerSocialContributionPayableAccountCode(),
          JournalLine.EntrySide.DEBIT,
          requiredSettlement.employerSocialContribution());
      addWhenPositive(
          lines,
          requiredSettlement.personalIncomeTaxPayableAccountCode(),
          JournalLine.EntrySide.DEBIT,
          requiredSettlement.personalIncomeTax());
      addWhenPositive(
          lines,
          requiredSettlement.cashAccountCode(),
          JournalLine.EntrySide.CREDIT,
          requiredSettlement.stateRemittance());
    }
    return new JournalEntry(effectiveDate, List.copyOf(lines));
  }

  private static ResolvedLatvianPayrollSettlement requireResolvedSettlement(
      @org.jspecify.annotations.Nullable ResolvedLatvianPayrollSettlement resolvedSettlement,
      String entryName) {
    if (resolvedSettlement == null) {
      throw new IllegalStateException(entryName + " requires executor-resolved payroll facts.");
    }
    return resolvedSettlement;
  }
}
