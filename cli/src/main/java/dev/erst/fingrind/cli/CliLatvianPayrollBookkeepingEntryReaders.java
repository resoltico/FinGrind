package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredBoolean;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredInt;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollWithholdingProfile;
import dev.erst.fingrind.contract.protocol.ProtocolBusinessEventFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestFieldSets;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import tools.jackson.databind.node.ObjectNode;

/** Reads request payloads owned by the deliberately narrow Latvian monthly-payroll context. */
final class CliLatvianPayrollBookkeepingEntryReaders {
  private CliLatvianPayrollBookkeepingEntryReaders() {}

  static BookkeepingEntry read(ObjectNode rootNode, BookkeepingEntryKind entryKind) {
    return switch (entryKind) {
      case LATVIAN_MONTHLY_PAYROLL -> readMonthlyPayroll(rootNode);
      case LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT -> readNetWageSettlement(rootNode);
      case LATVIAN_PAYROLL_STATE_REMITTANCE -> readStateRemittance(rootNode);
      default -> throw new IllegalArgumentException("Expected a Latvian payroll entry kind.");
    };
  }

  static LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll readMonthlyPayroll(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolPostingRequestFieldSets.fieldsFor(BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL));
    return new LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new LatvianPayrollRunId(
            requiredText(rootNode, ProtocolBusinessEventFields.LatvianPayroll.PAYROLL_RUN_ID)),
        new LatvianPayrollEmployeeReference(
            requiredText(rootNode, ProtocolBusinessEventFields.LatvianPayroll.EMPLOYEE_REFERENCE)),
        LatvianPayrollMonth.parse(
            requiredText(rootNode, ProtocolBusinessEventFields.LatvianPayroll.PAYROLL_MONTH)),
        new LatvianPayrollWithholdingProfile(
            requiredBoolean(
                rootNode, ProtocolBusinessEventFields.LatvianPayroll.TAX_BOOK_HELD_AT_EMPLOYER),
            requiredInt(rootNode, ProtocolBusinessEventFields.LatvianPayroll.DEPENDANT_COUNT)),
        account(rootNode, ProtocolBusinessEventFields.LatvianPayroll.WAGE_EXPENSE_ACCOUNT_CODE),
        account(
            rootNode,
            ProtocolBusinessEventFields.LatvianPayroll
                .EMPLOYER_SOCIAL_CONTRIBUTION_EXPENSE_ACCOUNT_CODE),
        account(
            rootNode, ProtocolBusinessEventFields.LatvianPayroll.NET_WAGES_PAYABLE_ACCOUNT_CODE),
        account(
            rootNode,
            ProtocolBusinessEventFields.LatvianPayroll
                .EMPLOYEE_SOCIAL_CONTRIBUTION_PAYABLE_ACCOUNT_CODE),
        account(
            rootNode,
            ProtocolBusinessEventFields.LatvianPayroll
                .EMPLOYER_SOCIAL_CONTRIBUTION_PAYABLE_ACCOUNT_CODE),
        account(
            rootNode,
            ProtocolBusinessEventFields.LatvianPayroll.PERSONAL_INCOME_TAX_PAYABLE_ACCOUNT_CODE),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(
            rootNode, ProtocolBusinessEventFields.LatvianPayroll.GROSS_WAGES),
        null);
  }

  static LatvianPayrollBookkeepingEntryVariants.NetWageSettlement readNetWageSettlement(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolPostingRequestFieldSets.fieldsFor(
            BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT));
    return new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new LatvianPayrollRunId(
            requiredText(rootNode, ProtocolBusinessEventFields.LatvianPayroll.PAYROLL_RUN_ID)),
        account(rootNode, ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE),
        null);
  }

  static LatvianPayrollBookkeepingEntryVariants.StateRemittance readStateRemittance(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolPostingRequestFieldSets.fieldsFor(
            BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE));
    return new LatvianPayrollBookkeepingEntryVariants.StateRemittance(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new LatvianPayrollRunId(
            requiredText(rootNode, ProtocolBusinessEventFields.LatvianPayroll.PAYROLL_RUN_ID)),
        account(rootNode, ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE),
        null);
  }

  private static AccountCode account(ObjectNode rootNode, String fieldName) {
    return new AccountCode(requiredText(rootNode, fieldName));
  }
}
