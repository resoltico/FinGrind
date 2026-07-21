package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.protocol.OperationId;
import tools.jackson.databind.node.ObjectNode;

/**
 * Rejects plan-contained initialization because custodian inputs must remain explicit CLI options.
 */
final class CliLedgerPlanEnsureBookParser {
  private CliLedgerPlanEnsureBookParser() {}

  static OpenBookCommand read(ObjectNode ensureBookNode) {
    java.util.Objects.requireNonNull(ensureBookNode, "ensureBookNode");
    throw CliArgumentValueParser.invalid(
        "ensureBook",
        "Ledger plans cannot initialize an attested book. Run "
            + OperationId.OPEN_BOOK.wireName()
            + " with explicit founder credentials before executing a plan.");
  }
}
