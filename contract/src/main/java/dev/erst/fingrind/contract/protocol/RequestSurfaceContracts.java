package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.bookkeeping.JournalRecipeKind;
import dev.erst.fingrind.core.AccountClassificationReachability;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Set;

/** Current request-surface contract owner for posting semantics and temporal lexicon facts. */
final class RequestSurfaceContracts {
  private static final String GENERAL_JOURNAL_EVIDENCE_PROFILE = "general-journal-support";
  private static final String CASH_REVENUE_EVIDENCE_PROFILE = "cash-revenue-support";
  private static final String CASH_EXPENSE_EVIDENCE_PROFILE = "cash-expense-support";
  private static final String EQUITY_CONTRIBUTION_EVIDENCE_PROFILE = "equity-contribution-support";
  private static final String EQUITY_WITHDRAWAL_EVIDENCE_PROFILE = "equity-withdrawal-support";
  private static final String OPENING_POSITION_EVIDENCE_PROFILE = "opening-position-support";
  private static final String REVERSAL_EVIDENCE_PROFILE = "reversal-support";

  private RequestSurfaceContracts() {}

  static RequestSurfaceFacts current() {
    return new RequestSurfaceFacts(
        postEntryKinds(),
        journalRecipes(),
        evidenceProfiles(),
        reachabilityMatrix(),
        postEntryEvidence(),
        temporalScopes(),
        commandTemporalScopes());
  }

