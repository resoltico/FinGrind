package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AmendAccountCommand;
import tools.jackson.databind.node.ObjectNode;

/** Parses account-definition payloads for the amend-account command. */
final class CliAmendAccountRequestParser {
  private CliAmendAccountRequestParser() {}

  static AmendAccountCommand readAmendAccountCommand(ObjectNode rootNode) {
    CliAccountDefinitionRequestParser.AccountDefinition definition =
        CliAccountDefinitionRequestParser.read(
            rootNode,
            "amendAccount",
            "Amend-account request fields must be top-level for direct request files; remove the amendAccount wrapper.");
    return new AmendAccountCommand(
        definition.accountCode(),
        definition.accountName(),
        definition.accountType(),
        definition.accountTaxonomy(),
        definition.unitOfMeasure());
  }
}
