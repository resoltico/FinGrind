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
          OperationId.RECORD_EXPENSE_SETTLED,
          OperationId.RECORD_EXPENSE_ON_CREDIT,
          OperationId.RECORD_RECEIPT,
          OperationId.RECORD_PAYMENT,
          OperationId.RECORD_OWNER_CONTRIBUTION,
          OperationId.RECORD_OWNER_WITHDRAWAL,
          OperationId.RECORD_OPENING_POSITION,
          OperationId.RECORD_REVERSAL,
          OperationId.DECLARE_ACCOUNT,
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