  private static List<RequestSurfaceFacts.PostEntryKindFacts> postEntryKinds() {
    return List.of(
        entryKindFacts(
            BookkeepingEntryKind.JOURNAL,
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE),
            GENERAL_JOURNAL_EVIDENCE_PROFILE,
            "Operational journal writes accept either direct balanced lines or one named journal recipe; direct caller-authored journals are rejected when debit-credit netting reduces every referenced account to zero; the reachability matrix publishes the exact account-classification cells that remain writable through this path after kernel reservations are applied."),
        entryKindFacts(
            BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION,
            ProtocolPostingRequestFieldSets.openAccountingPositionFields(),
            OPENING_POSITION_EVIDENCE_PROFILE,
            "Opening-position writes are reserved for the one-time adoption window before the first committed posting and may touch only asset, liability, or equity accounts."),
        entryKindFacts(
            BookkeepingEntryKind.REVERSAL_ADJUSTMENT,
            ProtocolPostingRequestFieldSets.reversalAdjustmentFields(),
            REVERSAL_EVIDENCE_PROFILE,
            "Reversal writes are contingent cleanup paths that must negate one existing posting exactly and therefore are not substitutes for forward-originating operational journals."));
  }

  private static List<RequestSurfaceFacts.JournalRecipeFacts> journalRecipes() {
    return List.of(
        recipeFacts(
            JournalRecipeKind.CASH_REVENUE,
            ProtocolPostingRequestFieldSets.cashRevenueRecipeFields(),
            CASH_REVENUE_EVIDENCE_PROFILE,
            "Recipe-backed journal that debits one asset cash account and credits one revenue account."),
        recipeFacts(
            JournalRecipeKind.CASH_EXPENSE,
            ProtocolPostingRequestFieldSets.cashExpenseRecipeFields(),
            CASH_EXPENSE_EVIDENCE_PROFILE,
            "Recipe-backed journal that debits one expense account and credits one asset cash account."),
        recipeFacts(
            JournalRecipeKind.EQUITY_CONTRIBUTION,
            ProtocolPostingRequestFieldSets.equityContributionRecipeFields(),
            EQUITY_CONTRIBUTION_EVIDENCE_PROFILE,
            "Recipe-backed journal that debits one asset cash account and credits one equity contribution account."),
        recipeFacts(
            JournalRecipeKind.EQUITY_WITHDRAWAL,
            ProtocolPostingRequestFieldSets.equityWithdrawalRecipeFields(),
            EQUITY_WITHDRAWAL_EVIDENCE_PROFILE,
            "Recipe-backed journal that debits one equity withdrawal account and credits one asset cash account."));
  }

  private static List<RequestSurfaceFacts.EvidenceProfileFacts> evidenceProfiles() {
    return List.of(
        evidenceProfile(
            GENERAL_JOURNAL_EVIDENCE_PROFILE,
            new RequestSurfaceFacts.SourceDocumentTypeFacts(
                SourceDocumentTypePolicyMode.PATTERN_ONLY,
                List.of(),
                "Source-document types remain caller-authored tokens constrained by the published token pattern for direct operational journals."),
            "Default evidence profile for direct operational journals, including transfers and dated adjustments."),
        evidenceProfile(
            CASH_REVENUE_EVIDENCE_PROFILE,
            new RequestSurfaceFacts.SourceDocumentTypeFacts(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("cash-receipt", "bank-deposit", "card-settlement"),
                "Accepted source-document types for the cash-revenue recipe."),
            "Evidence profile for the cash-revenue recipe."),
        evidenceProfile(
            CASH_EXPENSE_EVIDENCE_PROFILE,
            new RequestSurfaceFacts.SourceDocumentTypeFacts(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("expense-receipt", "cash-disbursement", "bank-payment-confirmation"),
                "Accepted source-document types for the cash-expense recipe."),
            "Evidence profile for the cash-expense recipe."),
        evidenceProfile(
            EQUITY_CONTRIBUTION_EVIDENCE_PROFILE,
            new RequestSurfaceFacts.SourceDocumentTypeFacts(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("equity-contribution", "capital-deposit", "bank-deposit"),
                "Accepted source-document types for the equity-contribution recipe."),
            "Evidence profile for the equity-contribution recipe."),
        evidenceProfile(
            EQUITY_WITHDRAWAL_EVIDENCE_PROFILE,
            new RequestSurfaceFacts.SourceDocumentTypeFacts(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("equity-withdrawal", "distribution-payment", "bank-payment-confirmation"),
                "Accepted source-document types for the equity-withdrawal recipe."),
            "Evidence profile for the equity-withdrawal recipe."),
        evidenceProfile(
            OPENING_POSITION_EVIDENCE_PROFILE,
            new RequestSurfaceFacts.SourceDocumentTypeFacts(
                SourceDocumentTypePolicyMode.PATTERN_ONLY,
                List.of(),
                "Source-document types remain caller-authored tokens constrained by the published token pattern for opening-position entries."),
            "Evidence profile for one opening-position request."),
        evidenceProfile(
            REVERSAL_EVIDENCE_PROFILE,
            new RequestSurfaceFacts.SourceDocumentTypeFacts(
                SourceDocumentTypePolicyMode.PATTERN_ONLY,
                List.of(),
                "Source-document types remain caller-authored tokens constrained by the published token pattern for reversal entries."),
            "Evidence profile for one reversal request."));
  }

  private static RequestSurfaceFacts.EvidenceRequirementFacts postEntryEvidence() {
    return new RequestSurfaceFacts.EvidenceRequirementFacts(
        "Every posting request must retain at least one source document with the full six-field evidence payload.",
        1,
        ProtocolPostEntryFields.sourceDocumentFields());
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
            OperationId.TRANSFER_PERIOD_RESULT, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.PERIOD_SUMMARY, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.INCOME_STATEMENT, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.CHANGES_IN_EQUITY, TemporalScopeArchetype.BOUNDED_PERIOD),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.TRIAL_BALANCE, TemporalScopeArchetype.AS_OF_DATE),
        new RequestSurfaceFacts.CommandTemporalScopeFacts(
            OperationId.FINANCIAL_POSITION, TemporalScopeArchetype.AS_OF_DATE));
  }

  private static RequestSurfaceFacts.PostEntryKindFacts entryKindFacts(
      BookkeepingEntryKind entryKind,
      Set<String> requiredTopLevelFields,
      String evidenceProfileId,
      String semantics) {
    return new RequestSurfaceFacts.PostEntryKindFacts(
        entryKind,
        ProtocolPostEntryFields.topLevelFields().stream()
            .filter(requiredTopLevelFields::contains)
            .toList(),
        ProtocolPostEntryFields.topLevelFields().stream()
            .filter(fieldName -> !requiredTopLevelFields.contains(fieldName))
            .toList(),
        evidenceProfileId,
        semantics);
  }

  private static RequestSurfaceFacts.JournalRecipeFacts recipeFacts(
      JournalRecipeKind recipeKind,
      Set<String> requiredTopLevelFields,
      String evidenceProfileId,
      String semantics) {
    return new RequestSurfaceFacts.JournalRecipeFacts(
        recipeKind,
        ProtocolPostEntryFields.topLevelFields().stream()
            .filter(requiredTopLevelFields::contains)
            .toList(),
        ProtocolPostEntryFields.topLevelFields().stream()
            .filter(fieldName -> !requiredTopLevelFields.contains(fieldName))
            .toList(),
        evidenceProfileId,
        semantics);
  }

  private static RequestSurfaceFacts.EvidenceProfileFacts evidenceProfile(
      String profileId,
      RequestSurfaceFacts.SourceDocumentTypeFacts sourceDocumentTypes,
      String semantics) {
    return new RequestSurfaceFacts.EvidenceProfileFacts(profileId, sourceDocumentTypes, semantics);
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
