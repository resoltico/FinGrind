package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import tools.jackson.databind.node.ObjectNode;

/** Parses posting-shaped request payloads for direct CLI commands and plan steps. */
final class CliPostingRequestParser {
  private CliPostingRequestParser() {}

  static PostEntryCommand readPostEntryCommand(
      ObjectNode rootNode, @org.jspecify.annotations.Nullable OperationId operationId) {
    return CliPostEntryRequestParser.readPostEntryCommand(rootNode, operationId);
  }

  static DeclareAccountCommand readDeclareAccountCommand(ObjectNode rootNode) {
    return CliDeclareAccountRequestParser.readDeclareAccountCommand(rootNode);
  }

  static DeclareTaxRegistrationCommand readDeclareTaxRegistrationCommand(ObjectNode rootNode) {
    return CliDeclareTaxRegistrationRequestParser.readDeclareTaxRegistrationCommand(rootNode);
  }
}
