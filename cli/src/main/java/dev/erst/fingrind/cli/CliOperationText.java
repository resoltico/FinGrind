package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;

/** Shared CLI-facing text helpers that derive operation references from the protocol catalog. */
final class CliOperationText {
  private CliOperationText() {}

  static String unsupportedCsvOutput(OperationId operationId) {
    return operationName(operationId) + " does not support CSV output.";
  }

  static String listPostingsCursorRepairHint() {
    String listPostings = operationName(OperationId.LIST_POSTINGS);
    return "Rerun "
        + listPostings
        + " without "
        + ProtocolOptions.CURSOR
        + ", or pass the opaque nextCursor value returned by a prior successful "
        + listPostings
        + " response.";
  }

  static String listAccountsCursorRepairHint() {
    String listAccounts = operationName(OperationId.LIST_ACCOUNTS);
    return "Rerun "
        + listAccounts
        + " without "
        + ProtocolOptions.CURSOR
        + ", or pass the opaque nextCursor value returned by a prior successful "
        + listAccounts
        + " response.";
  }

  static String listTaxRegistrationsCursorRepairHint() {
    String listTaxRegistrations = operationName(OperationId.LIST_TAX_REGISTRATIONS);
    return "Rerun "
        + listTaxRegistrations
        + " without "
        + ProtocolOptions.CURSOR
        + ", or pass the opaque nextCursor value returned by a prior successful "
        + listTaxRegistrations
        + " response.";
  }

  static String initializeWithOpenBookLabel() {
    return "Can initialize with " + operationName(OperationId.OPEN_BOOK);
  }

  private static String operationName(OperationId operationId) {
    return ProtocolCatalog.operationName(operationId);
  }
}
