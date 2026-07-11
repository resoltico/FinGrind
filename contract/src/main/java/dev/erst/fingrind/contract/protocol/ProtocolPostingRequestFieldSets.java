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
  private static final Set<String> SALE_SETTLED_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.TAX,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> SALE_ON_CREDIT_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.TAX,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  static final Set<String> PURCHASE_SETTLED_FIELDS =
      inventoryFields(
          ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.QUANTITY,
          ProtocolPostEntryFields.TopLevel.UNIT_COST,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.TAX);
  static final Set<String> PURCHASE_ON_CREDIT_FIELDS =
      inventoryFields(
          ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.QUANTITY,
          ProtocolPostEntryFields.TopLevel.UNIT_COST,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.TAX);
  static final Set<String> INVENTORY_CAPITALIZATION_SETTLED_FIELDS =
      inventoryFields(
          ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.TAX);
  static final Set<String> INVENTORY_CAPITALIZATION_ON_CREDIT_FIELDS =
      inventoryFields(
          ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.TAX);
  static final Set<String> INVENTORY_WRITE_DOWN_FIELDS =
      inventoryFields(
          ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.WRITE_DOWN_LOSS_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT);
  static final Set<String> INVENTORY_SHRINKAGE_FIELDS =
      inventoryFields(
          ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.SHRINKAGE_LOSS_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.QUANTITY);
  static final Set<String> INVENTORY_COUNT_INCREASE_FIELDS =
      inventoryFields(
          ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.COUNT_GAIN_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.QUANTITY,
          ProtocolPostEntryFields.TopLevel.UNIT_COST);
  private static final Set<String> EXPENSE_SETTLED_FIELDS =
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
  private static final Set<String> EXPENSE_ON_CREDIT_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.TAX,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> RECEIPT_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.SETTLEMENT_ADJUNCT,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> PAYMENT_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.SETTLEMENT_ADJUNCT,
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
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE,
          ProtocolPostEntryFields.TopLevel.REVERSAL);

  private ProtocolPostingRequestFieldSets() {}

  private static Set<String> inventoryFields(String... variantFields) {
    var fields =
        new java.util.LinkedHashSet<>(
            Set.of(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE));
    java.util.Collections.addAll(fields, variantFields);
    return Set.copyOf(fields);
  }

  /** Returns the accepted top-level fields shared by post-entry requests. */
  public static Set<String> postEntryTopLevelFields() {
    return POST_ENTRY_TOP_LEVEL_FIELDS;
  }

  /** Returns the accepted top-level fields for direct operational journal requests. */
  public static Set<String> journalDirectFields() {
    return JOURNAL_DIRECT_FIELDS;
  }

  /** Returns the accepted top-level fields for sale requests. */
  public static Set<String> saleSettledFields() {
    return SALE_SETTLED_FIELDS;
  }

  /** Returns the accepted top-level fields for sale-on-credit requests. */
  public static Set<String> saleOnCreditFields() {
    return SALE_ON_CREDIT_FIELDS;
  }

  /** Returns the accepted top-level fields for settled expense requests. */
  public static Set<String> expenseSettledFields() {
    return EXPENSE_SETTLED_FIELDS;
  }

  /** Returns the accepted top-level fields for expense-on-credit requests. */
  public static Set<String> expenseOnCreditFields() {
    return EXPENSE_ON_CREDIT_FIELDS;
  }

  /** Returns the accepted top-level fields for receipt requests. */
  public static Set<String> receiptFields() {
    return RECEIPT_FIELDS;
  }

  /** Returns the accepted top-level fields for payment requests. */
  public static Set<String> paymentFields() {
    return PAYMENT_FIELDS;
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
