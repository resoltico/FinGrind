package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.protocol.ExecutionMode;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CommandCatalogDescriptor}. */
class CommandCatalogDescriptorTest {
  @Test
  void allCommands_preservesStableGroupedCapabilitiesOrder() {
    CommandDescriptor help =
        new CommandDescriptor(
            OperationId.HELP,
            List.of(),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            List.of(),
            List.of(),
            "Help");
    CommandDescriptor openBook =
        new CommandDescriptor(
            OperationId.OPEN_BOOK,
            List.of(),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            List.of(),
            List.of(),
            "Open");
    CommandDescriptor listAccounts =
        new CommandDescriptor(
            OperationId.LIST_ACCOUNTS,
            List.of(),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            List.of(),
            List.of(),
            "List");
    CommandDescriptor postEntry =
        new CommandDescriptor(
            OperationId.POST_ENTRY,
            List.of(),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            List.of(),
            List.of(),
            "Post");

    CommandCatalogDescriptor catalog =
        new CommandCatalogDescriptor(
            List.of(help), List.of(openBook), List.of(listAccounts), List.of(postEntry));

    assertEquals(
        List.of(
            OperationId.HELP,
            OperationId.OPEN_BOOK,
            OperationId.LIST_ACCOUNTS,
            OperationId.POST_ENTRY),
        catalog.allCommands().stream().map(CommandDescriptor::name).toList());
  }

  @Test
  void constructor_rejectsDuplicateCommandIdsAcrossGroups() {
    CommandDescriptor help =
        new CommandDescriptor(
            OperationId.HELP,
            List.of(),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            List.of(),
            List.of(),
            "Help");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new CommandCatalogDescriptor(List.of(help), List.of(help), List.of(), List.of()));

    assertEquals(
        "Duplicate command descriptor in capabilities catalog: help", exception.getMessage());
  }
}
