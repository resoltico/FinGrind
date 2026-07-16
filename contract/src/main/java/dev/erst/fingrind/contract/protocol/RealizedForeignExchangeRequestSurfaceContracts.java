package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Set;

/** Request facts owned by the Realized Foreign Exchange context. */
final class RealizedForeignExchangeRequestSurfaceContracts {
  private RealizedForeignExchangeRequestSurfaceContracts() {}

  static List<RequestSurfaceFacts.BookkeepingEntryKindFacts> entryKindFacts() {
    return List.of(
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION,
            ProtocolPostingRequestFieldSets.fieldsFor(
                BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("foreign-currency-invoice", "customer-contract", "exchange-rate-quote"),
                "Accepted source-document types for foreign-currency obligation requests.",
                "foreign-currency-invoice"),
            "Foreign-currency obligation records one trade receivable and revenue at a retained functional carrying amount, along with the accounts that receive a later realized gain or loss."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
            ProtocolPostingRequestFieldSets.fieldsFor(
                BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("bank-credit-advice", "settlement-confirmation", "exchange-rate-quote"),
                "Accepted source-document types for realized foreign-exchange settlement requests.",
                "settlement-confirmation"),
            "Realized foreign-exchange settlement closes one admitted foreign-currency receivable for its exact transaction amount, debits cash at the settlement functional amount, and lets FinGrind derive the realized gain or loss."));
  }
}
