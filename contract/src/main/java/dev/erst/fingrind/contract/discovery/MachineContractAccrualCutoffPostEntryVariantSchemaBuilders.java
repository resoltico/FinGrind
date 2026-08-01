package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolBusinessEventFields;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Typed request-schema builders owned by the accrual cut-off context. */
final class MachineContractAccrualCutoffPostEntryVariantSchemaBuilders {
  private MachineContractAccrualCutoffPostEntryVariantSchemaBuilders() {}

  static Map<String, Object> prepaymentSchema() {
    return schema(
        BookkeepingEntryKind.PREPAYMENT,
        "Cash-funded prepayment that establishes a prepaid asset and an inclusive expense-recognition interval.",
        requiredAccount(
            ProtocolBusinessEventFields.AccrualCutoff.PREPAYMENT_ASSET_ACCOUNT_CODE,
            "Declared prepaid-expense asset account debited by this prepayment."),
        requiredAccount(
            ProtocolBusinessEventFields.Inventory.EXPENSE_ACCOUNT_CODE,
            "Declared expense account recognized as the prepaid balance is consumed."),
        requiredAccount(
            ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
            "Declared cash account credited by this prepayment."),
        requiredRecognitionInterval());
  }

  static Map<String, Object> deferredRevenueSchema() {
    return schema(
        BookkeepingEntryKind.DEFERRED_REVENUE,
        "Cash-funded deferred revenue that establishes a liability and an inclusive revenue-recognition interval.",
        requiredAccount(
            ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
            "Declared cash account debited by this deferred-revenue receipt."),
        requiredAccount(
            ProtocolBusinessEventFields.AccrualCutoff.DEFERRED_REVENUE_ACCOUNT_CODE,
            "Declared deferred-revenue liability credited by this receipt."),
        requiredAccount(
            ProtocolBusinessEventFields.Core.REVENUE_ACCOUNT_CODE,
            "Declared revenue account recognized as the deferred balance is consumed."),
        requiredRecognitionInterval());
  }

  static Map<String, Object> accruedExpenseSchema() {
    return schema(
        BookkeepingEntryKind.ACCRUED_EXPENSE,
        "Unpaid accrued expense that recognizes expense and establishes an accrued-expense liability.",
        requiredAccount(
            ProtocolBusinessEventFields.Inventory.EXPENSE_ACCOUNT_CODE,
            "Declared expense account debited by this accrued expense."),
        requiredAccount(
            ProtocolBusinessEventFields.AccrualCutoff.ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE,
            "Declared accrued-expense liability credited by this accrued expense."));
  }

  static Map<String, Object> recognitionSchema() {
    return schema(
        BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION,
        "Recognition that consumes one admitted prepayment or deferred-revenue balance inside its inclusive interval. FinGrind resolves the account pair.");
  }

  static Map<String, Object> settlementSchema() {
    return schema(
        BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT,
        "Settlement that consumes one admitted accrued-expense liability. FinGrind resolves the liability account.",
        requiredAccount(
            ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
            "Declared cash account credited by this accrued-expense settlement."));
  }

  private static Map<String, Object> schema(
      BookkeepingEntryKind entryKind,
      String description,
      MachineContractFieldSpec... accountFields) {
    RequestSurfaceFacts.BookkeepingEntryKindFacts facts =
        MachineContractPostEntryTypedVariantSchemaBuilders.entryKindFacts(entryKind);
    List<MachineContractFieldSpec> fields = new ArrayList<>();
    fields.add(
        MachineContractPostEntryComponentSchemas.requiredEntryKindField(
            entryKind, "This request records an accrual cut-off business event."));
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredEffectiveDateField());
    fields.add(
        MachineContractFieldSpec.required(
            ProtocolBusinessEventFields.AccrualCutoff.ACCRUAL_CUTOFF_ID,
            "Stable caller-chosen identifier for this accrual cut-off lifecycle.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Stable caller-chosen identifier for this accrual cut-off lifecycle.",
                dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId.pattern(),
                dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId.maxLength())));
    java.util.Collections.addAll(fields, accountFields);
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredAmountField());
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredEvidenceField(facts));
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredProvenanceField());
    return MachineContractSchemaSupport.objectSchema(description, List.copyOf(fields));
  }

  private static MachineContractFieldSpec requiredAccount(String fieldName, String description) {
    return MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
        fieldName, description);
  }

  private static MachineContractFieldSpec requiredRecognitionInterval() {
    return MachineContractFieldSpec.required(
        ProtocolBusinessEventFields.AccrualCutoff.RECOGNITION_INTERVAL,
        "Inclusive recognition interval for this deferred balance.",
        MachineContractPostEntryComponentSchemas.recognitionIntervalSchema());
  }
}
