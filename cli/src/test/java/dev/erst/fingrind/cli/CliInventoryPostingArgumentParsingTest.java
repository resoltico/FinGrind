package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage for the generic parser path used by inventory record commands. */
class CliInventoryPostingArgumentParsingTest {
  @Test
  void inventoryRecordCommands_parseThroughTheGenericRequestBoundPath() {
    assertRecordEntry(OperationId.RECORD_INVENTORY_CAPITALIZATION_SETTLED);
    assertRecordEntry(OperationId.RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT);
    assertRecordEntry(OperationId.RECORD_INVENTORY_WRITE_DOWN);
    assertRecordEntry(OperationId.RECORD_INVENTORY_SHRINKAGE);
    assertRecordEntry(OperationId.RECORD_INVENTORY_COUNT_INCREASE);
  }

  private static void assertRecordEntry(OperationId operationId) {
    assertInstanceOf(
        RecordEntry.class,
        CliPostingMutationArguments.parseRecordEntryCommand(requestArguments(), operationId));
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
