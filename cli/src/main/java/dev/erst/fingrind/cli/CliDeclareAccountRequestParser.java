package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import tools.jackson.databind.node.ObjectNode;

/** Parses declare-account request payloads into command objects. */
final class CliDeclareAccountRequestParser {
  private CliDeclareAccountRequestParser() {}

  static DeclareAccountCommand readDeclareAccountCommand(ObjectNode rootNode) {
    CliAccountDefinitionRequestParser.AccountDefinition definition =
        CliAccountDefinitionRequestParser.read(
            rootNode,
            ProtocolLedgerPlanFields.Step.DECLARE_ACCOUNT,
            "Declare-account request fields must be top-level for direct request files; remove the declareAccount wrapper.");
    return new DeclareAccountCommand(
        definition.accountCode(),
        definition.accountName(),
        definition.accountType(),
        definition.accountTaxonomy(),
        definition.unitOfMeasure());
  }
}
