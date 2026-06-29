package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.AccountClassificationReachability;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Set;

/** Current request-surface contract owner for posting semantics and temporal lexicon facts. */
final class RequestSurfaceContracts {
  private RequestSurfaceContracts() {}

  static RequestSurfaceFacts current() {
    return new RequestSurfaceFacts(
        bookkeepingEntryKinds(),
        reachabilityMatrix(),
        bookkeepingEntryEvidence(),
        temporalScopes(),
        commandTemporalScopes());
  }

  private static List<RequestSurfaceFacts.BookkeepingEntryKindFacts> bookkeepingEntryKinds() {
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
                "cash-receipt"),
            "Direct journal writes accept caller-authored balanced lines when no typed business-event surface fits exactly. They are rejected when debit-credit netting reduces every referenced account to zero, they must move at least one declared cash-and-cash-equivalent asset account, and the reachability matrix publishes the exact writable account-classification cells after kernel reservations are applied."),
        entryKindFacts(
            BookkeepingEntryKind.SALE,
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
                "Accepted source-document types for sale requests.",
                "cash-receipt"),
            "Sale writes debit one cash-and-cash-equivalent asset account and credit one revenue account."),
        entryKindFacts(
            BookkeepingEntryKind.EXPENSE,
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
                "Accepted source-document types for expense requests.",
                "expense-receipt"),
            "Expense writes debit one expense account and credit one cash-and-cash-equivalent asset account."),
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
            ProtocolPostingRequestFieldSets.openingPositionFields(),
            Set.of(),
            sourceDocumentTypes(
                SourceDocumentTypePolicyMode.PATTERN_ONLY,
                List.of(),
                "Source-document types remain caller-authored tokens constrained by the published token pattern for opening-position requests.",
                "opening-balance-support"),
            "Opening-position writes are reserved for the one-time adoption window before the first committed posting and may touch only asset, liability, or equity accounts."),
        entryKindFacts(
            BookkeepingEntryKind.REVERSAL,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.LINES,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE,
                ProtocolPostEntryFields.TopLevel.REVERSAL),
            Set.of(ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE),
            sourceDocumentTypes(
                SourceDocumentTypePolicyMode.PATTERN_ONLY,
                List.of(),
                "Source-document types remain caller-authored tokens constrained by the published token pattern for reversal requests.",
                "reversal-support"),
            "Reversal writes are contingent cleanup paths that must negate one existing posting exactly and therefore are not substitutes for forward-originating operational entries."));
  }

  private static RequestSurfaceFacts.EvidenceRequirementFacts bookkeepingEntryEvidence() {
    return new RequestSurfaceFacts.EvidenceRequirementFacts(
        "Every posting request must retain at least one source document. Inspect the selected entry kind for the required source-document fields and source-document-type policy.",
        1);
  }

  private static List<RequestSurfaceFacts.TemporalScopeFacts> temporalScopes() {
    return List.of(
        new RequestSurfaceFacts.TemporalScopeFacts(
            TemporalScopeArchetype.RANGED_FILTER,
            List.of(ProtocolOptions.EFFECTIVE_DATE_FROM, ProtocolOptions.EFFECTIVE_DATE_TO),
            "Effective date range",
            "Effective date from",
            "Effective date to",
            "Optional lower and upper effective-date filters over committed postings. Omit the lower boundary to start at book start; omit the upper boundary to end at the current book horizon.",
            "selected-date",
            "book-start",
            "current-book-horizon",
            "latest-posting-effective-date",
            "no-postings"),
        new RequestSurfaceFacts.TemporalScopeFacts(
            TemporalScopeArchetype.BOUNDED_PERIOD,
            List.of(ProtocolOptions.PERIOD_START, ProtocolOptions.PERIOD_END),
            "Reporting period",
            "Period start",
            "Period end",
            "One explicit closed reporting window. Both boundaries must be supplied, and neither boundary falls back to book start or the current book horizon.",
            "selected-date",
            "selected-date",
            "selected-date",
            "selected-date",
            "selected-date"),
        new RequestSurfaceFacts.TemporalScopeFacts(
            TemporalScopeArchetype.AS_OF_DATE,
            List.of(ProtocolOptions.EFFECTIVE_DATE_AS_OF),
            "As of",
            "As of",
            "As of",
            "One point-in-time effective-date cutoff. Supply --effective-date-as-of to pin that cutoff explicitly, or omit it to resolve the current book horizon for the selected report.",
            "selected-date",
            "book-start",
            "current-book-horizon",
            "latest-posting-effective-date",
            "no-postings"));
  }

  private static List<RequestSurfaceFacts.CommandTemporalScopeFacts> commandTemporalScopes() {
    return List.of(
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.LIST_POSTINGS, TemporalScopeArchetype.RANGED_FILTER),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.ACCOUNT_LEDGER, TemporalScopeArchetype.RANGED_FILTER),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.ACCOUNT_BALANCE, TemporalScopeArchetype.RANGED_FILTER),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.INTERIM_RESULT_SWEEP, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.FISCAL_YEAR_CLOSE, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.PERIOD_SUMMARY, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.INCOME_STATEMENT, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.CASH_FLOW_STATEMENT, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.CHANGES_IN_EQUITY, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.TAX_OBLIGATION, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.TRIAL_BALANCE, TemporalScopeArchetype.AS_OF_DATE),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.FINANCIAL_POSITION, TemporalScopeArchetype.AS_OF_DATE));
  }

  private static RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts(
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

  private static RequestSurfaceFacts.SourceDocumentTypeFacts sourceDocumentTypes(
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
