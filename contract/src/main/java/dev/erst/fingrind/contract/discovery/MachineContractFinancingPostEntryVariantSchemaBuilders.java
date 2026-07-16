package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;

/** Machine request schemas owned by the Financing context. */
final class MachineContractFinancingPostEntryVariantSchemaBuilders {
  private MachineContractFinancingPostEntryVariantSchemaBuilders() {}

  static Map<String, Object> borrowingSchema() {
    return schema(
        BookkeepingEntryKind.FINANCING_BORROWING,
        "Creates one financing arrangement from a lender disbursement.",
        List.of(
            arrangementId(),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                "Declared cash account debited by lender proceeds."),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolPostEntryFields.TopLevel.PRINCIPAL_LIABILITY_ACCOUNT_CODE,
                "Declared liability account credited for principal outstanding."),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolPostEntryFields.TopLevel.INTEREST_PAYABLE_ACCOUNT_CODE,
                "Declared current-liability account credited by future interest accrual."),
            MachineContractPostEntryContextSchemaSupport.requiredPositiveMoney(
                ProtocolPostEntryFields.TopLevel.PRINCIPAL_AMOUNT,
                "Exact positive functional-currency principal received.")));
  }

  static Map<String, Object> principalRepaymentSchema() {
    return schema(
        BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT,
        "Repays principal against one admitted financing arrangement.",
        List.of(
            arrangementId(),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                "Declared cash account credited by the repayment."),
            MachineContractPostEntryContextSchemaSupport.requiredPositiveMoney(
                ProtocolPostEntryFields.TopLevel.PRINCIPAL_AMOUNT,
                "Exact positive principal amount repaid.")));
  }

  static Map<String, Object> interestAccrualSchema() {
    return schema(
        BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL,
        "Accrues interest against one admitted financing arrangement.",
        List.of(
            arrangementId(),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolPostEntryFields.TopLevel.INTEREST_EXPENSE_ACCOUNT_CODE,
                "Declared expense account debited by accrued interest."),
            MachineContractPostEntryContextSchemaSupport.requiredPositiveMoney(
                ProtocolPostEntryFields.TopLevel.INTEREST_AMOUNT,
                "Exact positive interest amount accrued.")));
  }

  static Map<String, Object> interestPaymentSchema() {
    return schema(
        BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT,
        "Pays accrued interest against one admitted financing arrangement.",
        List.of(
            arrangementId(),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                "Declared cash account credited by the interest payment."),
            MachineContractPostEntryContextSchemaSupport.requiredPositiveMoney(
                ProtocolPostEntryFields.TopLevel.INTEREST_AMOUNT,
                "Exact positive accrued interest amount paid.")));
  }

  private static Map<String, Object> schema(
      BookkeepingEntryKind kind, String description, List<MachineContractFieldSpec> contextFields) {
    return MachineContractPostEntryContextSchemaSupport.typedEventSchema(
        kind, description, "This request records a typed financing event.", contextFields);
  }

  private static MachineContractFieldSpec arrangementId() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.FINANCING_ARRANGEMENT_ID,
        "Stable lowercase-kebab identifier for this financing arrangement.",
        MachineContractScalarSchemas.tokenStringSchema(
            "Stable lowercase-kebab identifier for this financing arrangement.",
            "[a-z0-9]+(?:-[a-z0-9]+)*",
            120));
  }
}
