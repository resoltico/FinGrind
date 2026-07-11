package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Owns the accepted request-template topic inventory for discovery parsing. */
final class CliDiscoveryRequestTemplateTopics {
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
  private static final Set<OperationId> SUPPORTED_TOPICS = Set.copyOf(TOPICS);

  private CliDiscoveryRequestTemplateTopics() {}

  static OperationId requireTopic(String token) {
    Optional<dev.erst.fingrind.contract.protocol.ProtocolOperation> operation =
        ProtocolCatalog.findByToken(token);
    if (operation.isEmpty()) {
      throw CliArgumentValueParser.invalid(token, "Unsupported request-template topic: " + token);
    }
    OperationId topic = operation.orElseThrow().id();
    if (SUPPORTED_TOPICS.contains(topic)) {
      return topic;
    }
    throw CliArgumentValueParser.invalid(
        token,
        "Unsupported request-template topic: " + token + ". Use " + supportedTopicNames() + ".");
  }

  private static String supportedTopicNames() {
    return TOPICS.stream()
        .map(ProtocolCatalog::operationName)
        .collect(java.util.stream.Collectors.joining(", "));
  }
}
