package dev.erst.fingrind.contract.protocol;

import java.util.Set;

/** Canonical post-entry request-field sets for typed events and administrative adjustments. */
public final class ProtocolPostingRequestFieldSets {
  private static final Set<String> POST_ENTRY_TOP_LEVEL_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.topLevelFields());
  private static final Set<String> CASH_REVENUE_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> CASH_EXPENSE_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> EQUITY_CONTRIBUTION_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> EQUITY_WITHDRAWAL_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> OPENING_BALANCE_ADJUSTMENT_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.LINES,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> CORRECTION_ADJUSTMENT_FIELDS =
      Set.copyOf(OPENING_BALANCE_ADJUSTMENT_FIELDS);
  private static final Set<String> REVERSAL_ADJUSTMENT_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.LINES,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE,
          ProtocolPostEntryFields.TopLevel.REVERSAL);
  private static final Set<String> EVIDENCE_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.evidenceFields());
  private static final Set<String> SOURCE_DOCUMENT_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.sourceDocumentFields());
  private static final Set<String> APPROVAL_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.approvalFields());
  private static final Set<String> PROVENANCE_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.provenanceFields());
  private static final Set<String> JOURNAL_LINE_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.journalLineFields());
  private static final Set<String> REVERSAL_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.reversalFields());

  private ProtocolPostingRequestFieldSets() {}

  /** Returns the accepted top-level fields shared by post-entry requests. */
  public static Set<String> postEntryTopLevelFields() {
    return POST_ENTRY_TOP_LEVEL_FIELDS;
  }

  /** Returns the accepted top-level fields for {@code CASH_REVENUE} post-entry requests. */
  public static Set<String> cashRevenueFields() {
    return CASH_REVENUE_FIELDS;
  }

  /** Returns the accepted top-level fields for {@code CASH_EXPENSE} post-entry requests. */
  public static Set<String> cashExpenseFields() {
    return CASH_EXPENSE_FIELDS;
  }

  /** Returns the accepted top-level fields for equity contribution post-entry requests. */
  public static Set<String> equityContributionFields() {
    return EQUITY_CONTRIBUTION_FIELDS;
  }

  /** Returns the accepted top-level fields for equity withdrawal post-entry requests. */
  public static Set<String> equityWithdrawalFields() {
    return EQUITY_WITHDRAWAL_FIELDS;
  }

  /** Returns the accepted top-level fields for opening-balance adjustment requests. */
  public static Set<String> openingBalanceAdjustmentFields() {
    return OPENING_BALANCE_ADJUSTMENT_FIELDS;
  }

  /** Returns the accepted top-level fields for correction-adjustment requests. */
  public static Set<String> correctionAdjustmentFields() {
    return CORRECTION_ADJUSTMENT_FIELDS;
  }

  /** Returns the accepted top-level fields for reversal-adjustment requests. */
  public static Set<String> reversalAdjustmentFields() {
    return REVERSAL_ADJUSTMENT_FIELDS;
  }

  /** Returns the accepted nested fields for accounting evidence objects. */
  public static Set<String> evidenceFields() {
    return EVIDENCE_FIELDS;
  }

  /** Returns the accepted nested fields for source-document reference objects. */
  public static Set<String> sourceDocumentFields() {
    return SOURCE_DOCUMENT_FIELDS;
  }

  /** Returns the accepted nested fields for approval reference objects. */
  public static Set<String> approvalFields() {
    return APPROVAL_FIELDS;
  }

  /** Returns the accepted nested fields for provenance objects. */
  public static Set<String> provenanceFields() {
    return PROVENANCE_FIELDS;
  }

  /** Returns the accepted nested fields for journal-line objects. */
  public static Set<String> journalLineFields() {
    return JOURNAL_LINE_FIELDS;
  }

  /** Returns the accepted nested fields for reversal-reference objects. */
  public static Set<String> reversalFields() {
    return REVERSAL_FIELDS;
  }
}
