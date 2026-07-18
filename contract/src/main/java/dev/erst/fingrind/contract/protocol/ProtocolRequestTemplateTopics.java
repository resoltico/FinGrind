package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;

/** Canonical request-template topic inventory for raw scaffold publishing. */
public final class ProtocolRequestTemplateTopics {
  private static final List<OperationId> TOPICS =
      List.of(
          OperationId.POST_ENTRY,
          OperationId.PREFLIGHT_ENTRY,
          OperationId.RECORD_SALE_SETTLED,
          OperationId.RECORD_SALE_ON_CREDIT,
          OperationId.RECORD_PURCHASE_SETTLED,
          OperationId.RECORD_PURCHASE_ON_CREDIT,
          OperationId.RECORD_INVENTORY_CAPITALIZATION_SETTLED,
          OperationId.RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT,
          OperationId.RECORD_INVENTORY_WRITE_DOWN,
          OperationId.RECORD_INVENTORY_SHRINKAGE,
          OperationId.RECORD_INVENTORY_COUNT_INCREASE,
          OperationId.RECORD_PREPAYMENT,
          OperationId.RECORD_DEFERRED_REVENUE,
          OperationId.RECORD_ACCRUED_EXPENSE,
          OperationId.RECORD_ACCRUAL_CUTOFF_RECOGNITION,
          OperationId.RECORD_ACCRUED_EXPENSE_SETTLEMENT,
          OperationId.RECORD_LATVIAN_MONTHLY_PAYROLL,
          OperationId.RECORD_LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
          OperationId.RECORD_LATVIAN_PAYROLL_STATE_REMITTANCE,
          OperationId.RECORD_FIXED_ASSET_CAPITALIZATION,
          OperationId.RECORD_FIXED_ASSET_DEPRECIATION,
          OperationId.RECORD_FIXED_ASSET_DISPOSAL,
          OperationId.RECORD_FINANCING_BORROWING,
          OperationId.RECORD_FINANCING_PRINCIPAL_REPAYMENT,
          OperationId.RECORD_FINANCING_INTEREST_ACCRUAL,
          OperationId.RECORD_FINANCING_INTEREST_PAYMENT,
          OperationId.RECORD_FOREIGN_CURRENCY_OBLIGATION,
          OperationId.RECORD_REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
          OperationId.RECORD_EXPENSE_SETTLED,
          OperationId.RECORD_EXPENSE_ON_CREDIT,
          OperationId.RECORD_RECEIPT,
          OperationId.RECORD_PAYMENT,
          OperationId.RECORD_OWNER_CONTRIBUTION,
          OperationId.RECORD_OWNER_WITHDRAWAL,
          OperationId.RECORD_OPENING_POSITION,
          OperationId.RECORD_REVERSAL,
          OperationId.DECLARE_ACCOUNT,
          OperationId.AMEND_ACCOUNT,
          OperationId.RETIRE_ACCOUNT,
          OperationId.DECLARE_TAX_REGISTRATION);

  private ProtocolRequestTemplateTopics() {}

  /** Returns the stable topic inventory accepted by print-request-template. */
  public static List<OperationId> topics() {
    return TOPICS;
  }

  /** Returns whether one operation id owns a raw request-template scaffold. */
  public static boolean supports(OperationId operationId) {
    return TOPICS.contains(Objects.requireNonNull(operationId, "operationId"));
  }

  /** Returns the rendered invocation syntax for the accepted topic inventory. */
  public static String syntax() {
    return "["
        + TOPICS.stream()
            .map(OperationId::wireName)
            .collect(java.util.stream.Collectors.joining("|"))
        + "]";
  }

  /** Returns the stable wire names for the accepted topic inventory. */
  public static List<String> topicNames() {
    return TOPICS.stream().map(OperationId::wireName).toList();
  }
}
