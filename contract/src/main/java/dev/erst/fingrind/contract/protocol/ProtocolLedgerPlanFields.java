package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical JSON field names accepted by ledger-plan request documents. */
public final class ProtocolLedgerPlanFields {
  private ProtocolLedgerPlanFields() {}

  /** Returns top-level ledger-plan request fields in stable wire order. */
  public static List<String> planFields() {
    return List.of(Plan.PLAN_ID, Plan.STEPS);
  }

  /** Returns ledger-plan step object fields in stable wire order. */
  public static List<String> stepFields() {
    return List.of(
        Step.STEP_ID,
        Step.KIND,
        Step.POSTING,
        Step.DECLARE_ACCOUNT,
        Step.QUERY,
        Step.ASSERTION,
        Step.POSTING_ID);
  }

  /** Returns ledger-plan query object fields in stable wire order. */
  public static List<String> queryFields() {
    return List.of(
        Query.ACCOUNT_CODE,
        Query.EFFECTIVE_DATE_FROM,
        Query.EFFECTIVE_DATE_TO,
        Query.LIMIT,
        Query.CURSOR,
        Query.OFFSET);
  }

  /** Returns ledger-plan assertion object fields in stable wire order. */
  public static List<String> assertionFields() {
    return List.of(
        Assertion.KIND,
        Assertion.ACCOUNT_CODE,
        Assertion.POSTING_ID,
        Assertion.EFFECTIVE_DATE_FROM,
        Assertion.EFFECTIVE_DATE_TO,
        Assertion.CURRENCY_CODE,
        Assertion.NET_AMOUNT,
        Assertion.BALANCE_SIDE);
  }

  /** Ledger-plan top-level field names. */
  public static final class Plan {
    /** Caller-supplied plan identifier. */
    public static final String PLAN_ID = "planId";

    /** Ordered executable step array. */
    public static final String STEPS = "steps";

    private Plan() {}
  }

  /** Ledger-plan step field names. */
  public static final class Step {
    /** Caller-supplied step identifier. */
    public static final String STEP_ID = "stepId";

    /** Canonical operation or assertion kind. */
    public static final String KIND = "kind";

    /** Posting request payload. */
    public static final String POSTING = "posting";

    /** Account declaration payload. */
    public static final String DECLARE_ACCOUNT = "declareAccount";

    /** Query payload. */
    public static final String QUERY = "query";

    /** Assertion payload. */
    public static final String ASSERTION = "assertion";

    /** Posting identifier payload. */
    public static final String POSTING_ID = "postingId";

    private Step() {}
  }

  /** Ledger-plan query field names. */
  public static final class Query {
    /** Account code filter or target. */
    public static final String ACCOUNT_CODE = "accountCode";

    /** Inclusive effective-date lower bound. */
    public static final String EFFECTIVE_DATE_FROM = "effectiveDateFrom";

    /** Inclusive effective-date upper bound. */
    public static final String EFFECTIVE_DATE_TO = "effectiveDateTo";

    /** Page-size field. */
    public static final String LIMIT = "limit";

    /** Opaque posting-page cursor field. */
    public static final String CURSOR = "cursor";

    /** Page-offset field. */
    public static final String OFFSET = "offset";

    private Query() {}
  }

  /** Ledger-plan assertion field names. */
  public static final class Assertion {
    /** Canonical assertion kind nested inside an assert step. */
    public static final String KIND = "kind";

    /** Account code target. */
    public static final String ACCOUNT_CODE = "accountCode";

    /** Posting identifier target. */
    public static final String POSTING_ID = "postingId";

    /** Inclusive effective-date lower bound. */
    public static final String EFFECTIVE_DATE_FROM = "effectiveDateFrom";

    /** Inclusive effective-date upper bound. */
    public static final String EFFECTIVE_DATE_TO = "effectiveDateTo";

    /** Currency bucket identifier. */
    public static final String CURRENCY_CODE = "currencyCode";

    /** Expected net amount. */
    public static final String NET_AMOUNT = "netAmount";

    /** Expected normal-balance side. */
    public static final String BALANCE_SIDE = "balanceSide";

    private Assertion() {}
  }
}
