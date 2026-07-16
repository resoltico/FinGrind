package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.AccountClassificationReachability;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Current request-surface contract owner for posting semantics and temporal lexicon facts. */
final class RequestSurfaceContracts {
  private RequestSurfaceContracts() {}

  static RequestSurfaceFacts current() {
    return new RequestSurfaceFacts(
        bookkeepingEntryKinds(),
        reachabilityMatrix(),
        bookkeepingEntryEvidence(),
        RequestSurfaceTemporalContracts.temporalScopes(),
        RequestSurfaceTemporalContracts.commandTemporalScopes());
  }

  private static List<RequestSurfaceFacts.BookkeepingEntryKindFacts> bookkeepingEntryKinds() {
    return Stream.of(
            directAndSalesEntryKindFacts(),
            InventoryRequestSurfaceContracts.purchaseEntryKindFacts(),
            AccrualCutoffRequestSurfaceContracts.entryKindFacts(),
            FixedAssetRequestSurfaceContracts.entryKindFacts(),
            FinancingRequestSurfaceContracts.entryKindFacts(),
            RealizedForeignExchangeRequestSurfaceContracts.entryKindFacts(),
            LatvianPayrollRequestSurfaceContracts.entryKindFacts(),
            expenseAndSettlementEntryKindFacts(),
            InventoryRequestSurfaceContracts.maintenanceEntryKindFacts(),
            ownerAndTerminalEntryKindFacts())
        .flatMap(List::stream)
        .toList();
  }

