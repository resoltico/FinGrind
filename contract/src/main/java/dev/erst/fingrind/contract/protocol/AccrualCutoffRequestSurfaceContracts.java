package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Set;

/** Request facts owned by the accrual cut-off context. */
final class AccrualCutoffRequestSurfaceContracts {
  private AccrualCutoffRequestSurfaceContracts() {}

  static List<RequestSurfaceFacts.BookkeepingEntryKindFacts> entryKindFacts() {
    return List.of(
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.PREPAYMENT,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.ACCRUAL_CUTOFF_ID,
                ProtocolPostEntryFields.TopLevel.PREPAYMENT_ASSET_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.AMOUNT,
                ProtocolPostEntryFields.TopLevel.RECOGNITION_INTERVAL,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("prepayment-invoice", "cash-disbursement", "bank-payment-confirmation"),
                "Accepted source-document types for prepayment requests.",
                "prepayment-invoice"),
            "Prepayment writes debit one prepaid-expense asset, credit one cash-and-cash-equivalent asset, and establishes an inclusive interval for future expense recognition."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.DEFERRED_REVENUE,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.ACCRUAL_CUTOFF_ID,
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.DEFERRED_REVENUE_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.AMOUNT,
                ProtocolPostEntryFields.TopLevel.RECOGNITION_INTERVAL,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("customer-contract", "cash-receipt", "bank-deposit"),
                "Accepted source-document types for deferred-revenue requests.",
                "customer-contract"),
            "Deferred revenue writes debit one cash-and-cash-equivalent asset, credit one deferred-revenue liability, and establishes an inclusive interval for future revenue recognition."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.ACCRUED_EXPENSE,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.ACCRUAL_CUTOFF_ID,
                ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.AMOUNT,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("supplier-invoice", "accrual-schedule", "service-receipt"),
                "Accepted source-document types for accrued-expense requests.",
                "accrual-schedule"),
            "Accrued expense writes debit one expense account and credit one accrued-expense liability."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.ACCRUAL_CUTOFF_ID,
                ProtocolPostEntryFields.TopLevel.AMOUNT,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("prepayment-schedule", "revenue-recognition-schedule"),
                "Accepted source-document types for accrual cut-off recognition requests.",
                "prepayment-schedule"),
            "Accrual cut-off recognition consumes an admitted prepayment or deferred-revenue balance inside its inclusive recognition interval, using executor-resolved account roles."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.ACCRUAL_CUTOFF_ID,
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.AMOUNT,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("cash-disbursement", "bank-payment-confirmation"),
                "Accepted source-document types for accrued-expense settlement requests.",
                "cash-disbursement"),
            "Accrued-expense settlement consumes an admitted accrued-expense liability and credits one cash-and-cash-equivalent asset using executor-resolved account roles."));
  }
}
