package dev.erst.fingrind.contract.protocol;

import java.util.Set;

/** Canonical nested request-field sets used inside posting-shaped request documents. */
public final class ProtocolPostingNestedFieldSets {
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
  private static final Set<String> OPENING_BALANCE_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.openingBalanceFields());
  private static final Set<String> REVERSAL_REFERENCE_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.reversalFields());
  private static final Set<String> TAX_FIELDS = Set.copyOf(ProtocolPostEntryFields.taxFields());
  private static final Set<String> FOREIGN_EXCHANGE_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.foreignExchangeFields());
  private static final Set<String> QUOTED_RATE_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.quotedRateFields());

  private ProtocolPostingNestedFieldSets() {}

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

  /** Returns the accepted nested fields for opening-balance objects. */
  public static Set<String> openingBalanceFields() {
    return OPENING_BALANCE_FIELDS;
  }

  /** Returns the accepted nested fields for reversal-reference objects. */
  public static Set<String> reversalFields() {
    return REVERSAL_REFERENCE_FIELDS;
  }

  /** Returns the accepted nested fields for request-side tax selectors. */
  public static Set<String> taxFields() {
    return TAX_FIELDS;
  }

  /** Returns the accepted nested fields for request-side foreign-exchange facts. */
  public static Set<String> foreignExchangeFields() {
    return FOREIGN_EXCHANGE_FIELDS;
  }

  /** Returns the accepted nested fields for quoted exchange-rate objects. */
  public static Set<String> quotedRateFields() {
    return QUOTED_RATE_FIELDS;
  }
}