  private static List<RequestSurfaceFacts.BookkeepingEntryKindFacts>
      directAndSalesEntryKindFacts() {
    return List.of(
        entryKindFacts(
            BookkeepingEntryKind.DIRECT_JOURNAL,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.LINES,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            Set.of(ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE),
            sourceDocumentTypes(
                SourceDocumentTypePolicyMode.PATTERN_ONLY,
                List.of(),
                "Source-document types remain caller-authored tokens constrained by the published token pattern for direct journals.",
                "journal-support"),
            "Direct journal writes accept caller-authored balanced lines when no typed business-event surface fits exactly. Raw admission rejects journals that shadow one typed event exactly, rejects bundled operational compounds, rejects every line resolving to an inventory account because raw journals do not own exact inventory quantity truth, preserves genuine non-inventory adjustments, and requires one cash line only on cash-basis books."),
        entryKindFacts(
            BookkeepingEntryKind.SALE_SETTLED,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.AMOUNT,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            Set.of(
                ProtocolPostEntryFields.TopLevel.TAX,
                ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE),
            sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("cash-receipt", "bank-deposit", "card-settlement"),
                "Accepted source-document types for settled-sale requests.",
                "cash-receipt"),
            "Settled-sale writes debit one cash-and-cash-equivalent asset account and credit one revenue account. Trading-template sale requests additionally carry inventoryRelief so the same event relieves one exact inventory quantity, debits one cost-of-sales account, credits one non-cash inventory account, and lets FinGrind derive cost of sales from the inventory pool."),
        entryKindFacts(
            BookkeepingEntryKind.SALE_ON_CREDIT,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.AMOUNT,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            Set.of(
                ProtocolPostEntryFields.TopLevel.TAX,
                ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE),
            sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("invoice"),
                "Accepted source-document types for sale-on-credit requests.",
                "invoice"),
            "Sale-on-credit writes debit one trade receivable account and credit one revenue account. Trading-template sale requests additionally carry inventoryRelief so the same event relieves one exact inventory quantity, debits one cost-of-sales account, credits one non-cash inventory account, and lets FinGrind derive cost of sales from the inventory pool."));
  }

  private static List<RequestSurfaceFacts.BookkeepingEntryKindFacts>
      expenseAndSettlementEntryKindFacts() {
    return List.of(
        entryKindFacts(
            BookkeepingEntryKind.EXPENSE_SETTLED,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.AMOUNT,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            Set.of(
                ProtocolPostEntryFields.TopLevel.TAX,
                ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE),
            sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("expense-receipt", "cash-disbursement", "bank-payment-confirmation"),
                "Accepted source-document types for settled-expense requests.",
                "expense-receipt"),
            "Settled-expense writes debit one expense account and credit one cash-and-cash-equivalent asset account."),
        entryKindFacts(
            BookkeepingEntryKind.EXPENSE_ON_CREDIT,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.AMOUNT,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            Set.of(
                ProtocolPostEntryFields.TopLevel.TAX,
                ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE),
            sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("bill"),
                "Accepted source-document types for expense-on-credit requests.",
                "bill"),
            "Expense-on-credit writes debit one expense account and credit one trade payable account."),
        entryKindFacts(
            BookkeepingEntryKind.RECEIPT,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.AMOUNT,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            Set.of(ProtocolPostEntryFields.TopLevel.SETTLEMENT_ADJUNCT),
            sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("cash-receipt", "bank-deposit", "card-settlement"),
                "Accepted source-document types for receipt requests.",
                "cash-receipt"),
            "Receipt writes credit one trade receivable account and debit one cash-and-cash-equivalent asset account, with one optional settlement adjunct."),
        entryKindFacts(
            BookkeepingEntryKind.PAYMENT,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.AMOUNT,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            Set.of(ProtocolPostEntryFields.TopLevel.SETTLEMENT_ADJUNCT),
            sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("cash-disbursement", "bank-payment-confirmation"),
                "Accepted source-document types for payment requests.",
                "cash-disbursement"),
            "Payment writes debit one trade payable account and credit one cash-and-cash-equivalent asset account, with one optional settlement adjunct."));
  }

  private static List<RequestSurfaceFacts.BookkeepingEntryKindFacts>
      ownerAndTerminalEntryKindFacts() {
    return List.of(
        entryKindFacts(
            BookkeepingEntryKind.OWNER_CONTRIBUTION,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.AMOUNT,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            Set.of(ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE),
            sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("owner-contribution", "capital-deposit", "bank-deposit"),
                "Accepted source-document types for owner-contribution requests.",
                "owner-contribution"),
            "Owner-contribution writes debit one cash-and-cash-equivalent asset account and credit one equity contribution account."),
        entryKindFacts(
            BookkeepingEntryKind.OWNER_WITHDRAWAL,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                ProtocolPostEntryFields.TopLevel.AMOUNT,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            Set.of(ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE),
            sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("owner-withdrawal", "distribution-payment", "bank-payment-confirmation"),
                "Accepted source-document types for owner-withdrawal requests.",
                "owner-withdrawal"),
            "Owner-withdrawal writes debit one equity withdrawal account and credit one cash-and-cash-equivalent asset account."),
        entryKindFacts(
            BookkeepingEntryKind.OPENING_POSITION,
            ProtocolPostingRequestFieldSets.fieldsFor(BookkeepingEntryKind.OPENING_POSITION),
            Set.of(),
            sourceDocumentTypes(
                SourceDocumentTypePolicyMode.PATTERN_ONLY,
                List.of(),
                "Source-document types remain caller-authored tokens constrained by the published token pattern for opening-position requests.",
                "opening-balance-support"),
            "Opening-position writes are reserved for the one-time adoption window before the first committed posting. Inventory opening balances must include exact quantity alongside carrying cost so FinGrind can initialize each inventory pool; other balances must omit quantity."),
        entryKindFacts(
            BookkeepingEntryKind.REVERSAL,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE,
                ProtocolPostEntryFields.TopLevel.REVERSAL),
            Set.of(ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE),
            sourceDocumentTypes(
                SourceDocumentTypePolicyMode.PATTERN_ONLY,
                List.of(),
                "Source-document types remain caller-authored tokens constrained by the published token pattern for reversal requests.",
                "reversal-support"),
            "Reversal writes are contingent cleanup paths that derive their journal lines from one existing posting and negate that target exactly, reject reversal targets that are themselves reversals, and are not substitutes for forward-originating operational entries."));
  }

  private static RequestSurfaceFacts.EvidenceRequirementFacts bookkeepingEntryEvidence() {
    return new RequestSurfaceFacts.EvidenceRequirementFacts(
        "Every posting request must retain at least one source document. Inspect the selected entry kind for the required source-document fields and source-document-type policy.",
        1);
  }

  static RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts(
      BookkeepingEntryKind entryKind,
      Set<String> requiredTopLevelFields,
      Set<String> optionalTopLevelFields,
      RequestSurfaceFacts.SourceDocumentTypeFacts sourceDocumentTypes,
      String semantics) {
    Set<String> acceptedTopLevelFields = new java.util.LinkedHashSet<>(requiredTopLevelFields);
    acceptedTopLevelFields.addAll(optionalTopLevelFields);
    return new RequestSurfaceFacts.BookkeepingEntryKindFacts(
        entryKind,
        ProtocolPostEntryFields.topLevelFields().stream()
            .filter(requiredTopLevelFields::contains)
            .toList(),
        ProtocolPostEntryFields.topLevelFields().stream()
            .filter(optionalTopLevelFields::contains)
            .toList(),
        ProtocolPostEntryFields.topLevelFields().stream()
            .filter(fieldName -> !acceptedTopLevelFields.contains(fieldName))
            .toList(),
        ProtocolPostEntryFields.sourceDocumentFields(),
        sourceDocumentTypes,
        semantics);
  }

  static RequestSurfaceFacts.SourceDocumentTypeFacts sourceDocumentTypes(
      SourceDocumentTypePolicyMode mode,
      List<String> acceptedValues,
      String semantics,
      String scaffoldValue) {
    return new RequestSurfaceFacts.SourceDocumentTypeFacts(
        mode, acceptedValues, semantics, scaffoldValue);
  }

  private static List<RequestSurfaceFacts.ReachabilityCellFacts> reachabilityMatrix() {
    return AccountClassificationReachability.currentKernel().stream()
        .map(
            cell ->
                new RequestSurfaceFacts.ReachabilityCellFacts(
                    cell.classificationFamily(),
                    cell.accountType(),
                    cell.classification(),
                    cell.declarable(),
                    cell.openingReachable(),
                    cell.operationalJournalReachable(),
                    cell.reversalReachable()))
        .toList();
  }
}
