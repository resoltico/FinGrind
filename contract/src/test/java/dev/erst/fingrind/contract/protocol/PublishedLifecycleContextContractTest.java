package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Locks each published lifecycle context to its commands, scaffold, register, and ADR boundary. */
class PublishedLifecycleContextContractTest extends ProtocolContractRepositorySupport {
  @Test
  void lifecycleContexts_publishTheirOwnedCommandsStateProjectionsAndBoundaries()
      throws IOException {
    assertPublishedContext(
        "fixed-assets-and-depreciation",
        "docs/ADR_FIXED_ASSETS.md",
        List.of(
            new Command(
                OperationId.RECORD_FIXED_ASSET_CAPITALIZATION,
                BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION),
            new Command(
                OperationId.RECORD_FIXED_ASSET_DEPRECIATION,
                BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION),
            new Command(
                OperationId.RECORD_FIXED_ASSET_DISPOSAL,
                BookkeepingEntryKind.FIXED_ASSET_DISPOSAL)),
        OperationId.FIXED_ASSET_REGISTER,
        "https://www.ifrs.org/");
    assertPublishedContext(
        "financing",
        "docs/ADR_FINANCING.md",
        List.of(
            new Command(
                OperationId.RECORD_FINANCING_BORROWING, BookkeepingEntryKind.FINANCING_BORROWING),
            new Command(
                OperationId.RECORD_FINANCING_PRINCIPAL_REPAYMENT,
                BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT),
            new Command(
                OperationId.RECORD_FINANCING_INTEREST_ACCRUAL,
                BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL),
            new Command(
                OperationId.RECORD_FINANCING_INTEREST_PAYMENT,
                BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT)),
        OperationId.FINANCING_REGISTER,
        "https://www.ifrs.org/");
    assertPublishedContext(
        "foreign-exchange",
        "docs/ADR_REALIZED_FOREIGN_EXCHANGE.md",
        List.of(
            new Command(
                OperationId.RECORD_FOREIGN_CURRENCY_OBLIGATION,
                BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION),
            new Command(
                OperationId.RECORD_REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
                BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT)),
        OperationId.REALIZED_FOREIGN_EXCHANGE_REGISTER,
        "https://www.ifrs.org/");
  }

  private void assertPublishedContext(
      String capabilityId,
      String adrPath,
      List<Command> commands,
      OperationId register,
      String primaryAuthorityPrefix)
      throws IOException {
    CapabilityCatalogEntry capability =
        CapabilityCatalog.entries().stream()
            .filter(entry -> entry.id().equals(capabilityId))
            .findFirst()
            .orElseThrow();
    assertEquals(CapabilityStatus.PARTIAL, capability.status());
    assertTrue(capability.operativeBoundary() != null && !capability.operativeBoundary().isBlank());

    for (Command command : commands) {
      assertEquals(
          command.entryKind(),
          ProtocolPostingRequestTopics.requiredEntryKind(command.operationId()).orElseThrow());
      assertTrue(ProtocolRequestTemplateTopics.supports(command.operationId()));
      assertEquals(
          command.operationId().wireName(), ProtocolCatalog.operationName(command.operationId()));
    }
    assertEquals(register.wireName(), ProtocolCatalog.operationName(register));

    String adr = Files.readString(repositoryRoot().resolve(adrPath)).replace("\r\n", "\n");
    assertTrue(adr.contains("## Invariants"));
    assertTrue(adr.contains("## Publication Condition"));
    assertTrue(adr.contains(primaryAuthorityPrefix));
    assertTrue(
        adr.contains(
            "protected-book format `"
                + ProtocolCatalog.runtime().protectedBookFormat().formatVersion()
                + "`"));
    for (Command command : commands) {
      assertTrue(adr.contains("`" + command.operationId().wireName() + "`"));
    }
    assertTrue(adr.contains("`" + register.wireName() + "`"));
  }

  private record Command(OperationId operationId, BookkeepingEntryKind entryKind) {}
}
