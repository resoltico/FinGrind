package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical post-entry request field names shared by parser and machine contract surfaces. */
public final class ProtocolPostEntryFields {
  private ProtocolPostEntryFields() {}

  /** Returns top-level posting request fields in stable wire order. */
  public static List<String> topLevelFields() {
    return List.of(TopLevel.EFFECTIVE_DATE, TopLevel.LINES, TopLevel.PROVENANCE, TopLevel.REVERSAL);
  }

  /** Returns journal-line request fields in stable wire order. */
  public static List<String> journalLineFields() {
    return List.of(
        JournalLine.ACCOUNT_CODE, JournalLine.SIDE, JournalLine.CURRENCY_CODE, JournalLine.AMOUNT);
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

  /** Returns reversal request fields in stable wire order. */
  public static List<String> reversalFields() {
    return List.of(Reversal.PRIOR_POSTING_ID, Reversal.REASON);
  }

  /** Top-level posting request fields. */
  public static final class TopLevel {
    public static final String EFFECTIVE_DATE = "effectiveDate";
    public static final String LINES = "lines";
    public static final String PROVENANCE = "provenance";
    public static final String REVERSAL = "reversal";
    public static final String CORRECTION = "correction";

    private TopLevel() {}
  }

  /** Journal-line request fields. */
  public static final class JournalLine {
    public static final String ACCOUNT_CODE = "accountCode";
    public static final String SIDE = "side";
    public static final String CURRENCY_CODE = "currencyCode";
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

  /** Reversal request fields. */
  public static final class Reversal {
    public static final String PRIOR_POSTING_ID = "priorPostingId";
    public static final String REASON = "reason";
    public static final String KIND = "kind";

    private Reversal() {}
  }
}
