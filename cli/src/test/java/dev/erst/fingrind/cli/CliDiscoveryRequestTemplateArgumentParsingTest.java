package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.BookTemplateId;
import org.junit.jupiter.api.Test;

/** Focused request-template parsing coverage kept separate for source-shape limits. */
class CliDiscoveryRequestTemplateArgumentParsingTest extends CliArgumentParsingTestSupport {
  @Test
  void parse_supportsBookTemplateSelectionForPrintRequestTemplate() {
    PrintRequestTemplate command =
        assertInstanceOf(
            PrintRequestTemplate.class,
            CliArguments.parse(
                new String[] {
                  "print-request-template",
                  "record-sale-settled",
                  "--book-template-id",
                  "OWNER_MANAGED_TRADING"
                }));

    assertEquals(OperationId.RECORD_SALE_SETTLED, command.commandTopic());
    assertEquals(BookTemplateId.OWNER_MANAGED_TRADING, command.bookTemplateId());
  }

  @Test
  void parse_rejectsUnsupportedFlagBeforeRequestTemplateTopic() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"print-request-template", "--output"}));

    assertEquals("invalid-request", exception.failure().code());
    assertEquals("--output", exception.failure().argument());
    assertEquals("Unsupported argument: --output", exception.failure().message());
  }
}
