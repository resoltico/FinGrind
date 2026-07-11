package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage for inventory mutation-command argument delegates. */
class CliInventoryPostingMutationArgumentsTest {
  @Test
  void inventoryMutationDelegates_parseEveryRecordCommand() {
    assertInstanceOf(
        RecordEntry.class,
        CliInventoryPostingMutationArguments.parseRecordInventoryCapitalizationSettledCommand(
            requestArguments()));
    assertInstanceOf(
        RecordEntry.class,
        CliInventoryPostingMutationArguments.parseRecordInventoryCapitalizationOnCreditCommand(
            requestArguments()));
    assertInstanceOf(
        RecordEntry.class,
        CliInventoryPostingMutationArguments.parseRecordInventoryWriteDownCommand(
            requestArguments()));
    assertInstanceOf(
        RecordEntry.class,
        CliInventoryPostingMutationArguments.parseRecordInventoryShrinkageCommand(
            requestArguments()));
    assertInstanceOf(
        RecordEntry.class,
        CliInventoryPostingMutationArguments.parseRecordInventoryCountIncreaseCommand(
            requestArguments()));
  }

  private static List<String> requestArguments() {
    return List.of(
        "record-inventory-capitalization-settled",
        "--book-file",
        "book.sqlite",
        "--book-key-file",
        "book.key",
        "--request-file",
        "request.json");
  }
}
