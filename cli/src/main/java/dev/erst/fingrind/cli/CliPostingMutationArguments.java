package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses request-bound posting mutation commands. */
final class CliPostingMutationArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec OUTPUT_ONLY_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(List.of(ProtocolOptions.OUTPUT), List.of());

  private CliPostingMutationArguments() {}

  static CliCommand parsePreflightEntryCommand(List<String> arguments) {
    return parseRequestBoundOutputCommand(arguments, PreflightEntry::new);
  }

  static CliCommand parsePostEntryCommand(List<String> arguments) {
    return parseRequestBoundOutputCommand(arguments, PostEntry::new);
  }

  static CliCommand parseRecordSaleSettledCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_SALE_SETTLED);
  }

  static CliCommand parseRecordSaleOnCreditCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_SALE_ON_CREDIT);
  }

  static CliCommand parseRecordPurchaseSettledCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_PURCHASE_SETTLED);
  }

  static CliCommand parseRecordPurchaseOnCreditCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_PURCHASE_ON_CREDIT);
  }

  static CliCommand parseRecordExpenseSettledCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_EXPENSE_SETTLED);
  }

  static CliCommand parseRecordExpenseOnCreditCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_EXPENSE_ON_CREDIT);
  }

  static CliCommand parseRecordReceiptCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_RECEIPT);
  }

  static CliCommand parseRecordPaymentCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_PAYMENT);
  }

  static CliCommand parseRecordOwnerContributionCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_OWNER_CONTRIBUTION);
  }

  static CliCommand parseRecordOwnerWithdrawalCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_OWNER_WITHDRAWAL);
  }

  static CliCommand parseRecordOpeningPositionCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_OPENING_POSITION);
  }

  static CliCommand parseRecordReversalCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_REVERSAL);
  }

  static CliCommand parseRecordEntryCommand(List<String> arguments, OperationId operationId) {
    return parseRequestBoundOutputCommand(
        arguments,
        (bookAccess, requestFile, outputMode) ->
            new RecordEntry(bookAccess, requestFile, outputMode, operationId));
  }

  private static CliCommand parseRequestBoundOutputCommand(
      List<String> arguments, RequestBoundOutputCommandFactory commandFactory) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseRequestBoundCommandArguments(arguments, OUTPUT_ONLY_ARGUMENTS);
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      argumentIterator.next();
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
    }
    return commandFactory.create(
        parsedArguments.bookAccess(),
        parsedArguments.optionalRequestFile().orElseThrow(),
        CliOptionModes.resolvedOutputMode(outputMode));
  }

  /** Builds one posting command from the resolved book, request file, and output mode. */
  @FunctionalInterface
  private interface RequestBoundOutputCommandFactory {
    /** Returns one parsed posting command. */
    CliCommand create(BookAccess bookAccess, Path requestFile, OutputMode outputMode);
  }
}
