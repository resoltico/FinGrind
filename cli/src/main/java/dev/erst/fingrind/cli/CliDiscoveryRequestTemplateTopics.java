package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolRequestTemplateTopics;
import java.util.Optional;

/** Owns the accepted request-template topic inventory for discovery parsing. */
final class CliDiscoveryRequestTemplateTopics {
  private CliDiscoveryRequestTemplateTopics() {}

  static OperationId requireTopic(String token) {
    Optional<dev.erst.fingrind.contract.protocol.ProtocolOperation> operation =
        ProtocolCatalog.findByToken(token);
    if (operation.isEmpty()) {
      throw CliArgumentValueParser.invalid(token, "Unsupported request-template topic: " + token);
    }
    OperationId topic = operation.orElseThrow().id();
    if (ProtocolRequestTemplateTopics.supports(topic)) {
      return topic;
    }
    throw CliArgumentValueParser.invalid(
        token,
        "Unsupported request-template topic: "
            + token
            + ". Run "
            + ProtocolCatalog.operationName(OperationId.PRINT_REQUEST_TEMPLATE)
            + " without a topic for the default scaffold, or select a request-bearing command topic.");
  }
}
