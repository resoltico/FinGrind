package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.protocol.ProtocolBusinessEventFields;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;

/** Schema builder owned by the deliberately narrow Latvian monthly-payroll context. */
final class MachineContractLatvianPayrollPostEntryVariantSchemaBuilders {
  private MachineContractLatvianPayrollPostEntryVariantSchemaBuilders() {}

  static Map<String, Object> monthlyPayrollSchema() {
    RequestSurfaceFacts.BookkeepingEntryKindFacts facts =
        MachineContractPostEntryTypedVariantSchemaBuilders.entryKindFacts(
            BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL);
    return MachineContractSchemaSupport.objectSchema(
        "Latvian 2026 ordinary monthly-payroll accrual. FinGrind derives all statutory components from gross wages; callers never supply net pay, personal income tax, or social contributions.",
        monthlyPayrollFields(facts));
  }

  static List<MachineContractFieldSpec> monthlyPayrollVariantFields() {
    return monthlyPayrollFields(
            MachineContractPostEntryTypedVariantSchemaBuilders.entryKindFacts(
                BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL))
        .stream()
        .filter(
            field ->
                !List.of(
                        ProtocolBusinessEventFields.Core.ENTRY_KIND,
                        ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
                        ProtocolBusinessEventFields.Core.EVIDENCE,
                        ProtocolBusinessEventFields.Core.PROVENANCE)
                    .contains(field.name()))
        .toList();
  }

  private static List<MachineContractFieldSpec> monthlyPayrollFields(
      RequestSurfaceFacts.BookkeepingEntryKindFacts facts) {
    return List.of(
        MachineContractPostEntryComponentSchemas.requiredEntryKindField(
            BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL,
            "This request records an executor-resolved Latvian monthly payroll accrual."),
        MachineContractPostEntryRequiredFieldSpecs.requiredEffectiveDateField(),
        MachineContractFieldSpec.required(
            ProtocolBusinessEventFields.LatvianPayroll.PAYROLL_RUN_ID,
            "Stable caller-chosen identifier for this immutable payroll run.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Stable caller-chosen identifier for this immutable payroll run.",
                LatvianPayrollRunId.pattern(),
                LatvianPayrollRunId.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolBusinessEventFields.LatvianPayroll.EMPLOYEE_REFERENCE,
            "Opaque non-personal-data employee reference unique within the protected book.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Opaque non-personal-data employee reference unique within the protected book.",
                LatvianPayrollEmployeeReference.pattern(),
                LatvianPayrollEmployeeReference.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolBusinessEventFields.LatvianPayroll.PAYROLL_MONTH,
            "Payroll month in canonical YYYY-MM form. The effective date must be its final calendar day.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Payroll month in canonical YYYY-MM form.",
                LatvianPayrollMonth.wirePattern(),
                LatvianPayrollMonth.wireLength())),
        MachineContractFieldSpec.required(
            ProtocolBusinessEventFields.LatvianPayroll.TAX_BOOK_HELD_AT_EMPLOYER,
            "True only when the employee has lodged the payroll tax book with this employer; the current profile refuses false.",
            Map.of("type", "boolean")),
        MachineContractFieldSpec.required(
            ProtocolBusinessEventFields.LatvianPayroll.DEPENDANT_COUNT,
            "Number of eligible dependants claimed through this employer's payroll tax book; the current profile admits only 0.",
            MachineContractScalarSchemas.nonNegativeIntegerSchema(
                "Non-negative eligible dependant count.")),
        account(
            ProtocolBusinessEventFields.LatvianPayroll.WAGE_EXPENSE_ACCOUNT_CODE,
            "Declared wage expense account debited by gross wages."),
        account(
            ProtocolBusinessEventFields.LatvianPayroll
                .EMPLOYER_SOCIAL_CONTRIBUTION_EXPENSE_ACCOUNT_CODE,
            "Declared employer social-contribution expense account debited by the employer contribution."),
        account(
            ProtocolBusinessEventFields.LatvianPayroll.NET_WAGES_PAYABLE_ACCOUNT_CODE,
            "Declared current-liability account credited by net wages payable."),
        account(
            ProtocolBusinessEventFields.LatvianPayroll
                .EMPLOYEE_SOCIAL_CONTRIBUTION_PAYABLE_ACCOUNT_CODE,
            "Declared current-liability account credited by employee social contributions."),
        account(
            ProtocolBusinessEventFields.LatvianPayroll
                .EMPLOYER_SOCIAL_CONTRIBUTION_PAYABLE_ACCOUNT_CODE,
            "Declared current-liability account credited by employer social contributions."),
        account(
            ProtocolBusinessEventFields.LatvianPayroll.PERSONAL_INCOME_TAX_PAYABLE_ACCOUNT_CODE,
            "Declared current-liability account credited by personal income tax."),
        MachineContractFieldSpec.required(
            ProtocolBusinessEventFields.LatvianPayroll.GROSS_WAGES,
            "Exact positive EUR gross wages. FinGrind derives every payroll component from this amount.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Exact positive EUR gross wages.", true)),
        MachineContractPostEntryRequiredFieldSpecs.requiredEvidenceField(facts),
        MachineContractPostEntryRequiredFieldSpecs.requiredProvenanceField());
  }

  static Map<String, Object> netWageSettlementSchema() {
    return settlementSchema(
        BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
        "This request settles the exact executor-derived net-wage obligation of the active retained Latvian payroll run.");
  }

  static Map<String, Object> stateRemittanceSchema() {
    return settlementSchema(
        BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE,
        "This request remits the exact executor-derived state obligation of the active retained Latvian payroll run.");
  }

  private static Map<String, Object> settlementSchema(
      BookkeepingEntryKind entryKind, String description) {
    RequestSurfaceFacts.BookkeepingEntryKindFacts facts =
        MachineContractPostEntryTypedVariantSchemaBuilders.entryKindFacts(entryKind);
    return MachineContractSchemaSupport.objectSchema(
        description,
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(entryKind, description),
            MachineContractPostEntryRequiredFieldSpecs.requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolBusinessEventFields.LatvianPayroll.PAYROLL_RUN_ID,
                "Stable identifier of the retained payroll run whose exact obligation is discharged.",
                MachineContractScalarSchemas.tokenStringSchema(
                    "Stable identifier of the retained payroll run whose exact obligation is discharged.",
                    LatvianPayrollRunId.pattern(),
                    LatvianPayrollRunId.maxLength())),
            account(
                ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
                "Declared cash-and-cash-equivalent asset account credited by the exact payroll obligation."),
            MachineContractPostEntryRequiredFieldSpecs.requiredEvidenceField(facts),
            MachineContractPostEntryRequiredFieldSpecs.requiredProvenanceField()));
  }

  private static MachineContractFieldSpec account(String fieldName, String description) {
    return MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
        fieldName, description);
  }
}
