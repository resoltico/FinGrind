package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;

/** Parses request-bound inventory posting commands. */
final class CliInventoryPostingMutationArguments {
  private CliInventoryPostingMutationArguments() {}

  static CliCommand parseRecordInventoryCapitalizationSettledCommand(List<String> arguments) {
    return CliPostingMutationArguments.parseRecordEntryCommand(
        arguments, OperationId.RECORD_INVENTORY_CAPITALIZATION_SETTLED);
  }

  static CliCommand parseRecordInventoryCapitalizationOnCreditCommand(List<String> arguments) {
    return CliPostingMutationArguments.parseRecordEntryCommand(
        arguments, OperationId.RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT);
  }

  static CliCommand parseRecordInventoryWriteDownCommand(List<String> arguments) {
    return CliPostingMutationArguments.parseRecordEntryCommand(
        arguments, OperationId.RECORD_INVENTORY_WRITE_DOWN);
  }

  static CliCommand parseRecordInventoryShrinkageCommand(List<String> arguments) {
    return CliPostingMutationArguments.parseRecordEntryCommand(
        arguments, OperationId.RECORD_INVENTORY_SHRINKAGE);
  }

  static CliCommand parseRecordInventoryCountIncreaseCommand(List<String> arguments) {
    return CliPostingMutationArguments.parseRecordEntryCommand(
        arguments, OperationId.RECORD_INVENTORY_COUNT_INCREASE);
  }
}
