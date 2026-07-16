package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Set;

/** Request facts owned by the deliberately narrow Latvian monthly-payroll context. */
final class LatvianPayrollRequestSurfaceContracts {
  private LatvianPayrollRequestSurfaceContracts() {}

  static List<RequestSurfaceFacts.BookkeepingEntryKindFacts> entryKindFacts() {
    return List.of(
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL,
            ProtocolPostingRequestFieldSets.fieldsFor(BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("payroll-register", "employment-contract", "timesheet"),
                "Accepted source-document types for the admitted Latvian monthly-payroll profile.",
                "payroll-register"),
            "Latvian monthly payroll records one 2026 EUR ordinary employee payroll accrual for one opaque employee and payroll month. FinGrind derives employee social contributions, employer social contributions, personal income tax, net wages payable, and the complete balanced journal from gross wages."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
            ProtocolPostingRequestFieldSets.fieldsFor(
                BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("payroll-register", "bank-payment-order"),
                "Accepted source-document types for an exact Latvian payroll net-wage settlement.",
                "bank-payment-order"),
            "Latvian payroll net-wage settlement discharges the exact net-wage liability of one active retained payroll run. The caller supplies no amount or liability account; FinGrind derives both from that run."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE,
            ProtocolPostingRequestFieldSets.fieldsFor(
                BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("social-insurance-report", "bank-payment-order"),
                "Accepted source-document types for an exact Latvian payroll state remittance.",
                "social-insurance-report"),
            "Latvian payroll state remittance discharges the exact employee social-contribution, employer social-contribution, and personal-income-tax liabilities of one active retained payroll run. The caller supplies no amount or liability account; FinGrind derives both from that run."));
  }
}
