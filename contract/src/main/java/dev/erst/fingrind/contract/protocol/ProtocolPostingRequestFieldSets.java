package dev.erst.fingrind.contract.protocol;

import java.util.Set;

/** Canonical post-entry request-field sets for direct journals and typed business entries. */
public final class ProtocolPostingRequestFieldSets {
  private static final Set<String> POST_ENTRY_TOP_LEVEL_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.topLevelFields());
  private static final Set<String> JOURNAL_DIRECT_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.LINES,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> SALE_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.TAX,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> EXPENSE_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.TAX,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> OWNER_CONTRIBUTION_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> OWNER_WITHDRAWAL_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> OPENING_POSITION_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.OPENING_BALANCES,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> REVERSAL_ENTRY_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.LINES,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE,
          ProtocolPostEntryFields.TopLevel.REVERSAL);

  private ProtocolPostingRequestFieldSets() {}

  /** Returns the accepted top-level fields shared by post-entry requests. */
  public static Set<String> postEntryTopLevelFields() {
    return POST_ENTRY_TOP_LEVEL_FIELDS;
  }

  /** Returns the accepted top-level fields for direct operational journal requests. */
  public static Set<String> journalDirectFields() {
    return JOURNAL_DIRECT_FIELDS;
  }

  /** Returns the accepted top-level fields for sale requests. */
  public static Set<String> saleFields() {
    return SALE_FIELDS;
  }

  /** Returns the accepted top-level fields for expense requests. */
  public static Set<String> expenseFields() {
    return EXPENSE_FIELDS;
  }

  /** Returns the accepted top-level fields for owner-contribution requests. */
  public static Set<String> ownerContributionFields() {
    return OWNER_CONTRIBUTION_FIELDS;
  }

  /** Returns the accepted top-level fields for owner-withdrawal requests. */
  public static Set<String> ownerWithdrawalFields() {
    return OWNER_WITHDRAWAL_FIELDS;
  }

  /** Returns the accepted top-level fields for opening-position requests. */
  public static Set<String> openingPositionFields() {
    return OPENING_POSITION_FIELDS;
  }

  /** Returns the accepted top-level fields for reversal requests. */
  public static Set<String> reversalEntryFields() {
    return REVERSAL_ENTRY_FIELDS;
  }
}
