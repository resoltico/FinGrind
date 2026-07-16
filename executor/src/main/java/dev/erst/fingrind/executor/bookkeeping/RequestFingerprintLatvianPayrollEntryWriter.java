package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;

/** Writes canonical caller-authored fingerprint fields for Latvian monthly-payroll entries. */
final class RequestFingerprintLatvianPayrollEntryWriter {
  private RequestFingerprintLatvianPayrollEntryWriter() {}

  static void append(StringBuilder canonical, LatvianPayrollBookkeepingEntryVariants entry) {
    switch (entry) {
      case LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll -> {
        RequestFingerprintEntryFieldWriter.appendField(
            canonical, "callerAuthoredEntry.payrollRunId", payroll.payrollRunId().value());
        RequestFingerprintEntryFieldWriter.appendField(
            canonical,
            "callerAuthoredEntry.employeeReference",
            payroll.employeeReference().value());
        RequestFingerprintEntryFieldWriter.appendField(
            canonical, "callerAuthoredEntry.payrollMonth", payroll.payrollMonth().wireValue());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "wageExpenseAccountCode", payroll.wageExpenseAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical,
            "employerSocialContributionExpenseAccountCode",
            payroll.employerSocialContributionExpenseAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "netWagesPayableAccountCode", payroll.netWagesPayableAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical,
            "employeeSocialContributionPayableAccountCode",
            payroll.employeeSocialContributionPayableAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical,
            "employerSocialContributionPayableAccountCode",
            payroll.employerSocialContributionPayableAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical,
            "personalIncomeTaxPayableAccountCode",
            payroll.personalIncomeTaxPayableAccountCode());
        RequestFingerprintEntryFieldWriter.appendAmount(canonical, payroll.grossWages());
      }
      case LatvianPayrollBookkeepingEntryVariants.NetWageSettlement settlement ->
          appendSettlement(
              canonical,
              settlement.payrollRunId().value(),
              settlement.cashAccountCode(),
              "NET_WAGES");
      case LatvianPayrollBookkeepingEntryVariants.StateRemittance settlement ->
          appendSettlement(
              canonical,
              settlement.payrollRunId().value(),
              settlement.cashAccountCode(),
              "STATE_REMITTANCE");
    }
  }

  private static void appendSettlement(
      StringBuilder canonical,
      String payrollRunId,
      dev.erst.fingrind.core.AccountCode cashAccountCode,
      String settlementKind) {
    RequestFingerprintEntryFieldWriter.appendField(
        canonical, "callerAuthoredEntry.payrollRunId", payrollRunId);
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "cashAccountCode", cashAccountCode);
    RequestFingerprintEntryFieldWriter.appendField(
        canonical, "callerAuthoredEntry.settlementKind", settlementKind);
  }
}
