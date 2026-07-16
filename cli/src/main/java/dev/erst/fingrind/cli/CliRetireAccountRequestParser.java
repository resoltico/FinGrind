package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;

import dev.erst.fingrind.contract.bookkeeping.RetireAccountCommand;
import dev.erst.fingrind.contract.protocol.ProtocolSharedRequestFields;
import dev.erst.fingrind.core.AccountCode;
import java.util.Set;
import tools.jackson.databind.node.ObjectNode;

/** Parses the minimal account-retirement request payload. */
final class CliRetireAccountRequestParser {
  private static final Set<String> FIELDS = Set.of(ProtocolSharedRequestFields.ACCOUNT_CODE);

  private CliRetireAccountRequestParser() {}

  static RetireAccountCommand readRetireAccountCommand(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, FIELDS);
    return new RetireAccountCommand(
        new AccountCode(requiredText(rootNode, ProtocolSharedRequestFields.ACCOUNT_CODE)));
  }
}
