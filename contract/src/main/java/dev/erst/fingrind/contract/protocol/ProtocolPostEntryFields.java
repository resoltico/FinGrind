package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical post-entry request field names shared by parser and machine contract surfaces. */
public final class ProtocolPostEntryFields {
  private ProtocolPostEntryFields() {}

  /** Returns top-level posting request fields in stable wire order. */
  public static List<String> topLevelFields() {
    return List.of(
        TopLevel.ENTRY_KIND,
        TopLevel.EFFECTIVE_DATE,
        TopLevel.CASH_ACCOUNT_CODE,
        TopLevel.REVENUE_ACCOUNT_CODE,
        TopLevel.EXPENSE_ACCOUNT_CODE,
        TopLevel.EQUITY_ACCOUNT_CODE,
        TopLevel.AMOUNT,
        TopLevel.LINES,
        TopLevel.EVIDENCE,
        TopLevel.PROVENANCE,
        TopLevel.REVERSAL);
  }

  /** Returns journal-line request fields in stable wire order. */
  public static List<String> journalLineFields() {
    return List.of(JournalLine.ACCOUNT_CODE, JournalLine.SIDE, JournalLine.AMOUNT);
  }

  /** Returns provenance request fields in stable wire order. */
  public static List<String> provenanceFields() {
    return List.of(
        Provenance.ACTOR_ID,
        Provenance.ACTOR_TYPE,
        Provenance.COMMAND_ID,
        Provenance.IDEMPOTENCY_KEY,
        Provenance.CAUSATION_ID,
        Provenance.CORRELATION_ID);
  }

  /** Returns evidence request fields in stable wire order. */
  public static List<String> evidenceFields() {
    return List.of(Evidence.SOURCE_DOCUMENTS, Evidence.APPROVALS);
  }

  /** Returns source-document evidence fields in stable wire order. */
  public static List<String> sourceDocumentFields() {
    return List.of(
        SourceDocument.SOURCE_DOCUMENT_ID,
        SourceDocument.SOURCE_DOCUMENT_TYPE,
        SourceDocument.DOCUMENT_DATE,
        SourceDocument.CAPTURED_AT,
        SourceDocument.STORAGE_LOCATOR,
        SourceDocument.CONTENT_SHA256);
  }

  /** Returns approval evidence fields in stable wire order. */
  public static List<String> approvalFields() {
    return List.of(
        Approval.APPROVAL_ID,
        Approval.APPROVAL_TYPE,
        Approval.APPROVER_ID,
        Approval.APPROVER_TYPE,
        Approval.DECISION,
        Approval.APPROVED_AT);
  }

  /** Returns reversal request fields in stable wire order. */
  public static List<String> reversalFields() {
    return List.of(Reversal.PRIOR_POSTING_ID, Reversal.REASON);
  }

  /** Top-level posting request fields. */
  public static final class TopLevel {
    public static final String ENTRY_KIND = "entryKind";
    public static final String EFFECTIVE_DATE = "effectiveDate";
    public static final String CASH_ACCOUNT_CODE = "cashAccountCode";
    public static final String REVENUE_ACCOUNT_CODE = "revenueAccountCode";
    public static final String EXPENSE_ACCOUNT_CODE = "expenseAccountCode";
    public static final String EQUITY_ACCOUNT_CODE = "equityAccountCode";
    public static final String AMOUNT = "amount";
    public static final String LINES = "lines";
    public static final String EVIDENCE = "evidence";
    public static final String PROVENANCE = "provenance";
    public static final String REVERSAL = "reversal";
    public static final String CORRECTION = "correction";

    private TopLevel() {}
  }

  /** Journal-line request fields. */
  public static final class JournalLine {
    public static final String ACCOUNT_CODE = ProtocolSharedRequestFields.ACCOUNT_CODE;
    public static final String SIDE = "side";
    public static final String AMOUNT = "amount";

    private JournalLine() {}
  }

  /** Provenance request fields. */
  public static final class Provenance {
    public static final String ACTOR_ID = "actorId";
    public static final String ACTOR_TYPE = "actorType";
    public static final String COMMAND_ID = "commandId";
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";
    public static final String CAUSATION_ID = "causationId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String REASON = "reason";
    public static final String RECORDED_AT = "recordedAt";
    public static final String SOURCE_CHANNEL = "sourceChannel";

    private Provenance() {}
  }

  /** Evidence request fields. */
  public static final class Evidence {
    public static final String SOURCE_DOCUMENTS = "sourceDocuments";
    public static final String APPROVALS = "approvals";

    private Evidence() {}
  }

  /** Source-document evidence fields. */
  public static final class SourceDocument {
    public static final String SOURCE_DOCUMENT_ID = "sourceDocumentId";
    public static final String SOURCE_DOCUMENT_TYPE = "sourceDocumentType";
    public static final String DOCUMENT_DATE = "documentDate";
    public static final String CAPTURED_AT = "capturedAt";
    public static final String STORAGE_LOCATOR = "storageLocator";
    public static final String CONTENT_SHA256 = "contentSha256";

    private SourceDocument() {}
  }

  /** Approval evidence fields. */
  public static final class Approval {
    public static final String APPROVAL_ID = "approvalId";
    public static final String APPROVAL_TYPE = "approvalType";
    public static final String APPROVER_ID = "approverId";
    public static final String APPROVER_TYPE = "approverType";
    public static final String DECISION = "decision";
    public static final String APPROVED_AT = "approvedAt";

    private Approval() {}
  }

  /** Reversal request fields. */
  public static final class Reversal {
    public static final String PRIOR_POSTING_ID = "priorPostingId";
    public static final String REASON = "reason";
    public static final String KIND = "kind";

    private Reversal() {}
  }
}
