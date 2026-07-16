package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Set;

/** Request facts owned by the Financing context. */
final class FinancingRequestSurfaceContracts {
  private FinancingRequestSurfaceContracts() {}

  static List<RequestSurfaceFacts.BookkeepingEntryKindFacts> entryKindFacts() {
    return List.of(
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.FINANCING_BORROWING,
            ProtocolFinancingPostingRequestFieldSets.borrowingFields(),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("loan-agreement", "lender-disbursement-notice", "bank-credit-advice"),
                "Accepted source-document types for financing-borrowing requests.",
                "loan-agreement"),
            "Financing borrowing creates one financing arrangement, debits one cash-and-cash-equivalent asset account, credits one principal liability account, and records the liability account used for future accrued interest."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT,
            ProtocolFinancingPostingRequestFieldSets.principalRepaymentFields(),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("loan-statement", "bank-payment-confirmation"),
                "Accepted source-document types for financing principal-repayment requests.",
                "loan-statement"),
            "Financing principal repayment debits the executor-resolved principal liability, credits cash, and cannot exceed principal outstanding."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL,
            ProtocolFinancingPostingRequestFieldSets.interestAccrualFields(),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("loan-statement", "interest-calculation"),
                "Accepted source-document types for financing interest-accrual requests.",
                "interest-calculation"),
            "Financing interest accrual debits one interest-expense account, credits the executor-resolved interest-payable account, and retains accrued interest for future settlement."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT,
            ProtocolFinancingPostingRequestFieldSets.interestPaymentFields(),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("loan-statement", "bank-payment-confirmation"),
                "Accepted source-document types for financing interest-payment requests.",
                "loan-statement"),
            "Financing interest payment debits the executor-resolved interest-payable account, credits cash, and cannot exceed accrued unpaid interest."));
  }
}
