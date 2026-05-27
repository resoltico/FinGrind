package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import tools.jackson.databind.node.ObjectNode;

/** Parses posting-shaped request payloads for direct CLI commands and plan steps. */
final class CliPostingRequestParser {
  private CliPostingRequestParser() {}

  static PostEntryCommand readPostEntryCommand(ObjectNode rootNode) {
    return CliPostEntryRequestParser.readPostEntryCommand(rootNode);
  }

  static DeclareAccountCommand readDeclareAccountCommand(ObjectNode rootNode) {
    return CliDeclareAccountRequestParser.readDeclareAccountCommand(rootNode);
  }
}
