package dev.erst.fingrind.contract.protocol;

import java.util.Set;

/** Canonical executable ledger-plan request-field sets. */
public final class ProtocolLedgerPlanRequestFieldSets {
  private static final Set<String> LEDGER_PLAN_FIELDS =
      Set.copyOf(ProtocolLedgerPlanFields.planFields());
  private static final Set<String> LEDGER_STEP_FIELDS =
      Set.copyOf(ProtocolLedgerPlanFields.stepFields());
  private static final Set<String> LEDGER_QUERY_FIELDS =
      Set.copyOf(ProtocolLedgerPlanFields.queryFields());
  private static final Set<String> LEDGER_ASSERTION_FIELDS =
      Set.copyOf(ProtocolLedgerPlanFields.assertionFields());
  private static final Set<String> LIST_ACCOUNTS_QUERY_FIELDS =
      Set.of(ProtocolLedgerPlanFields.Query.LIMIT, ProtocolLedgerPlanFields.Query.CURSOR);
  private static final Set<String> LIST_POSTINGS_QUERY_FIELDS =
      Set.of(
          ProtocolLedgerPlanFields.Query.ACCOUNT_CODE,
          ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM,
          ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO,
          ProtocolLedgerPlanFields.Query.LIMIT,
          ProtocolLedgerPlanFields.Query.CURSOR);
  private static final Set<String> ACCOUNT_BALANCE_QUERY_FIELDS =
      Set.of(
          ProtocolLedgerPlanFields.Query.ACCOUNT_CODE,
          ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM,
          ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO);
  private static final Set<String> ACCOUNT_DECLARED_ASSERTION_FIELDS =
      Set.of(
          ProtocolLedgerPlanFields.Assertion.KIND, ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE);
  private static final Set<String> ACCOUNT_ACTIVE_ASSERTION_FIELDS =
      Set.of(
          ProtocolLedgerPlanFields.Assertion.KIND, ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE);
  private static final Set<String> POSTING_EXISTS_ASSERTION_FIELDS =
      Set.of(
          ProtocolLedgerPlanFields.Assertion.KIND, ProtocolLedgerPlanFields.Assertion.POSTING_ID);
  private static final Set<String> ACCOUNT_BALANCE_ASSERTION_FIELDS =
      Set.of(
          ProtocolLedgerPlanFields.Assertion.KIND,
          ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE,
          ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM,
          ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO,
          ProtocolLedgerPlanFields.Assertion.NET_AMOUNT,
          ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE);

  private ProtocolLedgerPlanRequestFieldSets() {}

  /** Returns the accepted top-level fields for ledger-plan documents. */
  public static Set<String> ledgerPlanFields() {
    return LEDGER_PLAN_FIELDS;
  }

  /** Returns the accepted nested fields for ledger-plan step objects. */
  public static Set<String> ledgerStepFields() {
    return LEDGER_STEP_FIELDS;
  }

  /** Returns the accepted nested fields for ledger-plan query objects. */
  public static Set<String> ledgerQueryFields() {
    return LEDGER_QUERY_FIELDS;
  }

  /** Returns the accepted nested fields for list-accounts query objects. */
  public static Set<String> listAccountsQueryFields() {
    return LIST_ACCOUNTS_QUERY_FIELDS;
  }

  /** Returns the accepted nested fields for list-postings query objects. */
  public static Set<String> listPostingsQueryFields() {
    return LIST_POSTINGS_QUERY_FIELDS;
  }

  /** Returns the accepted nested fields for account-balance query objects. */
  public static Set<String> accountBalanceQueryFields() {
    return ACCOUNT_BALANCE_QUERY_FIELDS;
  }

  /** Returns the accepted nested fields for ledger-plan assertion objects. */
  public static Set<String> ledgerAssertionFields() {
    return LEDGER_ASSERTION_FIELDS;
  }

  /** Returns the accepted nested fields for one concrete ledger assertion kind. */
  public static Set<String> ledgerAssertionFields(LedgerAssertionKind kind) {
    return switch (kind) {
      case ACCOUNT_DECLARED -> ACCOUNT_DECLARED_ASSERTION_FIELDS;
      case ACCOUNT_ACTIVE -> ACCOUNT_ACTIVE_ASSERTION_FIELDS;
      case POSTING_EXISTS -> POSTING_EXISTS_ASSERTION_FIELDS;
      case ACCOUNT_BALANCE_EQUALS -> ACCOUNT_BALANCE_ASSERTION_FIELDS;
    };
  }
}
